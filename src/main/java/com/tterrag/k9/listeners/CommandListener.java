package com.tterrag.k9.listeners;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.tterrag.k9.commands.CommandTrick;
import com.tterrag.k9.commands.api.CommandRegistrar;

import discord4j.core.event.EventDispatcher;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.util.Snowflake;
import reactor.core.publisher.Mono;

public enum CommandListener {

    INSTANCE;
    
    public static final String DEFAULT_PREFIX = "!";
	public static final String CMD_PATTERN = "%s(%s)?(\\S+)(?:\\s(.*))?$";

    public static LongFunction<String> prefixes = id -> DEFAULT_PREFIX;
    private static final Map<String, Pattern> patternCache = new HashMap<>();
    
    public void subscribe(EventDispatcher events) {
        events.on(MessageCreateEvent.class)
              .filterWhen(e -> e.getMessage().getAuthor().map(u -> !u.isBot()))
              .filter(e -> e.getMessage().getContent().isPresent())
              .flatMap(this::tryInvoke)
              .subscribe();
    }
    
    private Mono<Void> tryInvoke(MessageCreateEvent evt) {
        // Hardcoded check for "@K9 help" for a global help command
//        msg.getUserMentions().filterWhen(u -> K9.instance.getSelf().map(self -> self.getId().equals(u.getId()))).subscribe(user -> {
//            String content = msg.getContent().get().replaceAll("<@!?" + user.getId().asLong() + ">", "").trim();
//            if (content.toLowerCase(Locale.ROOT).matches("^help.*")) {
//                CommandRegistrar.INSTANCE.invokeCommand(msg, "help", content.substring(4).trim());
//                return;
//            }
//        });
        Snowflake guild = evt.getGuildId().orElse(null);
        String cmdPrefix = getPrefix(guild);
        String trickPrefix = CommandTrick.getTrickPrefix(guild);
        
        return Mono.just(patternCache.computeIfAbsent(cmdPrefix + trickPrefix, prefix -> Pattern.compile(String.format(CMD_PATTERN, Pattern.quote(cmdPrefix), Pattern.quote(trickPrefix)), Pattern.DOTALL)))
           .map(p -> p.matcher(evt.getMessage().getContent().get()))
           .filter(m -> m.matches())
           .flatMap(m -> {
               boolean expand = m.group(1) != null;
               return CommandRegistrar.INSTANCE.invokeCommand(evt, expand ? "trick" : m.group(2), expand ? m.group(2) + " " + m.group(3) : m.group(3));
           })
           .then();
    }
    
    public static String getPrefix(Optional<Snowflake> guild) {
        return getPrefix(guild.orElse(null));
    }
    
    public static String getPrefix(Snowflake guild) {
        return guild == null ? DEFAULT_PREFIX : prefixes.apply(guild.asLong());
    }
}
