package com.tterrag.k9.commands;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeoutException;

import org.apache.commons.io.IOUtils;

import com.google.common.base.Charsets;
import com.tterrag.k9.commands.api.Argument;
import com.tterrag.k9.commands.api.Command;
import com.tterrag.k9.commands.api.CommandBase;
import com.tterrag.k9.commands.api.CommandContext;
import com.tterrag.k9.commands.api.CommandContext.TypingStatus;
import com.tterrag.k9.commands.api.CommandException;
import com.tterrag.k9.commands.api.Flag;
import com.tterrag.k9.util.Requirements;
import com.tterrag.k9.util.Requirements.RequiredType;

import discord4j.core.object.entity.Message;
import discord4j.core.object.util.Permission;
import discord4j.core.object.util.Snowflake;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Command
public class CommandInfoChannel extends CommandBase {

    private static final Flag FLAG_REPLACE = new SimpleFlag('r', "replace", "Replace the current contents of the channel.", false);
    
    private static final Argument<String> ARG_URL = new WordArgument("url", "The url to load the content from", true);

    public CommandInfoChannel() {
        super("info", false);
    }

    @Override
    public Mono<?> process(CommandContext ctx) {
        try(TypingStatus typing = ctx.setTyping()) {
            URL url = new URL(ctx.getArg(ARG_URL));
            List<String> lines = IOUtils.readLines(new InputStreamReader(url.openConnection().getInputStream(), Charsets.UTF_8));
            if (ctx.hasFlag(FLAG_REPLACE)) {
                Flux<Message> history = ctx.getChannel().flatMapMany(c -> c.getMessagesBefore(Snowflake.of(Instant.now())));
                history.timeout(Duration.ofSeconds(30))
                       .flatMap(Message::delete)
                       .onErrorResume(TimeoutException.class, e -> ctx.reply("Sorry, the message history in this channel is too long, or otherwise took too long to load.").then())
                       .subscribe();
            }
            StringBuilder sb = new StringBuilder();
            String embed = null;
            for (String s : lines) {
                if (s.equals("=>")) {
                    final String msg = sb.toString();
                    if (embed != null) {
                        try (InputStream in = new URL(embed).openStream()) {
                            String filename = embed.substring(embed.lastIndexOf('/') + 1);
                            ctx.getChannel().flatMap(c -> c.createMessage(spec -> spec.setContent(msg).setFile(filename, in))).subscribe();
                        }
                    } else {
                        ctx.replyFinal(msg);
                    }
                    sb = new StringBuilder();
                    embed = null;
                } else if (s.matches("<<https?://\\S+>>")) {
                    embed = s.substring(2, s.length() - 2);
                } else {
                    sb.append(s + "\n");
                }
            }
            return ctx.getMessage().delete();
        } catch (IOException e) {
            return ctx.error(e);
        }
    }
    
    @Override
    public String getDescription() {
        return "Loads messages in a channel from a URL.";
    }
    
    @Override
    public Requirements requirements() {
        return Requirements.builder().with(Permission.ADMINISTRATOR, RequiredType.ALL_OF).build();
    }
}
