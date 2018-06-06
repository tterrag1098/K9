package com.tterrag.k9.listeners;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import com.tterrag.k9.K9;
import com.tterrag.k9.commands.api.CommandRegistrar;
import com.tterrag.k9.util.GuildStorage;

import discord4j.core.event.EventDispatcher;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Message;

public enum CommandListener {

    INSTANCE;
    
    public static final String DEFAULT_PREFIX = "!";
	public static final String CMD_PATTERN = "(\\?)?(\\S+)(?:\\s(.*))?$";

    public static GuildStorage<String> prefixes = new GuildStorage<>(id -> DEFAULT_PREFIX);
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
           .map(CommandListener::getPrefix)
           .map(s -> patternCache.computeIfAbsent(s, prefix -> Pattern.compile(Pattern.quote(prefix) + CMD_PATTERN, Pattern.DOTALL)))
           .map(p -> p.matcher(msg.getContent().get()))
           .filter(m -> m.matches())
           .subscribe(m -> {
               boolean expand = m.group(1) != null;
               CommandRegistrar.INSTANCE.invokeCommand(msg, expand ? "trick" : m.group(2), expand ? m.group(2) + " " + m.group(3) : m.group(3));
           });
    }

    public static String getPrefix(Guild guild) {
        return guild == null ? DEFAULT_PREFIX : prefixes.get(guild);
    }
}
