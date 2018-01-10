package com.blamejared.mcbot.commands;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.blamejared.mcbot.MCBot;
import com.blamejared.mcbot.commands.CommandTrick.TrickData;
import com.blamejared.mcbot.commands.api.Argument;
import com.blamejared.mcbot.commands.api.Command;
import com.blamejared.mcbot.commands.api.CommandContext;
import com.blamejared.mcbot.commands.api.CommandException;
import com.blamejared.mcbot.commands.api.CommandPersisted;
import com.blamejared.mcbot.commands.api.CommandRegistrar;
import com.blamejared.mcbot.commands.api.Flag;
import com.blamejared.mcbot.trick.Trick;
import com.blamejared.mcbot.trick.TrickClojure;
import com.blamejared.mcbot.trick.TrickFactories;
import com.blamejared.mcbot.trick.TrickSimple;
import com.blamejared.mcbot.trick.TrickType;
import com.blamejared.mcbot.util.NonNull;
import com.blamejared.mcbot.util.Nullable;
import com.blamejared.mcbot.util.SaveHelper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import lombok.Value;
import sx.blah.discord.util.EmbedBuilder;

@Command
public class CommandTrick extends CommandPersisted<Map<String, TrickData>> {
    
    @Value
    public static class TrickData {
        TrickType type;
        String input;
        long owner;
    }
    
    public static final TrickType DEFAULT_TYPE = TrickType.STRING;
    
    private static final Pattern ARG_SPLITTER = Pattern.compile("(\"(?<quoted>.+?)(?<![^\\\\]\\\\)\")|(?<unquoted>\\S+)", Pattern.DOTALL);
    private static final Pattern CODEBLOCK_PARSER = Pattern.compile("```(\\w*)(.*)```", Pattern.DOTALL);
    
    private static final Flag FLAG_ADD = new SimpleFlag('a', "add", "Add a new trick.", false);
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
    private static final Flag FLAG_GLOBAL = new SimpleFlag('g', "global", "If true, the trick will be globally available. Only usable by admins.", false);
    private static final Flag FLAG_INFO = new SimpleFlag('i', "info", "Show info about the trick, instead of executing it, such as the owner and source code.", false);
    private static final Flag FLAG_SRC = new SimpleFlag('s', "source", "Show the source code of the trick. Can be used together with -i.", false);
    
    private static final Argument<String> ARG_TRICK = new WordArgument("trick", "The trick to invoke", true);
    private static final Argument<String> ARG_PARAMS = new SentenceArgument("params", "The parameters to pass to the trick, or when adding a trick, the content of the trick, script or otherwise.", false) {
        @Override
        public boolean required(Collection<Flag> flags) {
            return flags.contains(FLAG_ADD);
        }
    };
    
    private SaveHelper<Map<String, TrickData>> globalHelper;
    private Map<String, TrickData> globalTricks;
    
    private final Map<Long, Map<String, Trick>> trickCache = new HashMap<>();

    public CommandTrick() {
        super("trick", false, HashMap::new);
    }
    
    @Override
    public void init(File dataFolder, Gson gson) {
        super.init(dataFolder, gson);

        globalHelper = new SaveHelper<Map<String,TrickData>>(dataFolder, gson, new HashMap<>());
        globalTricks = globalHelper.fromJson("global_tricks.json", getDataType());
        
        TrickFactories.INSTANCE.addFactory(DEFAULT_TYPE, TrickSimple::new);
        
        final CommandClojure clj = (CommandClojure) CommandRegistrar.INSTANCE.findCommand("clj");
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
    public void process(CommandContext ctx) throws CommandException {
        if (ctx.hasFlag(FLAG_ADD)) {
            String typeId = ctx.getFlag(FLAG_TYPE);
            TrickType type = typeId == null ? DEFAULT_TYPE : TrickType.byId.get(typeId);
            if (type == null) {
                throw new CommandException("No such type \"" + typeId + "\"");
            }
            String args = ctx.getArg(ARG_PARAMS);
            Matcher codematcher = CODEBLOCK_PARSER.matcher(args);
            if (codematcher.matches()) {
                args = codematcher.group(2).trim();
            }
            TrickData data = new TrickData(type, args, ctx.getAuthor().getLongID());
            final String trick = ctx.getArg(ARG_TRICK);
            if (ctx.hasFlag(FLAG_GLOBAL)) {
                if (!CommandRegistrar.isAdmin(ctx.getAuthor())) {
                    throw new CommandException("You do not have permission to add global tricks.");
                }
                globalTricks.put(trick, data);
                globalHelper.writeJson("global_tricks.json", globalTricks);
                trickCache.getOrDefault(null, new HashMap<>()).remove(trick);
            } else {
                if (ctx.getGuild() == null) {
                    throw new CommandException("Cannot add local tricks in private message.");
                }
                storage.get(ctx).put(trick, data);
                trickCache.getOrDefault(ctx.getGuild().getLongID(), new HashMap<>()).remove(trick);
            }
            ctx.reply("Added new trick!");
        } else {
            TrickData data = ctx.getGuild() == null ? null : storage.get(ctx).get(ctx.getArg(ARG_TRICK));
            boolean global = false;
            if (data == null) {
                data = globalTricks.get(ctx.getArg(ARG_TRICK));
                if (data == null) {
                    throw new CommandException("No such trick!");
                }
                global = true;
            }
            
            
            final TrickData td = data;

            if (ctx.hasFlag(FLAG_INFO)) {
                EmbedBuilder builder = new EmbedBuilder()
                        .withTitle(ctx.getArg(ARG_TRICK))
                        .withDesc("Owner: " + MCBot.instance.fetchUser(data.getOwner()).mention())
                        .appendField("Type", data.getType().toString(), false);
                if (ctx.hasFlag(FLAG_SRC)) {
                    builder.appendField("Source", "```" + data.getType().getHighlighter() + "\n" + data.getInput() + "\n```", false);
                }
                ctx.replyBuffered(builder.build());
            } else if (ctx.hasFlag(FLAG_SRC)) {
                ctx.replyBuffered("```" + data.getType().getHighlighter() + "\n" + data.getInput() + "\n```");
            } else {
                Trick trick = getTrick(ctx, td, global);

                String args = ctx.getArgOrElse(ARG_PARAMS, "");
                Matcher matcher = ARG_SPLITTER.matcher(args);
                List<String> splitArgs = new ArrayList<>();
                while (matcher.find()) {
                    String arg = matcher.group("quoted");
                    if (arg == null) {
                        arg = matcher.group("unquoted");
                    }
                    splitArgs.add(arg);
                }

                String res = trick.process(ctx, splitArgs.toArray());
                if (StringUtils.isEmpty(res)) {
                    throw new CommandException("Empty result");
                }
                ctx.replyBuffered(res);
            }
        }
    }
    
    Trick getTrick(@NonNull CommandContext ctx, @Nullable String trick, boolean global) {
        TrickData td = storage.get(ctx).get(trick);
        if (td == null) {
            throw new IllegalArgumentException("No such trick!");
        }
        return getTrick(ctx, td, global);
    }

    private Trick getTrick(CommandContext ctx, TrickData td, boolean global) {
        Map<String, Trick> tricks = trickCache.computeIfAbsent(global ? null : ctx.getGuild().getLongID(), id -> new HashMap<>());
        return tricks.computeIfAbsent(ctx.getArg(ARG_TRICK), input -> TrickFactories.INSTANCE.create(td.getType(), td.getInput()));
    }

    @Override
    public String getDescription() {
        return "Teach K9 a new trick! Tricks can be invoked by calling `!trick [name]` or adding a `?` to the prefix.";
    }
}
