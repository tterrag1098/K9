package com.tterrag.k9.commands;

import java.io.File;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
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
import com.tterrag.k9.commands.api.Command;
import com.tterrag.k9.commands.api.CommandContext;
import com.tterrag.k9.commands.api.CommandPersisted;
import com.tterrag.k9.commands.api.Flag;
import com.tterrag.k9.irc.IRC;
import com.tterrag.k9.util.Patterns;
import com.tterrag.k9.util.Requirements;
import com.tterrag.k9.util.Requirements.RequiredType;

import discord4j.core.DiscordClient;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.TextChannel;
import discord4j.core.object.util.Permission;
import discord4j.core.object.util.Snowflake;
import reactor.core.publisher.Mono;

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
    public void init(DiscordClient client, File dataFolder, Gson gson) {
        super.init(client, dataFolder, gson);
        for (Guild guild : client.getGuilds().collectList().block()) {
            storage.get(guild).forEach((chan, data) -> IRC.INSTANCE.addChannel(data.getLeft(), (TextChannel) client.getChannelById(Snowflake.of(chan)).block(), data.getRight()));
        }
    }

    @Override
    public Mono<?> process(CommandContext ctx) {
        if (!ctx.getGuildId().isPresent()) {
            return ctx.error("IRC is not available in DMs.");
        }
        String chanMention = ctx.getArg(ARG_DISCORD_CHAN);
        Matcher m = Patterns.DISCORD_CHANNEL.matcher(chanMention);
        TextChannel chan;
        if (m.matches()) {
            chan = ctx.getGuild().block().getChannelById(Snowflake.of(m.group(1))).ofType(TextChannel.class).block();
        } else {
            return ctx.error("Not a valid channel.");
        }
        if (chan == null) {
            return ctx.error("Invalid channel mention.");
        }
        if (ctx.hasFlag(FLAG_ADD)) {
            String ircChan = ctx.getArg(ARG_IRC_CHAN);
            if (ircChan == null) {
                return ctx.error("Must provide IRC channel.");
            }
            // To avoid conflicts between IRC channel name and discord channel name
            if (!ircChan.startsWith("#")) {
                ircChan = "#" + ircChan;
            }
            IRC.INSTANCE.addChannel(ircChan, chan, ctx.hasFlag(FLAG_READONLY));
            final String irc = ircChan;
            return getData(ctx).doOnNext(data -> data.put(chan.getId().asLong(), Pair.of(irc, ctx.hasFlag(FLAG_READONLY))));
        } else if (ctx.hasFlag(FLAG_REMOVE)) {
            Pair<String, Boolean> data = getData(ctx).block().get(chan.getId().asLong());
            String ircChan = data == null ? null : data.getLeft();
            if (ircChan == null) {
                return ctx.error("There is no relay in this channel.");
            }
            IRC.INSTANCE.removeChannel(ircChan, chan);
            return getData(ctx).doOnNext(d -> d.remove(chan.getId().asLong()));
        }
        return Mono.empty();
    }

    @Override
    public String getDescription() {
        return "Bind an IRC relay to a channel";
    }

    @Override
    public Requirements requirements() {
        return Requirements.builder().with(Permission.MANAGE_GUILD, RequiredType.ALL_OF).build();
    }
}
