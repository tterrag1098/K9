package com.blamejared.mcbot;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.egit.github.core.Gist;
import org.eclipse.egit.github.core.GistFile;
import org.eclipse.egit.github.core.client.GitHubClient;
import org.eclipse.egit.github.core.service.GistService;

import com.blamejared.mcbot.commands.api.CommandRegistrar;
import com.blamejared.mcbot.irc.MCBotIRC;
import com.blamejared.mcbot.listeners.ChannelListener;
import com.blamejared.mcbot.mcp.DataDownloader;
import com.blamejared.mcbot.util.Threads;

import sx.blah.discord.api.ClientBuilder;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageDeleteEvent;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageUpdateEvent;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IGuild;
import sx.blah.discord.util.EmbedBuilder;

public class MCBot {
    
    public static IDiscordClient instance;
    
    public static void main(String[] args) {
        instance = new ClientBuilder().withToken(args[0]).login();
        
        CommandRegistrar.INSTANCE.slurpCommands();
        CommandRegistrar.INSTANCE.complete();
        
        DataDownloader.INSTANCE.start();
        
        // Handle "stop" and any future commands
        Thread consoleThread = new Thread(new Runnable() {
            
            @Override
            public void run() {
                Scanner scan = new Scanner(System.in);
                while (true) {
                    while (scan.hasNextLine()) {
                        if (scan.nextLine().equals("stop")) {
                            scan.close();
                            System.exit(0);
                        }
                    }
                    Threads.sleep(100);
                }
            }
        });
        
        // Make sure shutdown things are run, regardless of where shutdown came from
        // The above System.exit(0) will trigger this hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            CommandRegistrar.INSTANCE.onShutdown();
        }));
        
        consoleThread.start();
        
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
    
    private static final Pattern PASTEBIN_URL = Pattern.compile("https?:\\/\\/pastebin\\.com\\/(?:raw\\/)?([A-Za-z0-9]+)\\/?");
    
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
        Matcher matcher = PASTEBIN_URL.matcher(event.getMessage().getFormattedContent());
        List<String> lines = new ArrayList<>();
        while (matcher.find()) {
            event.getChannel().setTypingStatus(true);
            StringBuilder urls = new StringBuilder();

            URL url = new URL("https://pastebin.com/raw/" + matcher.group(1));
            urls.append("[Pastebin Raw](").append(url.toString()).append(") | ");
            BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()));
            StringBuilder content = new StringBuilder();
            in.lines().forEachOrdered(line -> content.append(line).append("\n"));
            GitHubClient client = new GitHubClient();
            Gist gist = new Gist().setDescription("Pastebin Conversion");
            GistFile file = new GistFile().setFilename("Converted.java").setContent(content.toString());
            gist.setFiles(Collections.singletonMap("Pastebin conversion", file));
            gist = new GistService(client).createGist(gist);
            urls.append("[Gist](").append(gist.getHtmlUrl()).append(")");
            lines.add(urls.toString());
        }
        if (lines.size() > 0) {
            EmbedBuilder embed = new EmbedBuilder();
            if (lines.size() == 1) {
                embed.withDesc(lines.get(0));
            } else {
                for (int i = 0; i < lines.size(); i++) {
                    embed.withDesc("Link " + i + ": " + lines.get(i));
                }
            }
            event.getChannel().sendMessage(embed.build());
        }
        event.getChannel().setTypingStatus(false);
    }
}
