package com.tterrag.k9.commands.api;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;

import com.tterrag.k9.K9;
import com.tterrag.k9.commands.CommandControl;
import com.tterrag.k9.util.BakedMessage;
import com.tterrag.k9.util.Monos;
import com.tterrag.k9.util.Patterns;
import com.tterrag.k9.util.annotation.NonNull;
import com.tterrag.k9.util.annotation.NonNullFields;
import com.tterrag.k9.util.annotation.NonNullMethods;
import com.tterrag.k9.util.annotation.NonNullParams;
import com.tterrag.k9.util.annotation.Nullable;

import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.Channel;
import discord4j.core.object.entity.channel.GuildChannel;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.legacy.LegacyEmbedCreateSpec;
import discord4j.rest.util.AllowedMentions;
import discord4j.common.util.Snowflake;
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

    private final K9 k9;
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

    public CommandContext(K9 k9, MessageCreateEvent evt) {
    	this(k9, evt.getMessage(), evt.getGuildId());
    }
    
    public CommandContext(K9 k9, Message message, Optional<Snowflake> guildId) {
        this(k9, message, guildId, new HashMap<>(), new HashMap<>());
    }
    
    private CommandContext(K9 k9, Message message, Optional<Snowflake> guildId, Map<Flag, String> flags, Map<Argument<?>, String> args) {
        this.k9 = k9;
    	this.message = message;
    	this.guildId = guildId;
    	this.flags = Collections.unmodifiableMap(flags);
    	this.args = Collections.unmodifiableMap(args);
    	
    	this.guild = message.getGuild().cache();
    	this.channel = message.getChannel().cache();
    	this.author = message.getAuthor();
    	this.member = message.getAuthorAsMember().cache();
    }
    
    public GatewayDiscordClient getClient() {
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
    
    public <T> Mono<T> getArgOrElse(Argument<T> arg, Mono<T> def) {
        return Mono.justOrEmpty(getArgs().get(arg)).map(s -> arg.parse(s)).switchIfEmpty(def);
    }
    
    public <T> T getArgOrGet(Argument<T> arg, Supplier<T> def) {
        return Optional.ofNullable(getArgs().get(arg)).map(s -> arg.parse(s)).orElseGet(def);
    }
    
    public Mono<Message> reply(String message) {
    	return getMessage().getChannel()
			.flatMap(chan -> chan.createMessage(m -> m.setContent(message).setAllowedMentions(AllowedMentions.builder().build())));
    }

    public Mono<Message> progress(String message) {
        return reply(message).transform(this::andThenType);
    }
    
    @Deprecated
    public Disposable replyFinal(String message) {
    	return reply(message).subscribe();
    }
    
    public Mono<Message> reply(Consumer<? super LegacyEmbedCreateSpec> message) {
    	return getMessage().getChannel().flatMap(c -> c.createMessage(m -> m.addEmbed(message)));
    }
    
    public Mono<Message> progress(Consumer<? super LegacyEmbedCreateSpec> message) {
        return reply(message).transform(this::andThenType);
    }
    
    public Mono<Message> reply(BakedMessage message) {
    	return sanitize(message).flatMap(m -> getChannel().flatMap(m::send));
    }
    
    public Mono<Message> progress(BakedMessage message) {
    	return reply(message).transform(this::andThenType);
    }
    
    public <T> Mono<T> andThenType(Mono<T> after) {
        return after.flatMap(o -> getChannel().flatMap(c -> c.type()).thenReturn(o));
    }
    
    @Deprecated
    public Disposable replyFinal(Consumer<? super LegacyEmbedCreateSpec> message) {
        return reply(message).subscribe();
    }
    
    public <T> Mono<T> error(String message) {
        return Mono.error(new CommandException(message));
    }
    
    public <T> Mono<T> error(String message, Throwable cause) {
        return Mono.error(new CommandException(message, cause));
    }
    
    public <T> Mono<T> error(Throwable cause) {
        return Mono.error(new CommandException(cause));
    }
    
    public Mono<BakedMessage> sanitize(BakedMessage message) {
        return Mono.justOrEmpty(message.getContent())
                   .map(message::withContent)
                   .defaultIfEmpty(message);
    }

    public CommandControl.ControlData getControls() {
        return k9.getCommands().getControls(this).orElse(new CommandControl.ControlData());
    }
}
