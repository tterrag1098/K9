package com.blamejared.mcbot;

import com.blamejared.mcbot.commands.*;
import sx.blah.discord.api.*;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.guild.channel.message.*;
import sx.blah.discord.handle.impl.events.guild.channel.message.reaction.ReactionAddEvent;
import sx.blah.discord.handle.impl.obj.Message;
import sx.blah.discord.handle.obj.*;

import java.util.*;

public class MCBot {
    
    public static IDiscordClient instance;
    
    
    public static Map<String, CommandBase> commands = new HashMap<>();
    
    public static void main(String[] args) {
        instance = new ClientBuilder().withToken(args[0]).login();
        instance.getDispatcher().registerListener(new MCBot());
        new CommandMCP();
    }
    
    public static IChannel getChannel(String name) {
        final IChannel[] channel = new IChannel[1];
        instance.getGuilds().forEach(guild -> guild.getChannels().forEach(chan -> {
            if(chan.getName().equalsIgnoreCase(name)) {
                channel[0] = chan;
            }
        }));
        return channel[0];
    }
    
    @EventSubscriber
    public void onMessageDeleted(MessageDeleteEvent event) {
        if(event.getChannel().getName().equalsIgnoreCase("bot-log")) {
            return;
        }
        getChannel("bot-log").sendMessage(event.getAuthor().getName() + " Deleted message: ```" + event.getMessage().getContent().replaceAll("```", "") + "```");
    }
    
    @EventSubscriber
    public void onMessageEdited(MessageUpdateEvent event) {
        if(event.getChannel().getName().equalsIgnoreCase("bot-log")) {
            return;
        }
        if(event.getAuthor().isBot()) {
            return;
        }
        getChannel("bot-log").sendMessage(event.getAuthor().getName() + " Edited message: ```" + event.getOldMessage().getContent().replaceAll("```", "") + "``` -> ```" + event.getNewMessage().getContent().replaceAll("```", "") + "```");
        
    }
    
    @EventSubscriber
    public void onMessageRecieved(MessageReceivedEvent event) {
        if(event.getMessage().getChannel().getName().equals("general-discussion")) {
            boolean isGif = false;
            if(event.getMessage().getContent().contains(".gif")) {
                isGif = true;
            }
            if(event.getMessage().getContent().contains("https://giphy.com")) {
                isGif = true;
            }
            if(event.getMessage().getContent().contains("https://tenor.co")) {
                isGif = true;
            }
            
            if(isGif) {
                event.getMessage().delete();
                event.getChannel().sendMessage("Sorry! GIFs are not allowed in this chat! Head to <#235949539138338816>");
            }
        }
        commands.forEach((key, val) -> {
            if(event.getMessage().getContent().startsWith(key))
                val.exectute(event.getMessage());
        });
    }
}
