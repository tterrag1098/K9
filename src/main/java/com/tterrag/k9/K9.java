package com.tterrag.k9;

import java.io.File;
import java.io.FilePermission;
import java.io.IOException;
import java.nio.file.Paths;
import java.security.AccessControlException;
import java.security.AccessController;
import java.util.Scanner;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.tterrag.k9.commands.api.CommandRegistrar;
import com.tterrag.k9.irc.IRC;
import com.tterrag.k9.listeners.CommandListener;
import com.tterrag.k9.listeners.EnderIOListener;
import com.tterrag.k9.listeners.IncrementListener;
import com.tterrag.k9.mappings.mcp.McpDownloader;
import com.tterrag.k9.mappings.yarn.YarnDownloader;
import com.tterrag.k9.util.annotation.NonNull;
import com.tterrag.k9.util.PaginatedMessageFactory;
import com.tterrag.k9.util.Threads;

import discord4j.core.DiscordClient;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.event.domain.message.ReactionAddEvent;
import discord4j.core.object.presence.Activity;
import discord4j.core.object.presence.Presence;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Hooks;

@Slf4j
public class K9 {
    
    private static class Arguments {
        @Parameter(names = { "-a", "--auth" }, description = "The Discord app key to authenticate with.", required = true)
        private String authKey;
        
        @Parameter(names = { "--ircnick" }, hidden = true)
        private String ircNickname;
        
        @Parameter(names = { "--ircpw" }, hidden = true)
        private String ircPassword;
        
        @Parameter(names = "--ltkey", hidden = true)
        private String loveTropicsKey;
        
        @Parameter(names = " --mindonation", hidden = true)
        private int minDonation = 25;
    }
    
    private static Arguments args;
    
    public static @NonNull CommandRegistrar commands = new CommandRegistrar(null);
    
    public static void main(String[] argv) {
        try {
            AccessController.checkPermission(new FilePermission(".", "read"));
        } catch (AccessControlException e) {
            throw new RuntimeException("Invalid policy settings!", e);
        }
        
        args = new Arguments();
        JCommander.newBuilder().addObject(args).build().parse(argv);
        
        Hooks.onOperatorDebug();

        DiscordClient client = new DiscordClientBuilder(args.authKey).build();
        
        commands = new CommandRegistrar(client);
        
        client.getEventDispatcher().on(ReadyEvent.class).subscribe(new K9()::onReady);
        new CommandListener(commands).subscribe(client.getEventDispatcher());

        client.getEventDispatcher().on(ReactionAddEvent.class).subscribe(PaginatedMessageFactory.INSTANCE::onReactAdd);
        
        client.getEventDispatcher().on(MessageCreateEvent.class)
                .doOnNext(IncrementListener.INSTANCE::onMessage)
                .doOnNext(EnderIOListener.INSTANCE::onMessage)
                .doOnNext(IRC.INSTANCE::onMessage)
                .subscribe();
                
        // Handle "stop" and any future commands
        Thread consoleThread = new Thread(() -> {
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
        });

        // Make sure shutdown things are run, regardless of where shutdown came from
        // The above System.exit(0) will trigger this hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            commands.onShutdown();
        }));
        
        consoleThread.start();

        if(args.ircNickname != null && args.ircPassword != null) {
            IRC.INSTANCE.connect(args.ircNickname, args.ircPassword);
        }
        
        client.login().block();
    }
    
    public void onReady(ReadyEvent event) {
        log.debug("Bot connected, starting up...");

        McpDownloader.INSTANCE.start();
        YarnDownloader.INSTANCE.start();
//        if (args.loveTropicsKey != null) {
//            instance.getDispatcher().registerListener(new LoveTropicsListener(args.loveTropicsKey, args.minDonation));
//        }

        commands.slurpCommands();
        commands.complete();
        
        // Change playing text to global help command
        event.getClient().getSelf()
                   .map(u -> "@" + u.getUsername() + " help")
                   .subscribe(s -> event.getClient().updatePresence(Presence.online(Activity.playing(s))));
    }

    public static String getVersion() {
        String ver = K9.class.getPackage().getImplementationVersion();
        if (ver == null) {
            File head = Paths.get(".git", "HEAD").toFile();
            if (head.exists()) {
                try {
                    String refpath = Files.readFirstLine(head, Charsets.UTF_8).replace("ref: ", "");
                    File ref = head.toPath().getParent().resolve(refpath).toFile();
                    String hash = Files.readFirstLine(ref, Charsets.UTF_8);
                    ver = "DEV " + hash.substring(0, 8);
                } catch (IOException e) {
                    log.error("Could not load version from git data: ", e);
                    ver = "DEV";
                }
            } else {
                ver = "DEV (no HEAD)";
            }
        }
        return ver;
    }
}
