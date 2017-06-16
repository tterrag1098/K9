package com.blamejared.mcbot;

import com.blamejared.mcbot.commands.api.CommandRegistrar;
import com.blamejared.mcbot.irc.MCBotIRC;
import com.blamejared.mcbot.listeners.ChannelListener;
import com.blamejared.mcbot.mcp.DataDownloader;
import sx.blah.discord.api.*;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.guild.channel.message.*;
import sx.blah.discord.handle.obj.*;

public class MCBot {
    
    public static IDiscordClient instance;
    
    public static void main(String[] args) {
        instance = new ClientBuilder().withToken(args[0]).login();
        
        CommandRegistrar.INSTANCE.slurpCommands();
        
        DataDownloader.INSTANCE.start();
        
        instance.getDispatcher().registerListener(new MCBot());
        instance.getDispatcher().registerListener(new ChannelListener());
        if(args.length > 1)
            new MCBotIRC(args[1]);
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
    
    public static IChannel getChannel(IGuild guild, String name) {
        final IChannel[] channel = new IChannel[1];
        guild.getChannels().forEach(chan -> {
            if(chan.getName().equalsIgnoreCase(name)) {
                channel[0] = chan;
            }
        });
        return channel[0];
    }
    
    @EventSubscriber
    public void onMessageDeleted(MessageDeleteEvent event) {
        if(!instance.getOurUser().getName().equalsIgnoreCase(event.getAuthor().getName())) {
            IChannel botLog = getChannel(event.getGuild(), "bot-log");
            if(event.getChannel().getName().equalsIgnoreCase("bot-log")) {
                return;
            }
            if(botLog != null) {
                getChannel(event.getGuild(), "bot-log").sendMessage(event.getAuthor().getName() + " Deleted message : ```" + event.getMessage().getContent().replaceAll("```", "") + "``` from channel: " + event.getChannel().getName());
            }
        }
    }
    
    @EventSubscriber
    public void onMessageEdited(MessageUpdateEvent event) {
        if(!instance.getOurUser().getName().equalsIgnoreCase(event.getAuthor().getName())) {
            IChannel botLog = getChannel(event.getGuild(), "bot-log");
            if(event.getChannel().getName().equalsIgnoreCase("bot-log")) {
                return;
            }
            if(botLog != null) {
                getChannel(event.getGuild(), "bot-log").sendMessage(event.getAuthor().getName() + " Edited message: ```" + event.getOldMessage().getContent().replaceAll("```", "") + "``` -> ```" + event.getNewMessage().getContent().replaceAll("```", "") + "``` from channel: " + event.getChannel().getName());
            }
        }
    }
    
    @EventSubscriber
    public void onMessageRecieved(MessageReceivedEvent event) {
        if(event.getGuild().getName().equals("Modders Corner")) {
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
}
