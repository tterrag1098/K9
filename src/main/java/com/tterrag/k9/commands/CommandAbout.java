package com.tterrag.k9.commands;

import java.io.File;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;

import com.google.common.base.Charsets;
import com.google.gson.Gson;
import com.tterrag.k9.K9;
import com.tterrag.k9.commands.api.Command;
import com.tterrag.k9.commands.api.CommandBase;
import com.tterrag.k9.commands.api.CommandContext;
import com.tterrag.k9.listeners.CommandListener;

import discord4j.core.DiscordClient;
import reactor.core.publisher.Mono;

@Command
public class CommandAbout extends CommandBase {
    
    public CommandAbout() {
        super("about", false);
    }
    
    private final Mono<String> recentChanges = Mono.fromCallable(() -> {
        return IOUtils.readLines(K9.class.getResourceAsStream("/recent-changes.txt"), Charsets.UTF_8)
                .stream()
                .collect(Collectors.joining("\n"));
    }).cache();
    
    @Override
    public void onRegister(DiscordClient client) {
        super.onRegister(client);
        recentChanges.subscribe();
    }

    @Override
    public Mono<?> process(CommandContext ctx) {
        String ver = K9.getVersion();
        return ctx.getClient().getSelf()
            .flatMap(u -> ctx.reply(spec ->
                spec.setThumbnail(u.getAvatarUrl())
                    .setDescription("A bot for looking up Minecraft mappings, and other useful things.\nFor more info, try `" + CommandListener.getPrefix(ctx.getGuildId()) + "help`.")
                    .setTitle("K9 " + ver)
                    .setUrl("http://tterrag.com/k9")
                    .addField("Source", "https://github.com/tterrag1098/K9", false)
                    .addField("Recent Changes", recentChanges.block(), false)
            ));
    }

    @Override
    public String getDescription(CommandContext ctx) {
        return "Provides info about the current version of the bot.";
    }

}
