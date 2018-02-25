package com.tterrag.k9.commands.api;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.tterrag.k9.K9;
import com.tterrag.k9.util.NonNull;
import com.tterrag.k9.util.NullHelper;
import com.tterrag.k9.util.Nullable;

import lombok.Getter;
import lombok.experimental.Wither;
import sx.blah.discord.api.internal.json.objects.EmbedObject;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IGuild;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.handle.obj.IUser;
import sx.blah.discord.util.RequestBuffer;
import sx.blah.discord.util.RequestBuffer.IRequest;
import sx.blah.discord.util.RequestBuffer.RequestFuture;

@Getter
public class CommandContext {

    private final IMessage message;
    @Wither(onMethod = @__({ @NonNull }))
    private final Map<Flag, String> flags;
    @Wither(onMethod = @__({ @NonNull }))
    private final Map<Argument<?>, String> args;

    public CommandContext(IMessage message) {
    	this(message, new HashMap<>(), new HashMap<>());
    }
    
    public CommandContext(IMessage message, Map<Flag, String> flags, Map<Argument<?>, String> args) {
    	this.message = message;
    	this.flags = Collections.unmodifiableMap(flags);
    	this.args = Collections.unmodifiableMap(args);
    }
    
    public @Nullable IGuild getGuild() {
    	return getMessage().getGuild();
    }
    
    public @NonNull IChannel getChannel() {
    	return NullHelper.notnullD(getMessage().getChannel(), "IMessage#getChannel");
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
    
    public <T> T getArg(Argument<T> arg) {
        return arg.parse(getArgs().get(arg));
    }
    
    public <T> T getArgOrElse(Argument<T> arg, T def) {
        return getArgOrGet(arg, (Supplier<T>) () -> def);
    }
    
    public <T> T getArgOrGet(Argument<T> arg, Supplier<T> def) {
        return Optional.ofNullable(getArgs().get(arg)).map(s -> arg.parse(s)).orElseGet(def);
    }
    
    public RequestFuture<IMessage> replyBuffered(String message) {
        return RequestBuffer.request((IRequest<IMessage>) () -> reply(message));
    }
    
    public RequestFuture<IMessage> replyBuffered(EmbedObject message) {
        return RequestBuffer.request((IRequest<IMessage>) () -> reply(message));
    }
    
    public IMessage reply(String message) {
    	return getMessage().getChannel().sendMessage(sanitize(message));
    }
    
    public IMessage reply(EmbedObject message) {
    	return getMessage().getChannel().sendMessage(message);
    }
    
    private static final Pattern REGEX_MENTION = Pattern.compile("<@&?!?([0-9]+)>");
    
    public String sanitize(String message) {
    	return sanitize(getGuild(), message);
    }
    
    public static String sanitize(IChannel channel, String message) {
        return channel.isPrivate() ? message : sanitize(channel.getGuild(), message);
    }

    public static String sanitize(@Nullable IGuild guild, String message) {
        if (message == null) return null;
        if (guild == null) return message;
        
    	Matcher matcher = REGEX_MENTION.matcher(message);
    	while (matcher.find()) {
    	    long id = Long.parseLong(matcher.group(1));
    	    String name;
    	    if (matcher.group().contains("&")) {
    	        name = "the " + K9.instance.getRoleByID(id).getName();
    	    } else {
    	        IUser user = guild.getUserByID(id);
    	        if (matcher.group().contains("!")) {
    	            name = user.getDisplayName(guild).replaceAll("@", "@\u200B");
    	        } else {
    	            name = user.getName();
    	        }
    	    }

    		message = message.replace(matcher.group(), name);
        }
        return message.replace("@here", "everyone").replace("@everyone", "everyone").replace("@", "@\u200B");
    }

    public EmbedObject sanitize(EmbedObject embed) {
    	return sanitize(getGuild(), embed);
    }
    
    public static EmbedObject sanitize(IChannel channel, EmbedObject embed) {
        return channel.isPrivate() ? embed : sanitize(channel.getGuild(), embed);
    }

    public static EmbedObject sanitize(IGuild guild, EmbedObject embed) {
        if (embed == null) return null;
        
    	embed.title = sanitize(guild, embed.title);
    	embed.description = sanitize(guild, embed.description);
    	return embed;
    }
}
