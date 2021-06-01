package com.tterrag.k9.listeners;

import java.time.Duration;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongFunction;
import java.util.regex.Pattern;

import org.apache.commons.lang3.tuple.Pair;

import com.tterrag.k9.commands.CommandTrick;
import com.tterrag.k9.commands.api.Argument;
import com.tterrag.k9.commands.api.CommandRegistrar;
import com.tterrag.k9.commands.api.Flag;
import com.tterrag.k9.commands.api.ICommand;

import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.discordjson.json.ApplicationCommandData;
import discord4j.discordjson.json.ApplicationCommandOptionData;
import discord4j.discordjson.json.ApplicationCommandRequest;
import discord4j.discordjson.json.ImmutableApplicationCommandRequest;
import discord4j.rest.service.ApplicationService;
import discord4j.rest.util.ApplicationCommandOptionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
@Slf4j
public class CommandListener {
    
    public static final String DEFAULT_PREFIX = "!";
	public static final String CMD_PATTERN = "%s(%s)?(\\S+)(?:\\s(.*))?$";

    public static LongFunction<String> prefixes = id -> DEFAULT_PREFIX;
    private static final Map<Pair<String, String>, Pattern> patternCache = new HashMap<>();
    
    private final CommandRegistrar commands;
    
    public Mono<MessageCreateEvent> onMessage(MessageCreateEvent event) {
        return this.tryInvoke(event)
                   .timeout(Duration.ofMinutes(1))
                   .doOnError(t -> log.error("Error dispatching commands:", t))
                   .onErrorResume(t -> event.getMessage().getChannel()
                           .flatMap(c -> c.createMessage(msg -> msg.setContent("Unexpected error occurred dispatching command. Please report this to your bot admin."))
                                   .onErrorResume($ -> Mono.empty())) // Don't let errors from reporting errors kill the listener
                           .then())
                   .doOnError(t -> log.error("Command listener errored!", t))
                   .thenReturn(event);
    }

    public Mono<Void> registerInteractions(long applicationId, ApplicationService applicationService) {
        return applicationService.getGlobalApplicationCommands(applicationId)
            .flatMap(c -> applicationService.deleteGlobalApplicationCommand(applicationId, Long.parseUnsignedLong(c.id())))
            .thenMany(Flux.fromIterable(commands.getCommands((Snowflake) null)))
            .flatMap(c -> updateInteraction(c, applicationId, applicationService))
            .then();
    }

    public Mono<ApplicationCommandData> updateInteraction(ICommand cmd, long applicationId, ApplicationService applicationService) {
        ImmutableApplicationCommandRequest.Builder command = ApplicationCommandRequest.builder()
                .name(cmd.getName())
                .description(trimToMax(cmd.getDescription(null), 100));
        
        cmd.getArguments().stream()
            .sorted(Comparator.<Argument<?>, Boolean>comparing(arg -> arg.required(Collections.emptyList())).reversed())
            .forEach(arg -> {
                command.addOption(ApplicationCommandOptionData.builder()
                        .name(arg.name())
                        .description(trimToMax(arg.description(), 100))
                        .type(ApplicationCommandOptionType.STRING.getValue())
                        .required(arg.required(Collections.emptyList()))
                        .build());
            });

        for (Flag f : cmd.getFlags()) {
            command.addOption(ApplicationCommandOptionData.builder()
                    .name(f.longFormName())
                    .description(trimToMax(f.description(), 100))
                    .type(f.canHaveValue() ? ApplicationCommandOptionType.STRING.getValue() : ApplicationCommandOptionType.BOOLEAN.getValue())
                    .required(false)
                    .build());
        }
        
        System.out.println(command.build());
        return applicationService.createGuildApplicationCommand(applicationId, 175740881389879296L, command.build());
    }

    private String trimToMax(String s, int max) {
        return s.length() > max ? s.substring(0, max - 4) + "..." : s;
    }

    private Mono<Void> tryInvoke(MessageCreateEvent evt) {
        // Hardcoded check for "@K9 help" for a global help command
        Mono<String> specialHelpCheck = Mono.just(evt.getMessage())
                .filterWhen(msg -> Flux.fromIterable(msg.getUserMentions()).next().map(u -> evt.getClient().getSelfId().equals(u.getId())))
                .map(msg -> msg.getContent().replaceAll("<@!?" + evt.getClient().getSelfId().asLong() + ">", "").trim())
                .filter(content -> content.toLowerCase(Locale.ROOT).matches("^help.*"))
                .flatMap(content -> commands.invokeCommand(evt, "help", content.substring(4).trim()).thenReturn(""));
                
        Snowflake guild = evt.getGuildId().orElse(null);
        String cmdPrefix = getPrefix(guild);
        String trickPrefix = CommandTrick.getTrickPrefix(guild);
        
        Mono<?> invokeCommand = Mono.just(patternCache.computeIfAbsent(Pair.of(cmdPrefix, trickPrefix), prefix -> Pattern.compile(String.format(CMD_PATTERN, Pattern.quote(cmdPrefix), Pattern.quote(trickPrefix)), Pattern.DOTALL)))
           .map(p -> p.matcher(evt.getMessage().getContent()))
           .filter(m -> m.matches())
           .flatMap(m -> {
               String args = m.group(3);
               return commands.invokeCommand(evt, m.group(2), args)
                              .switchIfEmpty(runTrickIfPresent(evt, trickPrefix, m.group(1), m.group(2), args));
           });
        
        return specialHelpCheck.switchIfEmpty(invokeCommand.thenReturn("")).then();
    }
    
    private Mono<ICommand> runTrickIfPresent(MessageCreateEvent evt, String prefix, String trigger, String name, String args) {
        if (prefix.isEmpty() || trigger != null) {
            Snowflake guild = evt.getGuildId().orElse(null);
            CommandTrick cmd = (CommandTrick) commands.findCommand(guild, "trick")
                    .orElseThrow(() -> new IllegalStateException("No trick command?"));
            if (cmd.getTrickData(guild, name) != null) {
                return commands.invokeCommand(evt, "trick", name + (args != null ? " " + args : ""));
            }
        }
        return Mono.empty();
    }
    
    public static String getPrefix(Optional<Snowflake> guild) {
        return getPrefix(guild.orElse(null));
    }
    
    public static String getPrefix(Snowflake guild) {
        return guild == null ? DEFAULT_PREFIX : prefixes.apply(guild.asLong());
    }
}
