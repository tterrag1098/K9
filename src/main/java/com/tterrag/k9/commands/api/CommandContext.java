package com.tterrag.k9.commands.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

import com.tterrag.k9.K9;
import com.tterrag.k9.util.DefaultNonNull;
import com.tterrag.k9.util.Monos;
import com.tterrag.k9.util.NonNull;
import com.tterrag.k9.util.Nullable;
import com.tterrag.k9.util.Patterns;

import discord4j.core.event.domain.message.MessageCreateEvent;
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

@Getter
@DefaultNonNull
public class CommandContext {

    private final Message message;
    private final Optional<Snowflake> guildId;
    @Wither(onMethod = @__({ @NonNull }))
    private final Map<Flag, String> flags;
    @Wither(onMethod = @__({ @NonNull }))
    private final Map<Argument<?>, String> args;

    public CommandContext(MessageCreateEvent evt) {
    	this(evt.getMessage(), evt.getGuildId());
    }
    
    public CommandContext(Message message, Optional<Snowflake> guildId) {
        this(message, guildId, new HashMap<>(), new HashMap<>());
    }
    
    private CommandContext(Message message, Optional<Snowflake> guildId, Map<Flag, String> flags, Map<Argument<?>, String> args) {
    	this.message = message;
    	this.guildId = guildId;
    	this.flags = Collections.unmodifiableMap(flags);
    	this.args = Collections.unmodifiableMap(args);
    }
    
    public Optional<Snowflake> getGuildId() {
        return guildId;
    }
    
    public Mono<Guild> getGuild() {
    	return getMessage().getGuild();
    }
    
    public Snowflake getChannelId() {
        return getMessage().getChannelId();
    }
    
    public Mono<MessageChannel> getChannel() {
    	return getMessage().getChannel();
    }
    
    public Optional<Snowflake> getAuthorId() {
        return getMessage().getAuthorId();
    }
    
    public Mono<User> getAuthor() {
        return getMessage().getAuthor();
    }
    
    public Mono<Member> getMember() {
        return getMessage().getAuthorAsMember();
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
    
    public Mono<Message> reply(String message) {
    	return getMessage().getChannel()
			.transform(Monos.flatZipWith(sanitize(message), (chan, msg) -> chan.createMessage(m -> m.setContent(msg))));
    }
    
    @Deprecated
    public Disposable replyFinal(String message) {
    	return reply(message).subscribe();
    }
    
    public Mono<Message> reply(Consumer<? super EmbedCreateSpec> message) {
    	return getMessage().getChannel().flatMap(c -> c.createMessage(m -> m.setEmbed(message)));
    }
    
    @Deprecated
    public Disposable replyFinal(Consumer<? super EmbedCreateSpec> message) {
        return reply(message).subscribe();
    }

    /**
     * A subinterface of {@link AutoCloseable} that does not throw an exception.
     */
    @FunctionalInterface
    public interface TypingStatus extends AutoCloseable {

        @Override
        void close();
    }
    
    private static class TypingStatusPublisher implements TypingStatus, Publisher<Object> {
        
        private List<Subscriber<? super Object>> subscribers = new ArrayList<>();

        @Override
        public void subscribe(Subscriber<? super Object> s) {
            subscribers.add(s);
        }

        @Override
        public void close() {
            Object dummy = new Object();
            subscribers.forEach(s -> s.onNext(dummy));
        }
    }

    /**
     * Convenience for setting and unsetting the typing status in the current channel. Will automatically handle
     * clearing the state.
     * <p>
     * Example usage:
     * 
     * <pre>
     * try (TypingStatus typing = ctx.setTyping()) {
     *     // Do background work
     * }
     * </pre>
     * <p>
     * Due to the nature of inheriting from {@link AutoCloseable} and try-with-resources statement, the typing status
     * will be automatically unset at the conclusion of the try block.
     * 
     * @return A {@link TypingStatus} representing the typing status, which will be set to false when
     *         {@link AutoCloseable#close()} is called.
     */
    public TypingStatus setTyping() {
        TypingStatusPublisher ret = new TypingStatusPublisher();
        getChannel().subscribe(c -> c.typeUntil(ret));
        return ret;
    }
    
    public Mono<String> sanitize(String message) {
    	return getGuild().flatMap(g -> sanitize(g, message));
    }
    
    public static Mono<String> sanitize(Channel channel, String message) {
        if (channel instanceof GuildChannel) {
            return ((GuildChannel) channel).getGuild().flatMap(g -> sanitize(g, message));
        }
        return Mono.just(message);
    }

    public static Mono<String> sanitize(@Nullable Guild guild, String message) {        
        Mono<String> result = Mono.just(message);
        if (guild == null) return result;
        
    	Matcher matcher = Patterns.DISCORD_MENTION.matcher(message);
    	while (matcher.find()) {
            final String match = matcher.group();
    	    Snowflake id = Snowflake.of(matcher.group(1));
    	    Mono<String> name;
    	    if (match.contains("&")) {
    	        name = K9.instance.getRoleById(guild.getId(), id).map(r -> "the " + r.getName());
    	    } else {
    	        Mono<Member> member = guild.getMembers().filter(p -> p.getId().equals(id)).single();
    	        if (match.contains("!")) {
    	            name = member.map(Member::getDisplayName).map(n -> n.replaceAll("@", "@\u200B"));
    	        } else {
    	            name = member.map(Member::getUsername);
    	        }
    	    }

    		result = result.flatMap(m -> name.map(n -> m.replace(match, n)));
        }
        return result.map(s -> s.replace("@here", "everyone").replace("@everyone", "everyone").replace("@", "@\u200B"));
    }
}
