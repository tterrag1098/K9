package com.blamejared.mcbot.commands.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.Getter;
import lombok.experimental.Wither;
import sx.blah.discord.api.internal.json.objects.EmbedObject;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IGuild;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.handle.obj.IUser;

import com.blamejared.mcbot.MCBot;
import com.blamejared.mcbot.util.Nullable;

@Getter
public class CommandContext {
    
    private final IMessage message;
    @Wither
    private final Map<Flag, String> flags;
    @Wither
    private final List<String> args;
    
    public CommandContext(IMessage message) {
    	this(message, new HashMap<>(), new ArrayList<>());
    }
    
    public CommandContext(IMessage message, Map<Flag, String> flags, List<String> args) {
    	this.message = message;
    	this.flags = Collections.unmodifiableMap(flags);
    	this.args = Collections.unmodifiableList(args);
    }
    
    public IGuild getGuild() {
    	return getMessage().getGuild();
    }
    
    public IChannel getChannel() {
    	return getMessage().getChannel();
    }
    
    public IUser getAuthor() {
        return getMessage().getAuthor();
    }
    
    public boolean hasFlag(Flag flag) {
        return getFlags().containsKey(flag);
    }
    
    public @Nullable String getFlag(Flag flag) {
        return getFlags().get(flag);
    }
    
    public int argCount() {
        return getArgs().size();
    }
    
    public String getArg(int idx) {
        return getArgs().get(idx);
    }
    
    public IMessage reply(String message) {
    	return getMessage().getChannel().sendMessage(sanitize(message));
    }
    
    public IMessage reply(EmbedObject message) {
    	return getMessage().getChannel().sendMessage(sanitize(message));
    }
    
    private static final Pattern REGEX_MENTION = Pattern.compile("<@&?!?([0-9]+)>");
    
    public String sanitize(String message) {
    	return sanitize(getGuild(), message);
    }

    public static String sanitize(IGuild guild, String message) {
        if (message == null) return null;
        
    	Matcher matcher = REGEX_MENTION.matcher(message);
    	while (matcher.find()) {
    	    long id = Long.parseLong(matcher.group(1));
    	    String name;
    	    if (matcher.group().contains("&")) {
    	        name = "the " + MCBot.instance.getRoleByID(id).getName();
    	    } else {
    	        IUser user = guild.getUserByID(id);
    	        if (matcher.group().contains("!")) {
    	            name = user.getNicknameForGuild(guild).replaceAll("@", "@\u200B");
    	        } else {
    	            name = user.getName();
    	        }
    	    }

    		message = message.replace(matcher.group(), name);
        }
        return message.replace("@here", "\u200Beveryone").replace("@everyone", "\u200Beveryone");
    }

    public EmbedObject sanitize(EmbedObject embed) {
    	return sanitize(getGuild(), embed);
    }

    public static EmbedObject sanitize(IGuild guild, EmbedObject embed) {
    	embed.title = sanitize(guild, embed.title);
    	embed.description = sanitize(guild, embed.description);
    	return embed;
    }
}
