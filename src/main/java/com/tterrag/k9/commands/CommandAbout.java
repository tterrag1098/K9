package com.tterrag.k9.commands;

import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;

import com.google.common.base.Charsets;
import com.tterrag.k9.K9;
import com.tterrag.k9.commands.api.Command;
import com.tterrag.k9.commands.api.CommandBase;
import com.tterrag.k9.commands.api.CommandContext;
import com.tterrag.k9.listeners.CommandListener;
import com.tterrag.k9.util.Monos;

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
    public void onRegister(K9 k9) {
        super.onRegister(k9);
        recentChanges.subscribe();
    }

    @Override
    public Mono<?> process(CommandContext ctx) {
        String ver = K9.getVersion();
        return ctx.getClient().getSelf()
            .transform(Monos.flatZipWith(recentChanges, (u, changes) -> ctx.reply(spec ->
                spec.setThumbnail(u.getAvatarUrl())
                    .setDescription("A bot for looking up Minecraft mappings, and other useful things.\nFor more info, try `" + CommandListener.getPrefix(ctx.getGuildId()) + "help`.")
                    .setTitle("K9 " + ver)
                    .setUrl("http://tterrag.com/k9")
                    .addField("Uptime", DurationFormatUtils.formatDurationWords((System.currentTimeMillis() - K9.getConnectionTimestamp()), true, false), false)
                    .addField("Source", "https://github.com/tterrag1098/K9", false)
                    .addField("Recent Changes", changes, false)
            )));
    }

    @Override
    public String getDescription(CommandContext ctx) {
        return "Provides info about the current version of the bot.";
    }

}
