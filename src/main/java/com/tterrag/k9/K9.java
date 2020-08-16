package com.tterrag.k9;

import java.io.File;
import java.io.FilePermission;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Paths;
import java.security.AccessControlException;
import java.security.AccessController;
import java.security.Policy;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
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
import com.tterrag.k9.logging.PrettifyMessageCreate;
import com.tterrag.k9.mappings.Yarn2McpService;
import com.tterrag.k9.mappings.mcp.McpDownloader;
import com.tterrag.k9.mappings.yarn.YarnDownloader;
import com.tterrag.k9.util.ConvertAdmins;
import com.tterrag.k9.util.PaginatedMessageFactory;
import com.tterrag.k9.util.Threads;

import discord4j.core.DiscordClient;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.event.domain.message.ReactionAddEvent;
import discord4j.core.object.presence.Activity;
import discord4j.core.object.presence.Presence;
import discord4j.core.object.util.Snowflake;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
public class K9 {
    
    private static class Arguments {
        @Parameter(names = { "-a", "--auth" }, description = "The Discord app key to authenticate with.", required = true)
        private String authKey;
        
        @Parameter(names = "--admins", description = "A list of user IDs that are admins", converter = ConvertAdmins.class)
        private List<Snowflake> admins = Collections.singletonList(Snowflake.of(140245257416736769L)); // tterrag
        
        @Parameter(names = { "--ircnick" }, hidden = true)
        private String ircNickname;
        
        @Parameter(names = { "--ircpw" }, hidden = true)
        private String ircPassword;
        
        @Parameter(names = "--ltapi", hidden = true) 
        private String loveTropicsApi;
        
        @Parameter(names = "--ltkey", hidden = true)
        private String loveTropicsKey;
        
        @Parameter(names = " --mindonation", hidden = true)
        private int minDonation = 25;
        
        @Parameter(names = "--yarn2mcpoutput", hidden = true)
        private String yarn2mcpOutput = null;
        
        @Parameter(names = "--yarn2mcpuser", hidden = true)
        private String yarn2mcpUser = null;
        
        @Parameter(names = "--yarn2mcppass", hidden = true)
        private String yarn2mcpPass = null;
    }

    public static void main(String[] argv) {
        String policyPath = "/policies/app.policy";
        URL policy = K9.class.getResource(policyPath);
        Objects.requireNonNull(policy, () -> "Could not find policy resource at " + policyPath);

        System.setProperty("java.security.policy", policy.toString());
        Policy.getPolicy().refresh();

        try {
            AccessController.checkPermission(new FilePermission(".", "read"));
        } catch (AccessControlException e) {
            throw new RuntimeException("Invalid policy settings!", e);
        }
        
        String protocol = K9.class.getResource("").getProtocol();
        if (!"jar".equals(protocol)) { // Only enable this in IDEs
            Hooks.onOperatorDebug();
        }
        
        Arguments args = new Arguments();
        JCommander.newBuilder().addObject(args).build().parse(argv);
        
        new K9(args).start().block();
    }
    
    private final Arguments args;
    @Getter
    private final DiscordClient client;
    @Getter
    private final CommandRegistrar commands;
    
    public K9(Arguments args) {
        this.args = args;
        this.client = new DiscordClientBuilder(args.authKey)
                .build();
        PrettifyMessageCreate.client = client;
        
        this.commands = new CommandRegistrar(this);
    }
    
    public Mono<Void> start() {
        Mono<Void> onReady = client.getEventDispatcher().on(ReadyEvent.class)
                .doOnNext(e -> {
                    log.info("Bot connected, starting up...");
                    log.info("Connected to {} guilds.", e.getGuilds().size());
                })
                .map(e -> e.getClient())
                .flatMap(c -> Mono.zip( // These actions could be slow, so run them in parallel
                    c.getGuilds() // Print all connected guilds
                        .collectList()
                        .doOnNext(guilds -> guilds.forEach(g -> log.info("\t" + g.getName()))),
                    c.getSelf() // Set initial presence
                        .map(u ->"@" + u.getUsername() + " help")
                        .flatMap(s -> c.updatePresence(Presence.online(Activity.playing(s))))
                ))
                .then();
        
        Mono<Void> onInitialReady = client.getEventDispatcher().on(ReadyEvent.class)
                .next()
                .then(Mono.fromRunnable(commands::complete))
                .then(YarnDownloader.INSTANCE.start())
                .then(McpDownloader.INSTANCE.start())
                .then(args.yarn2mcpOutput != null ? new Yarn2McpService(args.yarn2mcpOutput, args.yarn2mcpUser, args.yarn2mcpPass).start() : Mono.never());
        
        Mono<Void> reactionHandler = client.getEventDispatcher().on(ReactionAddEvent.class)
                .flatMap(evt -> PaginatedMessageFactory.INSTANCE.onReactAdd(evt)
                        .doOnError(t -> log.error("Error paging message", t))
                        .onErrorResume($ -> Mono.empty())
                        .thenReturn(evt))
                .then();
        
        final CommandListener commandListener = new CommandListener(commands);
                
        Mono<Void> messageHandler = client.getEventDispatcher().on(MessageCreateEvent.class)
                .filter(e -> e.getMessage().getContent().isPresent())
                .flatMap(IRC.INSTANCE::onMessage)
                .filter(e -> e.getMessage().getAuthor().map(u -> !u.isBot()).orElse(true))
                .flatMap(commandListener::onMessage)
                .flatMap(IncrementListener.INSTANCE::onMessage)
                .doOnNext(EnderIOListener.INSTANCE::onMessage)
                .then();
        
        // Make sure shutdown things are run, regardless of where shutdown came from
        // The above System.exit(0) will trigger this hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            commands.onShutdown();
            if (client.isConnected()) {
                client.logout().block();
            }
        }));
                
        // Handle "stop" and any future commands
        Mono<Void> consoleHandler = Mono.<Void>fromCallable(() -> {
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
        }).subscribeOn(Schedulers.newSingle("Console Listener", true));

        Mono<Void> ircHandler = Mono.never(); // Prevent immediate empty completion when IRC details are not provided
        if(args.ircNickname != null && args.ircPassword != null) {
            ircHandler = Mono.<Void>fromRunnable(() -> IRC.INSTANCE.connect(args.ircNickname, args.ircPassword))
                .publishOn(Schedulers.newSingle("IRC Thread"));
        }
        
        return Mono.fromRunnable(commands::slurpCommands)
            .then(Mono.zip(client.login(), onReady, onInitialReady, reactionHandler, messageHandler, consoleHandler, ircHandler)
                    .then()
                    .doOnTerminate(() -> log.error("Unexpected completion of main bot subscriber!")));
    }

    public static String getVersion() {
        String ver = K9.class.getPackage().getImplementationVersion();
        if (ver == null) {
            File head = Paths.get(".git", "HEAD").toFile();
            if (head.exists()) {
                try {
                    String refpath = Files.asCharSource(head, Charsets.UTF_8).readFirstLine().replace("ref: ", "");
                    File ref = head.toPath().getParent().resolve(refpath).toFile();
                    String hash = Files.asCharSource(ref, Charsets.UTF_8).readFirstLine();
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
    
    public boolean isAdmin(Snowflake id) {
        return args.admins.contains(id);
    }
}
