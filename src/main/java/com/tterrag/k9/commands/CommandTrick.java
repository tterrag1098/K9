package com.tterrag.k9.commands;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
import com.tterrag.k9.commands.api.CommandPersisted;
import com.tterrag.k9.commands.api.Flag;
import com.tterrag.k9.listeners.CommandListener;
import com.tterrag.k9.trick.Trick;
import com.tterrag.k9.trick.TrickClojure;
import com.tterrag.k9.trick.TrickFactories;
import com.tterrag.k9.trick.TrickSimple;
import com.tterrag.k9.trick.TrickType;
import com.tterrag.k9.util.DelegatingTypeReader;
import com.tterrag.k9.util.EmbedCreator;
import com.tterrag.k9.util.ListMessageBuilder;
import com.tterrag.k9.util.Patterns;
import com.tterrag.k9.util.Requirements;
import com.tterrag.k9.util.Requirements.RequiredType;
import com.tterrag.k9.util.SaveHelper;
import com.tterrag.k9.util.annotation.NonNull;
import com.tterrag.k9.util.annotation.Nullable;

import discord4j.core.object.entity.Guild;
import discord4j.core.object.util.Permission;
import discord4j.core.object.util.Snowflake;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

@Command
@Slf4j
public class CommandTrick extends CommandPersisted<Map<String, TrickData>> {
    
    @Value
    @RequiredArgsConstructor
    public static class TrickData {
        TrickType type;
        String input;
        long owner;
        int version;
        
        TrickData(TrickType type, String input, long owner) {
            this(type, input, owner, 1);
        }
    }

    public static final TrickType DEFAULT_TYPE = TrickType.STRING;
    
    private static final Requirements REMOVE_PERMS = Requirements.builder().with(Permission.MANAGE_MESSAGES, RequiredType.ALL_OF).build();
    
    private static final Flag FLAG_ADD = new SimpleFlag('a', "add", "Add a new trick.", false);
    private static final Flag FLAG_REMOVE = new SimpleFlag('r', "remove", "Remove a trick. Can only be done by the owner, or a moderator with MANAGE_MESSAGES permission.", false);
    private static final Flag FLAG_FETCH = new SimpleFlag('f', "fetch", "Fetch the trick source from a URL.", false);
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
    public void init(K9 k9, File dataFolder, Gson gson) {
        super.init(k9, dataFolder, gson);

        globalHelper = new SaveHelper<>(dataFolder, gson, new HashMap<>());
        globalTricks = globalHelper.fromJson("global_tricks.json", getDataType());
        
        TrickFactories.INSTANCE.addFactory(DEFAULT_TYPE, TrickSimple::new);
        
        final CommandClojure clj = (CommandClojure) k9.getCommands().findCommand((Snowflake) null, "clj").get();
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
        builder.registerTypeAdapterFactory(new DelegatingTypeReader<TrickData>(TrickData.class) {

            @Override
            protected TrickData handleDelegate(TrickData delegate) {
                if (delegate.getVersion() == 0) {
                    String input = delegate.getInput();
                    if (delegate.getType() == TrickType.CLOJURE) {
                        input = updateInput(delegate.getInput());
                    }
                    delegate = new TrickData(delegate.getType(), input, delegate.getOwner());
                }
                return super.handleDelegate(delegate);
            }
        });
    }
    
    @Override
    protected void onLoad(long guild, Map<String, TrickData> data) {
        for (String key : new HashSet<>(data.keySet())) {
            if (!Patterns.VALID_TRICK_NAME.matcher(key).matches()) {
                TrickData removed = data.remove(key);
                log.error("Trick with invalid name removed: " + key + " -> " + removed.getInput());
            }
        }
    }
    
    // Copied from Formatter
    // %[argument_index$][flags][width][.precision][t]conversion
    private static final Pattern fsPattern = Pattern.compile(
        "\"?%(\\d+\\$)?([-#+ 0,(\\<]*)?(\\d+)?(\\.\\d+)?([tT])?([a-zA-Z%])\"?");

    private String updateInput(String input) {
        int argCount = 0;
        Matcher m = fsPattern.matcher(input);
        StringBuffer replaced = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(replaced, Character.toString((char) ('a' + argCount)));
            argCount++;
        }
        m.appendTail(replaced);
        StringBuilder res = new StringBuilder("(fn [");
        for (int i = 0; i < argCount; i++) {
            res.append((char) ('a' + i)).append(' ');
        }
        if (argCount > 0) {
            res.deleteCharAt(res.length() - 1);
        }
        res.append("] ").append(replaced).append(')');
        return res.toString();
    }

    @Override
    protected TypeToken<Map<String, TrickData>> getDataType() {
        return new TypeToken<Map<String, TrickData>>(){};
    }

    @Override
    public Mono<?> process(CommandContext ctx) {
        if (ctx.hasFlag(FLAG_LIST)) {
            Collection<String> tricks = ctx.hasFlag(FLAG_GLOBAL) ? globalTricks.keySet() : storage.get(ctx).orElse(Collections.emptyMap()).keySet();
            if (tricks.isEmpty()) {
                return ctx.error("No tricks to list!");
            } else {
                return ctx.getChannel()
                        .flatMap(channel -> new ListMessageBuilder<String>("tricks")
                                .addObjects(tricks)
                                .objectsPerPage(10)
                                .build(channel, ctx.getMessage())
                                .send());
            }
        }
        
        if (ctx.hasFlag(FLAG_ADD) || ctx.hasFlag(FLAG_UPDATE)) {
            String typeId = ctx.getFlag(FLAG_TYPE);
            TrickType type = typeId == null ? DEFAULT_TYPE : TrickType.byId.get(typeId);
            if (type == null) {
                return ctx.error("No such type \"" + typeId + "\"");
            }
            final String trick = ctx.getArg(ARG_TRICK);
            if (!Patterns.VALID_TRICK_NAME.matcher(trick).matches() || Patterns.DISCORD_MENTION.matcher(trick).find()) {
                return ctx.error("Invalid trick name \"" + trick + "\"");
            }
            String args = ctx.getArg(ARG_PARAMS);
            if (ctx.hasFlag(FLAG_FETCH)) {
                try {
                    args = HttpClient.create().get()
                      .uri(args)
                      .responseSingle(($, content) -> content.asString(StandardCharsets.UTF_8))
                      .block(); // TODO: refactor me! temporary until this class gets refactored
                } catch (final Throwable t) {
                    return ctx.error("Could not fetch trick data.");
                }
            } else {
                Matcher codematcher = Patterns.CODEBLOCK.matcher(args);
                if (codematcher.matches()) {
                    args = codematcher.group(2).trim();
                }
            }
            if (ctx.getK9().getCommands().findCommand((Snowflake) null, trick).isPresent() && !ctx.getAuthor().filter(ctx.getK9().getCommands()::isAdmin).isPresent()) {
                return ctx.error("Cannot add a trick with the same name as a command.");
            }
            TrickData existing;
            if (ctx.hasFlag(FLAG_GLOBAL)) {
                if (!ctx.getK9().getCommands().isAdmin(ctx.getAuthor().get())) {
                    return ctx.error("You do not have permission to add global tricks.");
                }
                existing = globalTricks.get(trick);
                globalTricks.put(trick, new TrickData(type, args, existing == null ? ctx.getAuthorId().get().asLong() : existing.getOwner()));
                globalHelper.writeJson("global_tricks.json", globalTricks);
                trickCache.getOrDefault(null, new HashMap<>()).remove(trick);
            } else {
                Guild guild = ctx.getGuild().block();
                if (guild == null) {
                    return ctx.error("Cannot add local tricks in private message.");
                }
                existing = storage.get(ctx).get().get(trick);
                TrickData data;
                if (existing != null) {
                    if (existing.getOwner() != ctx.getAuthor().get().getId().asLong() && !REMOVE_PERMS.matches(ctx).block()) {
                        return ctx.error("A trick with this name already exists in this guild.");
                    }
                    if (!ctx.hasFlag(FLAG_UPDATE)) {
                        return ctx.error("A trick with this name already exists! Use -u to overwrite.");
                    }
                    data = new TrickData(type, args, existing.getOwner());
                } else if (ctx.hasFlag(FLAG_UPDATE)) {
                    return ctx.error("No trick with that name exists to update.");
                } else {
                    data = new TrickData(type, args, ctx.getAuthor().get().getId().asLong());
                }
                storage.get(ctx).get().put(trick, data);
                trickCache.getOrDefault(guild.getId().asLong(), new HashMap<>()).remove(trick);
            }
            return ctx.reply(existing == null ? "Added new trick!" : "Updated trick!");
        } else if (ctx.hasFlag(FLAG_REMOVE)) {
            if (ctx.hasFlag(FLAG_GLOBAL) && !ctx.getK9().getCommands().isAdmin(ctx.getAuthor().get())) {
                return ctx.error("You do not have permission to remove global tricks!");
            }
            String id = ctx.getArg(ARG_TRICK);
            Map<String, TrickData> tricks = ctx.hasFlag(FLAG_GLOBAL) ? globalTricks : storage.get(ctx).orElse(null);
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
            Map<String, TrickData> dataMap = storage.get(ctx).orElse(null);
            TrickData data = dataMap == null ? null : dataMap.get(ctx.getArg(ARG_TRICK));
            boolean global = false;
            if (data == null || ctx.hasFlag(FLAG_GLOBAL)) {
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
                        .flatMap(ctx::reply);
            }
        }
    }
    
    public @Nullable TrickData getTrickData(@Nullable Snowflake guild, String trick) {
        Map<String, TrickData> data = guild == null ? globalTricks : this.storage.get(guild);
        TrickData ret = data.get(trick);
        if (ret == null && guild != null) {
            ret = globalTricks.get(trick);
        }
        return ret;
    }
    
    TrickData getTrickData(@NonNull Map<String, TrickData> data, @Nullable String trick, boolean global) {
        TrickData td = data.get(trick);
        if (td == null) {
            throw new IllegalArgumentException("No such trick!");
        }
        return td;
    }

    private Trick getTrick(CommandContext ctx, TrickData td, boolean global) {
        return getTrick(ctx.getArg(ARG_TRICK), ctx.getGuildId().orElse(null), td, global);
    }
    
    Trick getTrick(String name, @Nullable Snowflake guild, TrickData td, boolean global) {
        Map<String, Trick> tricks = trickCache.computeIfAbsent(global || guild == null ? null : guild.asLong(), id -> new HashMap<>());
        return tricks.computeIfAbsent(name, input -> TrickFactories.INSTANCE.create(td.getType(), td.getInput()));
    }

    @Override
    public String getDescription(CommandContext ctx) {
        return "Teach K9 a new trick! Tricks can be invoked by calling `" + CommandListener.getPrefix(ctx.getGuildId()) + "trick [name]` or adding a `" + getTrickPrefix(ctx.getGuildId()) + "` to the prefix.";
    }
    
    public static String getTrickPrefix(Optional<Snowflake> guild) {
        return getTrickPrefix(guild.orElse(null));
    }
    
    public static String getTrickPrefix(Snowflake guild) {
        return guild == null ? DEFAULT_PREFIX : prefixes.apply(guild.asLong());
    }
}
