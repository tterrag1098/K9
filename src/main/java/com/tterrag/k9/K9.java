package com.tterrag.k9;

import java.io.File;
import java.io.FilePermission;
import java.io.IOException;
import java.nio.file.Paths;
import java.security.AccessControlException;
import java.security.AccessController;
import java.util.Scanner;
import java.util.concurrent.ThreadPoolExecutor;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.tterrag.k9.commands.api.CommandRegistrar;
import com.tterrag.k9.irc.IRC;
import com.tterrag.k9.listeners.CommandListener;
import com.tterrag.k9.listeners.IncrementListener;
import com.tterrag.k9.mcp.DataDownloader;
import com.tterrag.k9.util.NonNull;
import com.tterrag.k9.util.PaginatedMessageFactory;
import com.tterrag.k9.util.Threads;

import lombok.extern.slf4j.Slf4j;
import sx.blah.discord.api.ClientBuilder;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.api.events.EventDispatcher;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.ReadyEvent;

@Slf4j
public class K9 {
    
    public static IDiscordClient instance;
    
    private static class Arguments {
        @Parameter(names = { "-a", "--auth" }, description = "The Discord app key to authenticate with.", required = true)
        private String authKey;
        
        @Parameter(names = { "--irc" }, hidden = true)
        private String ircPassword;
    }
    
    public static void main(String[] argv) {
        try {
            AccessController.checkPermission(new FilePermission(".", "read"));
        } catch (AccessControlException e) {
            throw new RuntimeException("Invalid policy settings!", e);
        }
        
        Arguments args = new Arguments();
        JCommander.newBuilder().addObject(args).build().parse(argv);

        instance = new ClientBuilder()
                .withToken(args.authKey)
                .withEventBackpressureHandler(new EventDispatcher.CallerRunsPolicy() {
                    @Override
                    public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
                        super.rejectedExecution(r, executor);
                        log.error("Execution buffer overflow:", new RuntimeException());
                    }
                }).login();

        instance.getDispatcher().registerListener(new K9());
        instance.getDispatcher().registerListener(CommandListener.INSTANCE);

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
            CommandRegistrar.INSTANCE.onShutdown();
        }));
        
        consoleThread.start();

        if(args.ircPassword != null) {
            new IRC(args.ircPassword);
        }
    }
    
    @EventSubscriber
    public void onReady(ReadyEvent event) {
        log.debug("Bot connected, starting up...");

        DataDownloader.INSTANCE.start();

        instance.getDispatcher().registerListener(PaginatedMessageFactory.INSTANCE);
        instance.getDispatcher().registerListener(IncrementListener.INSTANCE);

        CommandRegistrar.INSTANCE.slurpCommands();
        CommandRegistrar.INSTANCE.complete();
        
        // Change playing text to global help command
        K9.instance.changePlayingText("@" + K9.instance.getOurUser().getName() + " help");
    }

    public static @NonNull String getVersion() {
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
