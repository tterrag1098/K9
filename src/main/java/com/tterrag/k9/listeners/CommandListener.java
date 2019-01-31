package com.tterrag.k9.listeners;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import java.util.function.LongFunction;
import java.util.regex.Matcher;

import com.tterrag.k9.K9;
import com.tterrag.k9.commands.CommandTrick;
import com.tterrag.k9.commands.api.CommandRegistrar;
import com.tterrag.k9.util.GuildStorage;

import discord4j.core.event.EventDispatcher;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Message;
import reactor.util.function.Tuples;

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
              .subscribe(e -> tryInvoke(e.getMessage()));
    }
    
    private void tryInvoke(Message msg) {
        // Hardcoded check for "@K9 help" for a global help command
//        msg.getUserMentions().filterWhen(u -> K9.instance.getSelf().map(self -> self.getId().equals(u.getId()))).subscribe(user -> {
//            String content = msg.getContent().get().replaceAll("<@!?" + user.getId().asLong() + ">", "").trim();
//            if (content.toLowerCase(Locale.ROOT).matches("^help.*")) {
//                CommandRegistrar.INSTANCE.invokeCommand(msg, "help", content.substring(4).trim());
//                return;
//            }
//        });
        msg.getGuild()
           .map(g -> Tuples.of(getPrefix(g), CommandTrick.getTrickPrefix(g)))
           .map(t -> patternCache.computeIfAbsent(t.getT1() + t.getT2(), prefix -> Pattern.compile(String.format(CMD_PATTERN, Pattern.quote(t.getT1()), Pattern.quote(t.getT2())), Pattern.DOTALL)))
           .map(p -> p.matcher(msg.getContent().get()))
           .filter(m -> m.matches())
           .subscribe(m -> {
               boolean expand = m.group(1) != null;
               CommandRegistrar.INSTANCE.invokeCommand(msg, expand ? "trick" : m.group(2), expand ? m.group(2) + " " + m.group(3) : m.group(3));
           });
    }

    public static String getPrefix(Guild guild) {
        return guild == null ? DEFAULT_PREFIX : prefixes.apply(guild.getId().asLong());
    }
}
