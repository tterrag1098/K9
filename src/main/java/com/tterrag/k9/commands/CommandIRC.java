package com.tterrag.k9.commands;

import java.io.File;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.commons.lang3.tuple.Pair;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;
import com.tterrag.k9.K9;
import com.tterrag.k9.commands.api.Command;
import com.tterrag.k9.commands.api.CommandContext;
import com.tterrag.k9.commands.api.CommandException;
import com.tterrag.k9.commands.api.CommandPersisted;
import com.tterrag.k9.commands.api.Flag;
import com.tterrag.k9.irc.IRC;
import com.tterrag.k9.util.Patterns;
import com.tterrag.k9.util.Requirements;
import com.tterrag.k9.util.Requirements.RequiredType;

import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IGuild;
import sx.blah.discord.handle.obj.Permissions;

@Command
public class CommandIRC extends CommandPersisted<Map<Long, Pair<String, Boolean>>> {
    
    private static class PairAdapter implements JsonDeserializer<Pair<String, Boolean>>, JsonSerializer<Pair<String, Boolean>> {

        @Override
        public JsonElement serialize(Pair<String, Boolean> src, Type typeOfSrc, JsonSerializationContext context) {
            JsonArray ret = new JsonArray();
            ret.add(src.getLeft());
            ret.add(src.getRight());
            return ret;
        }

        @Override
        public Pair<String, Boolean> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            if (json.isJsonArray() && json.getAsJsonArray().size() == 2) {
                JsonArray arr = json.getAsJsonArray();
                return Pair.of(arr.get(0).getAsString(), arr.get(1).getAsBoolean());
            }
            throw new JsonParseException("Cannot deserialize Pair");
        }
    }
    
    private static final Flag FLAG_ADD = new SimpleFlag('a', "add", "Add a new relay channel.", false);
    private static final Flag FLAG_READONLY = new SimpleFlag('o', "readonly", "Mark this relay as readonly, that is, messages cannot be sent to IRC from Discord.", false);
    private static final Flag FLAG_REMOVE = new SimpleFlag('r', "remove", "Remove a relay channel.", false);
    
    private static final WordArgument ARG_DISCORD_CHAN = new WordArgument("discord_channel", "The Discord channel.", true) {
        
        @Override
        public Pattern pattern() {
            return Patterns.DISCORD_CHANNEL;
        }
    };
    private static final WordArgument ARG_IRC_CHAN = new WordArgument("irc_channel", "The IRC channel.", false) {
        
        @Override
        public Pattern pattern() {
            return Patterns.IRC_CHANNEL;
        }
    };

    public CommandIRC() {
        super("irc", false, HashMap::new);
    }

    @Override
    protected TypeToken<Map<Long, Pair<String, Boolean>>> getDataType() {
        return new TypeToken<Map<Long, Pair<String, Boolean>>>(){};
    }
    
    @Override
    public void gatherParsers(GsonBuilder builder) {
        builder.registerTypeAdapter(new TypeToken<Pair<String, Boolean>>(){}.getType(), new PairAdapter());
    }
    
    @Override
    public void init(File dataFolder, Gson gson) {
        super.init(dataFolder, gson);
        for (IGuild guild : K9.instance.getGuilds()) {
            storage.get(guild).forEach((chan, data) -> IRC.INSTANCE.addChannel(data.getLeft(), K9.instance.getChannelByID(chan), data.getRight()));
        }
    }

    @Override
    public void process(CommandContext ctx) throws CommandException {
        IChannel chan = ctx.getMessage().getChannelMentions().get(0);
        if (!chan.mention().equals(ctx.getArg(ARG_DISCORD_CHAN))) {
            throw new CommandException("Invalid channel.");
        }
        if (ctx.hasFlag(FLAG_ADD)) {
            String ircChan = ctx.getArg(ARG_IRC_CHAN);
            if (ircChan == null) {
                throw new CommandException("Must provide IRC channel.");
            }
            // To avoid conflicts between IRC channel name and discord channel name
            if (!ircChan.startsWith("#")) {
                ircChan = "#" + ircChan;
            }
            IRC.INSTANCE.addChannel(ircChan, chan, ctx.hasFlag(FLAG_READONLY));
            getData(ctx).put(chan.getLongID(), Pair.of(ircChan, ctx.hasFlag(FLAG_READONLY)));
        } else if (ctx.hasFlag(FLAG_REMOVE)) {
            Pair<String, Boolean> data = getData(ctx).get(chan.getLongID());
            String ircChan = data == null ? null : data.getLeft();
            if (ircChan == null) {
                throw new CommandException("There is no relay in this channel.");
            }
            IRC.INSTANCE.removeChannel(ircChan, chan);
            getData(ctx).remove(chan.getLongID());
        }
    }

    @Override
    public String getDescription() {
        return "Bind an IRC relay to a channel";
    }

    @Override
    public Requirements requirements() {
        return Requirements.builder().with(Permissions.MANAGE_SERVER, RequiredType.ALL_OF).build();
    }
}
