package com.tterrag.k9.listeners;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tterrag.k9.util.BakedMessage;
import com.tterrag.k9.util.GuildStorage;
import com.tterrag.k9.util.Patterns;
import com.tterrag.k9.util.SaveHelper;

import discord4j.core.event.domain.message.MessageCreateEvent;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
public enum IncrementListener {
    
    INSTANCE;
    
    private static final SaveHelper<Map<String, Long>> saveHelper = new SaveHelper<>(new File("counts"), new Gson(), new HashMap<>());
    private static final GuildStorage<Map<String, Long>> counts = new GuildStorage<>(
            id -> saveHelper.fromJson(id.getLeft() + ".json", new TypeToken<Map<String, Long>>(){})
    );

    public Mono<MessageCreateEvent> onMessage(MessageCreateEvent event) {
        return Mono.justOrEmpty(event.getGuildId())
             .flatMap($ -> Mono.justOrEmpty(event.getMessage().getContent()))
             .map(Patterns.INCREMENT_DECREMENT::matcher)
             .filter(Matcher::matches)
             .flatMap(matcher -> {
                 String key = matcher.group(1);
                 if (key.length() > 128) {
                     return Mono.empty();
                 }
                 String action = matcher.group(2);
                 long incr = action.equals("++") ? 1 : action.equals("--") ? -1 : 0;
                 long current = counts.get(event.getMessage()).block().merge(key, incr, (a, b) -> a + b);
                 saveHelper.writeJson(event.getGuildId().get().asLong() + ".json", counts.get(event.getGuildId().get()));
                 return event.getMessage().getChannel()
                         .flatMap(chan -> new BakedMessage().withContent(key + " == " + current).send(chan));
             })
             .doOnError(t -> log.error("Exception processing increment:", t))
             .onErrorResume(e -> event.getMessage().getChannel().flatMap(c -> c.createMessage("Exception processing increment: " + e.toString())))
             .onErrorResume($ -> Mono.empty()) // Ignore errors from posting errors
             .thenReturn(event);
    }
}
