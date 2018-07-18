package com.tterrag.k9.commands.api;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import com.tterrag.k9.K9;

import discord4j.core.object.entity.Channel;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.GuildChannel;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.MessageChannel;
import discord4j.core.object.entity.User;
import discord4j.core.object.util.Snowflake;
import discord4j.core.spec.EmbedCreateSpec;
import lombok.Getter;
import lombok.experimental.Wither;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.util.annotation.NonNull;
import reactor.util.annotation.Nullable;

@Getter
@ParametersAreNonnullByDefault
public class CommandContext {

    private final Message message;
    @Wither(onMethod = @__({ @NonNull }))
    private final Map<Flag, String> flags;
    @Wither(onMethod = @__({ @NonNull }))
    private final Map<Argument<?>, String> args;

    public CommandContext(Message message) {
    	this(message, new HashMap<>(), new HashMap<>());
    }
    
    public CommandContext(Message message, Map<Flag, String> flags, Map<Argument<?>, String> args) {
    	this.message = message;
    	this.flags = Collections.unmodifiableMap(flags);
    	this.args = Collections.unmodifiableMap(args);
    }
    
    public Mono<Guild> getGuild() {
    	return getMessage().getGuild();
    }
    
    public @NonNull Mono<MessageChannel> getChannel() {
    	return getMessage().getChannel();
    }
    
    public Mono<User> getAuthor() {
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
    
    @Deprecated
    public Mono<Message> reply(String message) {
    	return getMessage().getChannel()
			.zipWith(sanitize(message), (chan, msg) -> chan.createMessage(m -> m.setContent(msg)))
			.flatMap(Function.identity()); // Unroll the nested Mono<Message>
    }
    
    public Disposable replyFinal(String message) {
    	return reply(message).subscribe();
    }
    
    @Deprecated
    public Mono<Message> reply(EmbedCreateSpec message) {
    	return getMessage().getChannel().flatMap(c -> c.createMessage(m -> m.setEmbed(message)));
    }
    
    public Disposable replyFinal(EmbedCreateSpec message) {
        return reply(message).subscribe();
    }
    
    private static final Pattern REGEX_MENTION = Pattern.compile("<@&?!?([0-9]+)>");
    
    public @Nonnull Mono<String> sanitize(String message) {
    	return getGuild().flatMap(g -> sanitize(g, message));
    }
    
    public static @Nonnull Mono<String> sanitize(Channel channel, String message) {
        if (channel instanceof GuildChannel) {
            return ((GuildChannel) channel).getGuild().flatMap(g -> sanitize(g, message));
        }
        return Mono.just(message);
    }

    public static Mono<String> sanitize(@Nullable Guild guild, String message) {
        if (message == null) return Mono.empty();
        
        Mono<String> result = Mono.just(message);
        if (guild == null) return result;
        
    	Matcher matcher = REGEX_MENTION.matcher(message);
    	while (matcher.find()) {
    	    Snowflake id = Snowflake.of(matcher.group(1));
    	    Mono<String> name;
    	    if (matcher.group().contains("&")) {
    	        name = K9.instance.getRoleById(guild.getId(), id).map(r -> "the " + r.getName());
    	    } else {
    	        Mono<Member> member = guild.getMembers().filter(p -> p.getId().equals(id)).single();
    	        if (matcher.group().contains("!")) {
    	            name = member.map(Member::getDisplayName).map(n -> n.replaceAll("@", "@\u200B"));
    	        } else {
    	            name = member.map(Member::getUsername);
    	        }
    	    }

    		result = result.flatMap(m -> name.map(n -> m.replace(matcher.group(), n)));
        }
        return result.map(s -> s.replace("@here", "everyone").replace("@everyone", "everyone").replace("@", "@\u200B"));
    }
}
