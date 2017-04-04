package com.blamejared.mcbot.listeners;

import com.blamejared.mcbot.commands.api.CommandRegistrar;

import com.blamejared.mcbot.zenscript.ZenScript;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.guild.channel.message.*;
import sx.blah.discord.handle.obj.IMessage;

public class ChannelListener {
	
	public static final String PREFIX_CHAR = "!";
    public static final String PREFIX_CHAR_ZEN = "?";
    
    
    @EventSubscriber
    public void onMessageRecieved(MessageReceivedEvent event) {
        IMessage message = event.getMessage();
        String text = message.getContent();
        
        if (text.startsWith(PREFIX_CHAR)) {
        	CommandRegistrar.INSTANCE.invokeCommand(message);
        }
        if(text.startsWith(PREFIX_CHAR_ZEN)) {
            ZenScript.zenCommands.forEach((key, val)->{
                if(text.startsWith(key)){
                    val.process(event.getGuild().getName(), event.getChannel().getName(), event.getAuthor().getName(), text);
                }
            });
            
        }
    }
    
    @EventSubscriber
    public void onMessageEdited(MessageUpdateEvent event){
        IMessage message = event.getNewMessage();
        String text = message.getContent();
        if (text.startsWith(PREFIX_CHAR)) {
            CommandRegistrar.INSTANCE.invokeCommand(message);
        }
    }
}
