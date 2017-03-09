package com.blamejared.mcbot;

import com.blamejared.mcbot.commands.api.CommandRegistrar;
import com.blamejared.mcbot.listeners.ChannelListener;
import com.blamejared.mcbot.mcp.DataDownloader;

import sx.blah.discord.api.ClientBuilder;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageDeleteEvent;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageUpdateEvent;
import sx.blah.discord.handle.obj.IChannel;

public class MCBot {
    
    public static IDiscordClient instance;
    
    public static void main(String[] args) {
        instance = new ClientBuilder().withToken(args[0]).login();

        CommandRegistrar.INSTANCE.slurpCommands();
        
        DataDownloader.INSTANCE.start();
        
        instance.getDispatcher().registerListener(new MCBot());
        instance.getDispatcher().registerListener(new ChannelListener());
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
    }
}
