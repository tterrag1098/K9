package com.tterrag.k9.listeners;

import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongFunction;
import java.util.regex.Pattern;

import com.tterrag.k9.commands.CommandTrick;
import com.tterrag.k9.commands.api.CommandRegistrar;

import discord4j.core.event.EventDispatcher;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.util.Snowflake;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
@Slf4j
public class CommandListener {
    
    public static final String DEFAULT_PREFIX = "!";
	public static final String CMD_PATTERN = "%s(%s)?(\\S+)(?:\\s(.*))?$";

    public static LongFunction<String> prefixes = id -> DEFAULT_PREFIX;
    private static final Map<String, Pattern> patternCache = new HashMap<>();
    
    private final CommandRegistrar commands;
    
    public void subscribe(EventDispatcher events) {
        events.on(MessageCreateEvent.class)
              .filter(e -> e.getMessage().getAuthor().map(u -> !u.isBot()).orElse(true))
              .filter(e -> e.getMessage().getContent().isPresent())
              .flatMap(e -> this.tryInvoke(e)
                      .timeout(Duration.ofMinutes(1))
                      .doOnError(t -> log.error("Error dispatching commands:", t))
                      .onErrorResume(t -> e.getMessage().getChannel()
                              .flatMap(c -> c.createMessage(msg -> msg.setContent("Unexpected error occurred dispatching command. Please report this to your bot admin.")))
                              .then()))
              .doOnComplete(() -> log.warn("Command listener completed!"))
              .doOnError(t -> log.error("Command listener errored!", t))
              .subscribe();
    }
    
    private Mono<Void> tryInvoke(MessageCreateEvent evt) {
        // Hardcoded check for "@K9 help" for a global help command
        Mono<String> specialHelpCheck = Mono.just(evt.getMessage())
                .filterWhen(msg -> msg.getUserMentions().next().map(u -> evt.getClient().getSelfId().get().equals(u.getId())))
                .map(msg -> msg.getContent().get().replaceAll("<@!?" + evt.getClient().getSelfId().get().asLong() + ">", "").trim())
                .filter(content -> content.toLowerCase(Locale.ROOT).matches("^help.*"))
                .flatMap(content -> commands.invokeCommand(evt, "help", content.substring(4).trim()).thenReturn(""));
                
        Snowflake guild = evt.getGuildId().orElse(null);
        String cmdPrefix = getPrefix(guild);
        String trickPrefix = CommandTrick.getTrickPrefix(guild);
        
        Mono<?> invokeCommand = Mono.just(patternCache.computeIfAbsent(cmdPrefix + trickPrefix, prefix -> Pattern.compile(String.format(CMD_PATTERN, Pattern.quote(cmdPrefix), Pattern.quote(trickPrefix)), Pattern.DOTALL)))
           .map(p -> p.matcher(evt.getMessage().getContent().get()))
           .filter(m -> m.matches())
           .flatMap(m -> {
               String args = m.group(3);
               return commands.invokeCommand(evt, m.group(2), args)
                              .switchIfEmpty(trickPrefix.isEmpty() || m.group(1) != null ? commands.invokeCommand(evt, "trick", m.group(2) + (args != null ? " " + args : "")) : Mono.empty());
           });
        
        return specialHelpCheck.switchIfEmpty(invokeCommand.thenReturn("")).then();
    }
    
    public static String getPrefix(Optional<Snowflake> guild) {
        return getPrefix(guild.orElse(null));
    }
    
    public static String getPrefix(Snowflake guild) {
        return guild == null ? DEFAULT_PREFIX : prefixes.apply(guild.asLong());
    }
}
