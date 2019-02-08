package com.tterrag.k9.commands;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongFunction;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.tterrag.k9.K9;
import com.tterrag.k9.commands.CommandTrick.TrickData;
import com.tterrag.k9.commands.api.Argument;
import com.tterrag.k9.commands.api.Command;
import com.tterrag.k9.commands.api.CommandContext;
import com.tterrag.k9.commands.api.CommandException;
import com.tterrag.k9.commands.api.CommandPersisted;
import com.tterrag.k9.commands.api.CommandRegistrar;
import com.tterrag.k9.commands.api.Flag;
import com.tterrag.k9.listeners.CommandListener;
import com.tterrag.k9.trick.Trick;
import com.tterrag.k9.trick.TrickClojure;
import com.tterrag.k9.trick.TrickFactories;
import com.tterrag.k9.trick.TrickSimple;
import com.tterrag.k9.trick.TrickType;
import com.tterrag.k9.util.BakedMessage;
import com.tterrag.k9.util.EmbedCreator;
import com.tterrag.k9.util.ListMessageBuilder;
import com.tterrag.k9.util.annotation.NonNull;
import com.tterrag.k9.util.annotation.Nullable;
import com.tterrag.k9.util.Patterns;
import com.tterrag.k9.util.Requirements;
import com.tterrag.k9.util.Requirements.RequiredType;
import com.tterrag.k9.util.SaveHelper;

import discord4j.core.DiscordClient;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.GuildChannel;
import discord4j.core.object.util.Permission;
import discord4j.core.object.util.Snowflake;
import lombok.Value;
import reactor.core.publisher.Mono;

@Command
public class CommandTrick extends CommandPersisted<Map<String, TrickData>> {
    
    @Value
    public static class TrickData {
        TrickType type;
        String input;
        long owner;
    }
    
    public static final TrickType DEFAULT_TYPE = TrickType.STRING;
    
    private static final Requirements REMOVE_PERMS = Requirements.builder().with(Permission.MANAGE_MESSAGES, RequiredType.ALL_OF).build();
    
    private static final Flag FLAG_ADD = new SimpleFlag('a', "add", "Add a new trick.", false);
    private static final Flag FLAG_REMOVE = new SimpleFlag('r', "remove", "Remove a trick. Can only be done by the owner, or a moderator with MANAGE_MESSAGES permission.", false);
    private static final Flag FLAG_LIST = new SimpleFlag('l', "list", "List all tricks.", false);
    private static final Flag FLAG_TYPE = new SimpleFlag('t', "type", "The type of trick, aka the language.", true) {
        
        @Override
        public String description() {
            return super.description() + " Possible values: `"
                    + Arrays.stream(TrickFactories.INSTANCE.getTypes())
                          .map(TrickType::getId)
                          .collect(Collectors.joining(", "))
                    + "`. Default: `" + DEFAULT_TYPE + "`";
        }
    };
    private static final Flag FLAG_GLOBAL = new SimpleFlag('g', "global", "Forces any trick lookup to be global, bypassing the guild's local tricks. For adding, usable only by admins.", false);
    private static final Flag FLAG_INFO = new SimpleFlag('i', "info", "Show info about the trick, instead of executing it, such as the owner and source code.", false);
    private static final Flag FLAG_SRC = new SimpleFlag('s', "source", "Show the source code of the trick. Can be used together with -i.", false);
    private static final Flag FLAG_UPDATE = new SimpleFlag('u', "update", "Overwrite an existing trick, if applicable. Can only be done by the trick owner.", false);

    private static final Argument<String> ARG_TRICK = new WordArgument("trick", "The trick to invoke", true) {
        @Override
        public boolean required(Collection<Flag> flags) {
            return !flags.contains(FLAG_LIST);
        };
    };
    private static final Argument<String> ARG_PARAMS = new SentenceArgument("params", "The parameters to pass to the trick, or when adding a trick, the content of the trick, script or otherwise.", false) {
        @Override
        public boolean required(Collection<Flag> flags) {
            return flags.contains(FLAG_ADD);
        }
    };
    
    static final String DEFAULT_PREFIX = "?";
    static LongFunction<String> prefixes = id -> DEFAULT_PREFIX;

    private SaveHelper<Map<String, TrickData>> globalHelper;
    private Map<String, TrickData> globalTricks;
    
    private final Map<Long, Map<String, Trick>> trickCache = new HashMap<>();

    public CommandTrick() {
        super("trick", false, HashMap::new);
    }
    
    @Override
    public void init(DiscordClient client, File dataFolder, Gson gson) {
        super.init(client, dataFolder, gson);

        globalHelper = new SaveHelper<>(dataFolder, gson, new HashMap<>());
        globalTricks = globalHelper.fromJson("global_tricks.json", getDataType());
        
        TrickFactories.INSTANCE.addFactory(DEFAULT_TYPE, TrickSimple::new);
        
        final CommandClojure clj = (CommandClojure) K9.commands.findCommand(null, "clj").block();
        TrickFactories.INSTANCE.addFactory(TrickType.CLOJURE, code -> new TrickClojure(clj, code));
    }
    
    @Override
    public void gatherParsers(GsonBuilder builder) {
        builder.registerTypeHierarchyAdapter(TrickType.class, new TypeAdapter<TrickType>() {
            @Override
            public TrickType read(JsonReader in) throws IOException {
                TrickType type =  TrickType.byId.get(in.nextString());
                if (type == null) {
                    return TrickType.STRING;
                }
                return type;
            }
            
            @Override
            public void write(JsonWriter out, TrickType value) throws IOException {
                out.value(value.getId());
            }
        });
    }
    
    @Override
    protected TypeToken<Map<String, TrickData>> getDataType() {
        return new TypeToken<Map<String, TrickData>>(){};
    }

    @Override
    public Mono<?> process(CommandContext ctx) {
        if (ctx.hasFlag(FLAG_LIST)) {
            Collection<String> tricks = ctx.hasFlag(FLAG_GLOBAL) ? globalTricks.keySet() : storage.get(ctx).block().keySet();
            if (tricks.isEmpty()) {
                return ctx.error("No tricks to list!");
            } else {
                return new ListMessageBuilder<String>("tricks").addObjects(tricks).objectsPerPage(10).build(ctx).send();
            }
        }
        
        if (ctx.hasFlag(FLAG_ADD)) {
            String typeId = ctx.getFlag(FLAG_TYPE);
            TrickType type = typeId == null ? DEFAULT_TYPE : TrickType.byId.get(typeId);
            if (type == null) {
                return ctx.error("No such type \"" + typeId + "\"");
            }
            String args = ctx.getArg(ARG_PARAMS);
            Matcher codematcher = Patterns.CODEBLOCK.matcher(args);
            if (codematcher.matches()) {
                args = codematcher.group(2).trim();
            }
            TrickData data = new TrickData(type, args, ctx.getAuthor().get().getId().asLong());
            final String trick = ctx.getArg(ARG_TRICK);
            if (ctx.hasFlag(FLAG_GLOBAL)) {
                if (!CommandRegistrar.isAdmin(ctx.getAuthor().get())) {
                    return ctx.error("You do not have permission to add global tricks.");
                }
                globalTricks.put(trick, data);
                globalHelper.writeJson("global_tricks.json", globalTricks);
                trickCache.getOrDefault(null, new HashMap<>()).remove(trick);
            } else {
                Guild guild = ctx.getGuild().block();
                if (guild == null) {
                    return ctx.error("Cannot add local tricks in private message.");
                }
                TrickData existing = storage.get(ctx).block().get(trick);
                if (existing != null) {
                    if (existing.getOwner() != ctx.getAuthor().get().getId().asLong() && !REMOVE_PERMS.matches(ctx).block()) {
                        return ctx.error("A trick with this name already exists in this guild.");
                    }
                    if (!ctx.hasFlag(FLAG_UPDATE)) {
                        return ctx.error("A trick with this name already exists! Use -u to overwrite.");
                    }
                }
                storage.get(ctx).block().put(trick, data);
                trickCache.getOrDefault(guild.getId().asLong(), new HashMap<>()).remove(trick);
            }
            return ctx.reply("Added new trick!");
        } else if (ctx.hasFlag(FLAG_REMOVE)) {
            if (ctx.hasFlag(FLAG_GLOBAL) && !CommandRegistrar.isAdmin(ctx.getAuthor().get())) {
                return ctx.error("You do not have permission to remove global tricks!");
            }
            String id = ctx.getArg(ARG_TRICK);
            Map<String, TrickData> tricks = ctx.hasFlag(FLAG_GLOBAL) ? globalTricks : storage.get(ctx).block();
            TrickData trick = tricks.get(id);
            if (trick == null) {
                return ctx.error("No trick with that name!");
            }
            if (trick.getOwner() != ctx.getAuthor().get().getId().asLong() && !REMOVE_PERMS.matches(ctx).block()) {
                return ctx.error("You do not have permission to remove this trick!");
            }
            tricks.remove(id);
            trickCache.computeIfPresent(ctx.hasFlag(FLAG_GLOBAL) ? null : ctx.getGuild().block().getId().asLong(), (i, m) -> {
               m.remove(id);
               return m.isEmpty() ? null : m;
            });
            return ctx.reply("Removed trick!");
        } else {
            TrickData data = ctx.getGuild() == null || ctx.hasFlag(FLAG_GLOBAL) ? null : storage.get(ctx).block().get(ctx.getArg(ARG_TRICK));
            boolean global = false;
            if (data == null) {
                data = globalTricks.get(ctx.getArg(ARG_TRICK));
                if (data == null) {
                    if (!ctx.getMessage().getContent().get().startsWith(CommandListener.getPrefix(ctx.getGuildId()) + getTrickPrefix(ctx.getGuildId()))) {
                        return ctx.error("No such trick!");
                    }
                    return Mono.empty();
                }
                global = true;
            }
            
            final TrickData td = data;

            if (ctx.hasFlag(FLAG_INFO)) {
                EmbedCreator.Builder builder = EmbedCreator.builder()
                        .title(ctx.getArg(ARG_TRICK))
                        .description("Owner: " + ctx.getClient().getUserById(Snowflake.of(data.getOwner())).block().getMention())
                        .field("Type", data.getType().toString(), false)
                        .field("Global", Boolean.toString(global), false);
                if (ctx.hasFlag(FLAG_SRC)) {
                    builder.field("Source", "```" + data.getType().getHighlighter() + "\n" + data.getInput() + "\n```", false);
                }
                return ctx.reply(builder.build());
            } else if (ctx.hasFlag(FLAG_SRC)) {
                return ctx.reply("```" + data.getType().getHighlighter() + "\n" + data.getInput() + "\n```");
            } else {
                Trick trick = getTrick(ctx, td, global);

                String args = ctx.getArgOrElse(ARG_PARAMS, "");
                Matcher matcher = Patterns.ARG_SPLITTER.matcher(args);
                List<String> splitArgs = new ArrayList<>();
                while (matcher.find()) {
                    String arg = matcher.group("quoted");
                    if (arg == null) {
                        arg = matcher.group("unquoted");
                    }
                    splitArgs.add(arg);
                }

                
                return trick.process(ctx, splitArgs.toArray())
                        .filter(m -> m.getEmbed() != null && !StringUtils.isEmpty(m.getContent()))
                        .switchIfEmpty(ctx.error("Empty result"))
                        .flatMap(m -> ctx.getChannel().flatMap(m::send));
            }
        }
    }
    
    Trick getTrick(@NonNull CommandContext ctx, @Nullable String trick, boolean global) {
        TrickData td = storage.get(ctx).block().get(trick);
        if (td == null) {
            throw new IllegalArgumentException("No such trick!");
        }
        return getTrick(ctx, td, global);
    }

    private Trick getTrick(CommandContext ctx, TrickData td, boolean global) {
        Guild guild = ctx.getGuild().block();
        Map<String, Trick> tricks = trickCache.computeIfAbsent(global || guild == null ? null : guild.getId().asLong(), id -> new HashMap<>());
        return tricks.computeIfAbsent(ctx.getArg(ARG_TRICK), input -> TrickFactories.INSTANCE.create(td.getType(), td.getInput()));
    }

    @Override
    public String getDescription() {
        return "Teach K9 a new trick! Tricks can be invoked by calling `!trick [name]` or adding a `?` to the prefix.";
    }
    
    public static String getTrickPrefix(Optional<Snowflake> guild) {
        return getTrickPrefix(guild.orElse(null));
    }
    
    public static String getTrickPrefix(Snowflake guild) {
        return guild == null ? DEFAULT_PREFIX : prefixes.apply(guild.asLong());
    }
}
