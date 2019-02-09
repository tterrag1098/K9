package com.tterrag.k9.commands.api;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;

import com.tterrag.k9.util.Monos;
import com.tterrag.k9.util.Patterns;
import com.tterrag.k9.util.annotation.NonNull;
import com.tterrag.k9.util.annotation.NonNullFields;
import com.tterrag.k9.util.annotation.NonNullMethods;
import com.tterrag.k9.util.annotation.NonNullParams;
import com.tterrag.k9.util.annotation.Nullable;

import discord4j.core.DiscordClient;
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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Wither;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

@Getter
@NonNullFields
@NonNullMethods
@NonNullParams
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CommandContext {

    private final Message message;
    private final Optional<Snowflake> guildId;
    @Wither(onMethod = @__({ @NonNull }))
    private final Map<Flag, String> flags;
    @Wither(onMethod = @__({ @NonNull }))
    private final Map<Argument<?>, String> args;
    
    // Cached monos
    private final Mono<Guild> guild;
    private final Mono<MessageChannel> channel;
    private final Optional<User> author;
    private final Mono<Member> member;

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
    	
    	this.guild = message.getGuild().cache();
    	this.channel = message.getChannel().cache();
    	this.author = message.getAuthor();
    	this.member = message.getAuthorAsMember().cache();
    }
    
    public DiscordClient getClient() {
        return message.getClient();
    }
    
    public Optional<Snowflake> getGuildId() {
        return guildId;
    }
    
    public Snowflake getChannelId() {
        return getMessage().getChannelId();
    }
    
    public Optional<Snowflake> getAuthorId() {
        return getMessage().getAuthor().map(User::getId);
    }
    
    public Mono<String> getDisplayName() {
        return getMember().map(Member::getDisplayName)
                .switchIfEmpty(Mono.justOrEmpty(getAuthor().map(User::getUsername)))
                .switchIfEmpty(message.getWebhook().flatMap(w -> Mono.justOrEmpty(w.getName())))
                .defaultIfEmpty("Unknown");
    }
    
    public Mono<String> getDisplayName(User user) {
        return Mono.justOrEmpty(getGuildId())
                .flatMap(user::asMember)
                .map(Member::getDisplayName)
                .defaultIfEmpty(user.getUsername());
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
    
    public Mono<Message> progress(String message) {
        return reply(message).transform(this::andThenType);
    }
    
    @Deprecated
    public Disposable replyFinal(String message) {
    	return reply(message).subscribe();
    }
    
    public Mono<Message> reply(Consumer<? super EmbedCreateSpec> message) {
    	return getMessage().getChannel().flatMap(c -> c.createMessage(m -> m.setEmbed(message)));
    }
    
    public Mono<Message> progress(Consumer<? super EmbedCreateSpec> message) {
        return reply(message).transform(this::andThenType);
    }
    
    public <T> Mono<T> andThenType(Mono<T> after) {
        return after.flatMap(o -> getChannel().flatMap(c -> c.type()).thenReturn(o));
    }
    
    @Deprecated
    public Disposable replyFinal(Consumer<? super EmbedCreateSpec> message) {
        return reply(message).subscribe();
    }
    
    public <T> Mono<T> error(String message) {
        return Mono.error(new CommandException(message));
    }
    
    public <T> Mono<T> error(Throwable cause) {
        return Mono.error(new CommandException(cause));
    }
    
    public Mono<String> sanitize(String message) {
    	return getGuild().flatMap(g -> sanitize(g, message)).switchIfEmpty(Mono.just(message));
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
    	        name = guild.getClient().getRoleById(guild.getId(), id).map(r -> "the " + r.getName());
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
