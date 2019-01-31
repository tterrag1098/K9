package com.tterrag.k9.commands.api;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import com.tterrag.k9.K9;
import com.tterrag.k9.util.NonNull;
import com.tterrag.k9.util.Nullable;
import com.tterrag.k9.util.Patterns;

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
import sx.blah.discord.util.RequestBuffer;

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

    /**
     * A subinterface of {@link AutoCloseable} that does not throw an exception.
     */
    @FunctionalInterface
    public interface TypingStatus extends AutoCloseable {

        @Override
        void close();
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
        RequestBuffer.request(() -> getChannel().setTypingStatus(true));
        return () -> RequestBuffer.request(() -> getChannel().setTypingStatus(false));
    }

    /**
     * Like {@link #setTyping()}, but returns a no-op {@link TypingStatus} in the case where {@code state} is false.
     * 
     * @param state
     *            The state to set typing to.
     * @return A {@link TypingStatus} representing the typing status, which will be set to false when
     *         {@link AutoCloseable#close()} is called.
     * @see #setTyping()
     */
    public TypingStatus setTyping(boolean state) {
        if (state) {
            return setTyping();
        } else {
            RequestBuffer.request(() -> getChannel().setTypingStatus(false));
            return () -> {};
        }
    }
    
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
        
    	Matcher matcher = Patterns.DISCORD_MENTION.matcher(message);
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
