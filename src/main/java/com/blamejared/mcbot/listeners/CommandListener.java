package com.blamejared.mcbot.listeners;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.blamejared.mcbot.commands.api.CommandRegistrar;
import com.blamejared.mcbot.util.GuildStorage;

import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageUpdateEvent;
import sx.blah.discord.handle.obj.IGuild;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.util.RequestBuffer;

public enum CommandListener {
    
    INSTANCE;

    public static final String DEFAULT_PREFIX = "!";
	public static final String CMD_PATTERN = "(\\w+)(?:[^\\S\\n](.*))?$";

    public static GuildStorage<String> prefixes = new GuildStorage<>(id -> DEFAULT_PREFIX);
    private static final Map<String, Pattern> patternCache = new HashMap<>();

    @EventSubscriber
    public void onMessageRecieved(MessageReceivedEvent event) {
        tryInvoke(event.getMessage());
    }
    
    @EventSubscriber
    public void onMessageEdited(MessageUpdateEvent event) {
        tryInvoke(event.getMessage());
    }
    
    private void tryInvoke(IMessage msg) {
        Pattern pattern = patternCache.computeIfAbsent(getPrefix(msg.getGuild()), prefix -> Pattern.compile(Pattern.quote(prefix) + CMD_PATTERN));
        Matcher matcher = pattern.matcher(msg.getContent());
        if (matcher.matches()) {
            if (CommandRegistrar.INSTANCE.getCommands().isEmpty()) {
                RequestBuffer.request(() -> msg.getChannel().sendMessage("The bot is still booting up, wait a few seconds and try again."));
            } else {
                CommandRegistrar.INSTANCE.invokeCommand(msg, matcher.group(1), matcher.group(2));
            }
        }
    }

    public static String getPrefix(IGuild guild) {
        return guild == null ? DEFAULT_PREFIX : prefixes.get(guild);
    }
}
