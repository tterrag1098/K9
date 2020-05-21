package com.tterrag.k9.commands;

import java.io.File;
import java.io.IOException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.tterrag.k9.K9;
import com.tterrag.k9.commands.CommandPrefix.PrefixData;
import com.tterrag.k9.commands.api.Command;
import com.tterrag.k9.commands.api.CommandContext;
import com.tterrag.k9.commands.api.CommandPersisted;
import com.tterrag.k9.commands.api.Flag;
import com.tterrag.k9.listeners.CommandListener;
import com.tterrag.k9.util.DelegatingTypeReader;
import com.tterrag.k9.util.Requirements;
import com.tterrag.k9.util.Requirements.RequiredType;

import discord4j.rest.util.Permission;
import discord4j.rest.util.Snowflake;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import reactor.core.publisher.Mono;

@Command
public class CommandPrefix extends CommandPersisted<PrefixData> {
    
    @Setter
    @Getter
    @AllArgsConstructor
    @NoArgsConstructor(access = AccessLevel.PACKAGE)
    public static class PrefixData {
        private String command = CommandListener.DEFAULT_PREFIX;
        private String trick = CommandTrick.DEFAULT_PREFIX;
    }
    
    private static final WordArgument ARG_PREFIX = new WordArgument("prefix", "The prefix to set. Leave out to reset to default.", false);
    
    private static final Flag FLAG_TRICK = new SimpleFlag('t', "trick", "Modify the prefix for tricks. Use \"none\" to use no prefix, tricks will be run using the main prefix.", false);
    
    public CommandPrefix() {
        super("prefix", false, PrefixData::new);
    }

    @Override
    protected TypeToken<PrefixData> getDataType() {
        return TypeToken.get(PrefixData.class);
    }
    
    @Override
    public Mono<?> process(CommandContext ctx) {
        return this.storage.get(ctx)
                .map(data -> {
                    String newPrefix = ctx.getArgOrElse(ARG_PREFIX, CommandListener.DEFAULT_PREFIX);
                    if (ctx.hasFlag(FLAG_TRICK)) {
                        if (newPrefix.equalsIgnoreCase("none")) {
                            newPrefix = "";
                        }
                        data.setTrick(newPrefix);
                    } else {
                        data.setCommand(newPrefix);
                    }
                    final String prefix = newPrefix;
                    return ctx.getGuild().flatMap(guild -> ctx.reply("Prefix for " + guild.getName() + (ctx.hasFlag(FLAG_TRICK) ? " tricks" : "") + (prefix.isEmpty() ? " removed" : " set to `" + prefix + "`") + "."));
                })
                .orElse(ctx.error("Cannot change prefix in DMs."));
    }
    
    @Override
    public void init(K9 k9, File dataFolder, Gson gson) {
        super.init(k9, dataFolder, gson);
        CommandListener.prefixes = id -> this.storage.get(Snowflake.of(id)).getCommand();
        CommandTrick.prefixes = id -> this.storage.get(Snowflake.of(id)).getTrick();
    }
    
    @Override
    public void gatherParsers(GsonBuilder builder) {
        super.gatherParsers(builder);
        // Backwards compat: load from string primitive
        builder.registerTypeAdapterFactory(new DelegatingTypeReader<PrefixData>(PrefixData.class) {
            
            @Override
            protected PrefixData readDelegate(TypeAdapter<PrefixData> delegate, JsonReader in) throws IOException {
                if (in.peek() == JsonToken.STRING) {
                    return new PrefixData(in.nextString(), CommandTrick.DEFAULT_PREFIX);
                }
                return super.readDelegate(delegate, in);
            }
        });
    }
    
    @Override
    public String getDescription(CommandContext ctx) {
        return "Set the bot's command prefix for this guild.";
    }
    
    @Override
    public Requirements requirements() {
        return Requirements.builder().with(Permission.MANAGE_GUILD, RequiredType.ALL_OF).build();
    }
}
