package com.blamejared.mcbot;

import com.blamejared.mcbot.commands.api.CommandRegistrar;
import com.blamejared.mcbot.irc.MCBotIRC;
import com.blamejared.mcbot.listeners.ChannelListener;
import com.blamejared.mcbot.mcp.DataDownloader;
import org.eclipse.egit.github.core.*;
import org.eclipse.egit.github.core.client.GitHubClient;
import org.eclipse.egit.github.core.service.GistService;
import sx.blah.discord.api.*;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.guild.channel.message.*;
import sx.blah.discord.handle.obj.*;

import java.io.*;
import java.net.*;
import java.util.*;

public class MCBot {
    
    public static IDiscordClient instance;
    
    public static void main(String[] args) {
        instance = new ClientBuilder().withToken(args[0]).login();
        
        CommandRegistrar.INSTANCE.slurpCommands();
        CommandRegistrar.INSTANCE.complete();
        
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
    public void onMessageRecieved(MessageReceivedEvent event) throws IOException {
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
        if(event.getMessage().getFormattedContent().contains("https://pastebin.com/")) {
            boolean valid = false;
            StringBuilder urls = new StringBuilder("Pastebin -> Gist: \n");
            for(String s : event.getMessage().getFormattedContent().split(" ")) {
                if(s.startsWith("https://pastebin.com/") && !s.endsWith("/")) {
                    valid = true;
                    URL url = new URL("https://pastebin.com/raw/" + event.getMessage().getFormattedContent().split("https://pastebin.com/")[1].split(" ")[0]);
                    BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()));
                    StringBuilder content = new StringBuilder();
                    in.lines().forEachOrdered(line -> content.append(line).append("\n"));
                    GitHubClient client = new GitHubClient();
                    Gist gist = new Gist().setDescription("Pastebin Conversion");
                    GistFile file = new GistFile().setFilename("Converted.java").setContent(content.toString());
                    gist.setFiles(Collections.singletonMap("Pastebin conversion", file));
                    gist = new GistService(client).createGist(gist);
                    urls.append(gist.getHtmlUrl()).append("\n");
                }
            }
            if(valid)
                event.getChannel().sendMessage(urls.toString());
        }
    }
}
