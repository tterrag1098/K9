package com.blamejared.mcbot.listeners;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.blamejared.mcbot.commands.api.CommandRegistrar;

import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageUpdateEvent;
import sx.blah.discord.handle.obj.IMessage;

public class ChannelListener {
	
    public static final String PREFIX = "!";
	public static final Pattern COMMAND_PATTERN = Pattern.compile("!(\\w+)(?:[^\\S\\n](.*))?$");

    @EventSubscriber
    public void onMessageRecieved(MessageReceivedEvent event) {
        tryInvoke(event.getMessage());
    }
    
    @EventSubscriber
    public void onMessageEdited(MessageUpdateEvent event){
        tryInvoke(event.getMessage());
    }
    
    private void tryInvoke(IMessage msg) {
        Matcher matcher = COMMAND_PATTERN.matcher(msg.getContent());
        if (matcher.matches()) {
            CommandRegistrar.INSTANCE.invokeCommand(msg, matcher.group(1), matcher.group(2));
        }
    }
}
