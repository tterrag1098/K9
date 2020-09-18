package com.tterrag.k9.commands;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.tterrag.k9.commands.CommandCustomPing.CustomPing;
import com.tterrag.k9.commands.api.Argument;
import com.tterrag.k9.commands.api.Command;
import com.tterrag.k9.commands.api.CommandContext;
import com.tterrag.k9.commands.api.CommandPersisted;
import com.tterrag.k9.commands.api.CommandRegistrar;
import com.tterrag.k9.commands.api.Flag;
import com.tterrag.k9.commands.api.ReadyContext;
import com.tterrag.k9.util.InterruptibleCharSequence;
import com.tterrag.k9.util.ListMessageBuilder;
import com.tterrag.k9.util.Monos;
import com.tterrag.k9.util.Patterns;
import com.tterrag.k9.util.annotation.NonNull;

import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.PrivateChannel;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.rest.http.client.ClientException;
import discord4j.rest.util.Permission;
import discord4j.common.util.Snowflake;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@Command
@Slf4j
public class CommandCustomPing extends CommandPersisted<Map<Long, List<CustomPing>>> {
    
    @Value
    public static class CustomPing {
        Pattern pattern;
        String text;
    }
    
    private static final Predicate<Throwable> IS_403_ERROR = ClientException.isStatusCode(403);
    
    @RequiredArgsConstructor
    private class PingListener {
        
        private final AtomicReference<Thread> schedulerThread = new AtomicReference<>();
        
        private final ThreadFactory threadFactory = new ThreadFactory() {

            @Override
            public synchronized Thread newThread(Runnable r) {
                schedulerThread.set(new Thread(r, "Custom Ping Listener"));
                return schedulerThread.get();
            }
        };
        
        private final Scheduler scheduler = Schedulers.newSingle(threadFactory);
        
        private final CommandRegistrar registrar;
        
        public Mono<Void> onMessageRecieved(MessageCreateEvent event) {
            if (!Sets.newHashSet(registrar.getCommands(event.getGuildId())).contains(CommandCustomPing.this)) return Mono.empty();
            return Mono.justOrEmpty(event.getMember())
                    .filter(a -> !a.getId().equals(event.getClient().getSelfId()))
                    .flatMap(author -> event.getMessage().getChannel()
                            .ofType(TextChannel.class)
                            .transform(Monos.flatZipWith(event.getGuild(), (channel, guild) -> checkPings(event, author, channel, guild))));
        }
        
        private Mono<Void> checkPings(MessageCreateEvent event, Member author, TextChannel channel, Guild guild) {
            ListMultimap<Long, CustomPing> pings = ArrayListMultimap.create();
            CommandCustomPing.this.getPingsForGuild(guild).forEach(pings::putAll);
            final AtomicBoolean modified = new AtomicBoolean(false);
            return Flux.fromIterable(pings.entries())
                .filter(e -> e.getKey().longValue() != author.getId().asLong())
                .filterWhen(e -> canViewChannel(guild, Snowflake.of(e.getKey()), channel))
                .flatMap(e -> Mono.just(e.getValue().getPattern())
                    .publishOn(scheduler)
                    .filterWhen(p -> pingMatches(event.getMessage(), p)
                        .timeout(Duration.ofSeconds(1), Mono.fromSupplier(() -> {
                            log.warn("Removing ping {} for user {} as it took too long to resolve.", e.getValue().getPattern().pattern(), e.getKey());
                            pings.remove(e.getKey(), e.getValue());
                            modified.set(true);
                            schedulerThread.get().interrupt();
                            return false;
                        })))
                    .flatMap($ -> event.getClient().getUserById(Snowflake.of(e.getKey()))
                        .flatMap(User::getPrivateChannel)
                        .flatMap(c -> sendPingMessage(c, author, event.getMessage(), guild, e.getValue().getText())
                            .onErrorResume(IS_403_ERROR, t -> {
                                log.warn("Removing pings for user {} as DMs are disabled.", e.getKey());
                                return Mono.fromRunnable(() -> {
                                    pings.removeAll(e.getKey());
                                    modified.set(true);
                                });
                            })))
                    .thenReturn(e))
                .flatMap(e -> Mono.just(modified)
                    .filter(AtomicBoolean::get)
                    .map($ -> storage.get(guild).put(e.getKey(), Collections.synchronizedList(Lists.newArrayList(pings.get(e.getKey()))))))
                .then();
        }

        private Mono<Boolean> canViewChannel(Guild guild, Snowflake member, TextChannel channel) {
            return guild.getMemberById(member)
                    .onErrorResume($ -> Mono.empty())
                    .flatMap(m -> channel.getEffectivePermissions(m.getId()))
                    .map(perms -> perms.contains(Permission.VIEW_CHANNEL));
        }
        
        private Mono<Boolean> pingMatches(Message message, Pattern pattern) {
            return Mono.fromSupplier(() -> pattern.matcher(new InterruptibleCharSequence(message.getContent())))
                .flatMap(m -> Mono.fromCallable(m::find)
                    .onErrorReturn(false));
        }
        
        private Mono<Message> sendPingMessage(PrivateChannel dm, Member author, Message original, Guild from, String pingText) {
            return dm.createMessage(m -> m.setEmbed(embed -> embed
                .setAuthor("New ping from: " + author.getDisplayName(), author.getAvatarUrl(), null)
                .addField(pingText, original.getContent(), false)
                .addField("Link", String.format("https://discord.com/channels/%d/%d/%d", from.getId().asLong(), original.getChannelId().asLong(), original.getId().asLong()), false)));
        }
    }
    
    @NonNull
    public static final String NAME = "ping";
    
    private static final Flag FLAG_ADD = new SimpleFlag('a', "add", "Adds a new custom ping.", false);
    private static final Flag FLAG_RM = new SimpleFlag('r', "remove", "Removes a custom ping by its pattern.", true);
    private static final Flag FLAG_LS = new SimpleFlag('l', "list", "Lists your pings for this guild.", false);

    private static final Argument<String> ARG_PATTERN = new WordArgument("pattern", "The regex pattern to match messages against for a ping to be sent to you. Must be inside slashes, e.g. `/foo.*bar/`.", true) {
        @Override
        public Pattern pattern() {
            return Patterns.REGEX_PATTERN;
        }
        
        @Override
        public boolean required(Collection<Flag> flags) {
           return flags.contains(FLAG_ADD);
        }
    };
    
    private static final Argument<String> ARG_TEXT = new SentenceArgument("pingtext", "The text to use when notifying you about the ping.", false);

    public CommandCustomPing() {
        super(NAME, false, HashMap::new);
    }
    
    @Override
    public Mono<?> onReady(ReadyContext ctx) {
        final PingListener listener = new PingListener(ctx.getK9().getCommands());
        return super.onReady(ctx)
                .then(ctx.on(MessageCreateEvent.class)
                    .flatMap(e -> listener.onMessageRecieved(e)
                        .doOnError(t -> log.error("Error handling pings:", t))
                        .onErrorResume(t -> Mono.empty()))
                    .then());
    }
    
    @Override
    public void gatherParsers(GsonBuilder builder) {
        builder.registerTypeAdapter(Pattern.class, (JsonDeserializer<Pattern>) (json, typeOfT, context) -> {
            if (json.isJsonObject()) {
                String pattern = json.getAsJsonObject().get("pattern").getAsString();
                int flags = json.getAsJsonObject().get("flags").getAsInt();
                return Pattern.compile(pattern, flags);
            }
            throw new JsonParseException("Pattern must be an object");
        });
    }
    
    public Map<Long, List<CustomPing>> getPingsForGuild(Guild guild) {
        if (storage == null) {
            return Collections.emptyMap();
        }
        return storage.get(guild);
    }

    @Override
    public Mono<?> process(CommandContext ctx) {
        if (!ctx.getGuildId().isPresent()) {
            return ctx.error("Custom pings are not available in DMs.");
        }
        User author = ctx.getMessage().getAuthor().orElse(null);
        if (author == null) {
            return ctx.error("Cannot add custom ping without an author.");
        }
        long authorId = author.getId().asLong();
        if (ctx.hasFlag(FLAG_LS)) {
            return storage.get(ctx)
                    .map(data -> data.getOrDefault(authorId, Collections.emptyList()))
                    .filter(data -> !data.isEmpty())
                    .map(pings -> ctx.getChannel().flatMap(channel -> new ListMessageBuilder<CustomPing>("custom pings")
                        .addObjects(pings)
                        .objectsPerPage(10)
                        .indexFunc((p, i) -> i) // 0-indexed
                        .stringFunc(p -> "`/" + p.getPattern().pattern() + "/` | " + p.getText())
                        .build(channel, ctx.getMessage())
                        .send()))
                    .orElse(ctx.error("No pings to list!"));
        } else if (ctx.hasFlag(FLAG_ADD)) {
            Matcher matcher = Patterns.REGEX_PATTERN.matcher(ctx.getArg(ARG_PATTERN));
            matcher.find();
            Pattern pattern = Pattern.compile(matcher.group(1));
            
            String text = ctx.getArgOrElse(ARG_TEXT, "You have a new ping!");
            CustomPing ping = new CustomPing(pattern, text);
            
            return storage.get(ctx)
                      .map(data -> data.computeIfAbsent(authorId, $ -> Collections.synchronizedList(new ArrayList<>())))
                      .map(pings -> pings.isEmpty()
                              ? Mono.justOrEmpty(ctx.getAuthor())
                                    .flatMap(User::getPrivateChannel)
                                    .flatMap(c -> ctx.getGuild().flatMap(g -> c.createMessage("You just added your first ping for **" + g.getName() + "**. This is a test message to be sure that you are able to receive DMs from there.")))
                                    .onErrorResume(IS_403_ERROR, t -> ctx.error("Could not send test DM, make sure you allow DMs from this guild!"))
                                    .thenReturn(pings)
                              : Mono.just(pings))
                      .orElse(Mono.empty())
                      .doOnNext(pings -> pings.add(ping))
                      .flatMap($ -> ctx.reply("Added a new custom ping for the pattern: `" + pattern + "`"));
        } else if (ctx.hasFlag(FLAG_RM)) {
            return storage.get(ctx)
                    .map(data -> data.getOrDefault(authorId, Collections.emptyList())) // Try to remove by pattern
                    .filter(data -> data.removeIf(ping -> ping.getPattern().pattern().equals(ctx.getFlag(FLAG_RM))))
                    .map($ -> ctx.reply("Deleted ping(s)."))
                    .orElse( // If none were removed, try to remove by ID
                            storage.get(ctx)
                                   .map(data -> data.getOrDefault(authorId, Collections.emptyList()))
                                   .map(pings -> {
                                            int idx = Integer.parseInt(ctx.getFlag(FLAG_RM));
                                            if (idx < 0 || idx >= pings.size()) {
                                                return ctx.<Message>error("Ping index out of range!");
                                            }
                                            CustomPing removed = pings.remove(idx);
                                            return ctx.reply("Removed ping: " + removed.getPattern().pattern());
                                   })
                                   .orElse(Mono.empty())
                                   .onErrorResume(NumberFormatException.class, e -> ctx.error("Found no pings to delete!")));
        }
        return ctx.error("No action to perform.");
    }
    
    @Override
    public String getDescription(CommandContext ctx) {
        return "Use this command to create a custom ping, when any message is sent in this guild that matches the given regex, you will be notified via PM.";
    }

    @Override
    protected TypeToken<Map<Long, List<CustomPing>>> getDataType() {
        return new TypeToken<Map<Long, List<CustomPing>>>(){};
    }
}
