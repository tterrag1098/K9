package com.blamejared.mcbot.commands;

import java.io.File;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.blamejared.mcbot.MCBot;
import com.blamejared.mcbot.commands.CommandCustomPing.CustomPing;
import com.blamejared.mcbot.commands.api.Argument;
import com.blamejared.mcbot.commands.api.Command;
import com.blamejared.mcbot.commands.api.CommandContext;
import com.blamejared.mcbot.commands.api.CommandException;
import com.blamejared.mcbot.commands.api.CommandPersisted;
import com.blamejared.mcbot.commands.api.Flag;
import com.blamejared.mcbot.util.NonNull;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import lombok.Value;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageUpdateEvent;
import sx.blah.discord.handle.obj.IGuild;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.handle.obj.IUser;
import sx.blah.discord.handle.obj.Permissions;

@Command
public class CommandCustomPing extends CommandPersisted<Map<Long, List<CustomPing>>> {
    
    @Value
    public static class CustomPing {
        Pattern pattern;
        String text;
    }
    
    private class PingListener {
        
        @EventSubscriber
        public void onMessageRecieved(MessageReceivedEvent event) {
            checkCustomPing(event.getMessage());
        }
        
        @EventSubscriber
        public void onMessageEdited(MessageUpdateEvent event){
            checkCustomPing(event.getMessage());
        }
        
        private void checkCustomPing(IMessage msg) {
            if (msg.getChannel().isPrivate() || msg.getAuthor().equals(MCBot.instance.getOurUser())) return;
            
            Multimap<Long, CustomPing> pings = HashMultimap.create();
            CommandCustomPing.this.getPingsForGuild(msg.getGuild()).forEach(pings::putAll);
            for (Entry<Long, CustomPing> e : pings.entries()) {
                if (e.getKey() == msg.getAuthor().getLongID()) {
                    continue;
                }
                IUser owner = msg.getGuild().getUserByID(e.getKey());
                if (owner == null || !msg.getChannel().getModifiedPermissions(owner).contains(Permissions.READ_MESSAGES)) {
                    continue;
                }
                Matcher matcher = e.getValue().getPattern().matcher(msg.getContent());
                if (matcher.find()) {
                    owner.getOrCreatePMChannel().sendMessage(e.getValue().getText() + " <#" + msg.getChannel().getStringID() + ">");
                }
            }
        }
    }
    
    @NonNull
    public static final String NAME = "ping";
    
    private static final Flag FLAG_ADD = new SimpleFlag("add", "Adds a new custom ping.", false);
    private static final Flag FLAG_RM = new SimpleFlag("rm", "Removes a custom ping by its pattern.", true);
    
    private static final Pattern REGEX_PATTERN = Pattern.compile("\\/(.*?)\\/");

    private static final Argument<String> ARG_PATTERN = new WordArgument("pattern", "The regex pattern to match messages against for a ping to be sent to you.", true) {
        @Override
        public Pattern pattern() {
            return REGEX_PATTERN;
        }
        
        @Override
        public boolean required(Collection<Flag> flags) {
           return !flags.contains(FLAG_RM);
        }
    };
    
    private static final Argument<String> ARG_TEXT = new SentenceArgument("pingtext", "The text to use in the ping.", false);

    public CommandCustomPing() {
        super(NAME, false, Lists.newArrayList(FLAG_ADD, FLAG_RM), Lists.newArrayList(ARG_PATTERN, ARG_TEXT), HashMap::new);
    }
    
    @Override
    public void onRegister() {
        super.onRegister();
        MCBot.instance.getDispatcher().registerListener(new PingListener());
    }
    
    @Override
    public void init(File dataFolder, Gson gson) {
        super.init(dataFolder, gson);
    }
    
    @Override
    public void gatherParsers(GsonBuilder builder) {
        builder.registerTypeAdapter(Pattern.class, new JsonDeserializer<Pattern>() {
            @Override
            public Pattern deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
                if (json.isJsonObject()) {
                    String pattern = json.getAsJsonObject().get("pattern").getAsString();
                    int flags = json.getAsJsonObject().get("flags").getAsInt();
                    return Pattern.compile(pattern, flags);
                }
                throw new JsonParseException("Pattern must be an object");
            }
        });
    }
    
    public Map<Long, List<CustomPing>> getPingsForGuild(IGuild guild) {
        return storage.get(guild);
    }

    @Override
    public void process(CommandContext ctx) throws CommandException {
        if (ctx.hasFlag(FLAG_ADD)) {
            Matcher matcher = REGEX_PATTERN.matcher(ctx.getArg(ARG_PATTERN));
            matcher.find();
            Pattern pattern = Pattern.compile(matcher.group(1));
            
            String text = ctx.getArgOrElse(ARG_TEXT, "You have a new ping!");
            CustomPing ping = new CustomPing(pattern, text);
            
            // Lie a bit, do this first so it doesn't ping for itself
            ctx.replyBuffered("Added a new custom ping for the pattern: " + pattern);
            
            storage.get(ctx).computeIfAbsent(ctx.getAuthor().getLongID(), id -> new ArrayList<>()).add(ping);
        } else if (ctx.hasFlag(FLAG_RM)) {
            if (storage.get(ctx).getOrDefault(ctx.getAuthor().getLongID(), Collections.emptyList()).removeIf(ping -> ping.getPattern().pattern().equals(ctx.getFlag(FLAG_RM)))) {
                ctx.replyBuffered("Deleted ping(s).");
            } else {
                ctx.replyBuffered("Found no pings to delete!");
            }
        }
    }
    
    @Override
    public String getDescription() {
        return "Use this command to create a custom ping, when any message is sent in this guild that matches the given regex, you will be notified via PM.";
    }

    @Override
    protected TypeToken<Map<Long, List<CustomPing>>> getDataType() {
        return new TypeToken<Map<Long, List<CustomPing>>>(){};
    }
}
