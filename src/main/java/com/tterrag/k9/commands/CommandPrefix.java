package com.tterrag.k9.commands;

import java.io.File;
import java.io.IOException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.tterrag.k9.commands.CommandPrefix.PrefixData;
import com.tterrag.k9.commands.api.Command;
import com.tterrag.k9.commands.api.CommandContext;
import com.tterrag.k9.commands.api.CommandException;
import com.tterrag.k9.commands.api.CommandPersisted;
import com.tterrag.k9.commands.api.Flag;
import com.tterrag.k9.listeners.CommandListener;
import com.tterrag.k9.util.GuildStorage;
import com.tterrag.k9.util.Requirements;
import com.tterrag.k9.util.Requirements.RequiredType;

import discord4j.core.object.entity.Guild;
import discord4j.core.object.util.Permission;
import discord4j.core.object.util.Snowflake;
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
    
    private static final Flag FLAG_TRICK = new SimpleFlag('t', "trick", "Modify the prefix for tricks.", false);
    
    public CommandPrefix() {
        super("prefix", false, PrefixData::new);
    }

    @Override
    protected TypeToken<PrefixData> getDataType() {
        return TypeToken.get(PrefixData.class);
    }
    
    @Override
    public Mono<?> process(CommandContext ctx) throws CommandException {
        Guild guild = ctx.getGuild().block();
        if (guild == null) {
            throw new CommandException("Cannot change prefix in private channel!");
        }
        PrefixData data = this.storage.get(ctx).block();
        String newPrefix = ctx.getArgOrElse(ARG_PREFIX, CommandListener.DEFAULT_PREFIX);
        if (ctx.hasFlag(FLAG_TRICK)) {
            data.setTrick(newPrefix);
        } else {
            data.setCommand(newPrefix);
        }
        return ctx.reply("Prefix for " + guild.getName() + (ctx.hasFlag(FLAG_TRICK) ? " tricks" : "") + " set to `" + newPrefix + "`.");
    }
    
    @Override
    public void init(File dataFolder, Gson gson) {
        super.init(dataFolder, gson);
        CommandListener.prefixes = id -> this.storage.get(Snowflake.of(id)).getCommand();
        CommandTrick.prefixes = id -> this.storage.get(Snowflake.of(id)).getTrick();
    }
    
    @Override
    public void gatherParsers(GsonBuilder builder) {
        super.gatherParsers(builder);
        // Backwards compat: load from string primitive
        builder.registerTypeAdapterFactory(new TypeAdapterFactory() {
            
            @Override
            public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
                if (type.getRawType() != PrefixData.class) {
                    return null;
                }
                final TypeAdapter<T> delegate = gson.getDelegateAdapter(this, type);
                return new TypeAdapter<T>() {

                    public void write(JsonWriter out, T value) throws IOException {
                        delegate.write(out, value);
                    }

                    public T read(JsonReader in) throws IOException {
                        if (in.peek() == JsonToken.STRING) {
                            return (T) new PrefixData(in.nextString(), CommandTrick.DEFAULT_PREFIX);
                        }
                        return delegate.read(in);
                    }
                };
            }
        });
    }
    
    @Override
    public String getDescription() {
        return "Set the bot's command prefix for this guild.";
    }
    
    @Override
    public Requirements requirements() {
        return Requirements.builder().with(Permission.MANAGE_GUILD, RequiredType.ALL_OF).build();
    }
}
