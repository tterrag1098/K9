package com.tterrag.k9.listeners;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.tterrag.k9.K9;
import com.tterrag.k9.commands.CommandTrick;
import com.tterrag.k9.commands.api.CommandRegistrar;
import com.tterrag.k9.util.GuildStorage;

import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.obj.IGuild;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.util.RequestBuffer;

public enum CommandListener {

    INSTANCE;
    
    public static final String DEFAULT_PREFIX = "!";
	public static final String CMD_PATTERN = "%s(%s)?(\\S+)(?:\\s(.*))?$";

    public static GuildStorage<String> prefixes = new GuildStorage<>(id -> DEFAULT_PREFIX);
    private static final Map<String, Pattern> patternCache = new HashMap<>();

    @EventSubscriber
    public void onMessageRecieved(MessageReceivedEvent event) {
        tryInvoke(event.getMessage());
    }
    
//    @EventSubscriber
//    public void onMessageEdited(MessageUpdateEvent event) {
//        if (event.getMessage() != null) {
//            tryInvoke(event.getMessage());
//        }
//    }
    
    private void tryInvoke(IMessage msg) {
        if (msg.getAuthor() == null || msg.getAuthor().isBot()) {
            return;
        }
        // Hardcoded check for "@K9 help" for a global help command
        if (msg.getMentions().contains(K9.instance.getOurUser())) {
            String content = msg.getContent().replaceAll("<@!?" + K9.instance.getOurUser().getLongID() + ">", "").trim();
            if (content.toLowerCase(Locale.ROOT).matches("^help.*")) {
                CommandRegistrar.INSTANCE.invokeCommand(msg, "help", content.substring(4).trim());
                return;
            }
        }
        final String prefix = getPrefix(msg.getGuild());
        final String trickPrefix = CommandTrick.getTrickPrefix(msg.getGuild());
        Pattern pattern = patternCache.computeIfAbsent(prefix + trickPrefix, $ -> Pattern.compile(String.format(CMD_PATTERN, Pattern.quote(prefix), Pattern.quote(trickPrefix)), Pattern.DOTALL));
        Matcher matcher = pattern.matcher(msg.getContent());
        if (matcher.matches()) {
            boolean expand = matcher.group(1) != null;
            CommandRegistrar.INSTANCE.invokeCommand(msg, expand ? "trick" : matcher.group(2), expand ? matcher.group(2) + " " + matcher.group(3) : matcher.group(3));
        }
    }

    public static String getPrefix(IGuild guild) {
        return guild == null ? DEFAULT_PREFIX : prefixes.get(guild);
    }
}
