package com.blamejared.mcbot.listeners;

import com.blamejared.mcbot.commands.api.CommandRegistrar;

import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.guild.channel.message.*;
import sx.blah.discord.handle.obj.IMessage;

public class ChannelListener {
	
	public static final String PREFIX_CHAR = "!";

    @EventSubscriber
    public void onMessageRecieved(MessageReceivedEvent event) {
        IMessage message = event.getMessage();
        String text = message.getFormattedContent();
        
        if (text.startsWith(PREFIX_CHAR)) {
        	CommandRegistrar.INSTANCE.invokeCommand(message);
        }
    }
    
    @EventSubscriber
    public void onMessageEdited(MessageUpdateEvent event){
        IMessage message = event.getNewMessage();
        String text = message.getFormattedContent();
        if (text.startsWith(PREFIX_CHAR)) {
            CommandRegistrar.INSTANCE.invokeCommand(message);
        }
    }
}
