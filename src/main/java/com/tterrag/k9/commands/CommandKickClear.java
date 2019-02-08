package com.tterrag.k9.commands;

import java.time.Duration;
import java.time.Instant;

import com.tterrag.k9.commands.api.Argument;
import com.tterrag.k9.commands.api.Command;
import com.tterrag.k9.commands.api.CommandBase;
import com.tterrag.k9.commands.api.CommandContext;
import com.tterrag.k9.commands.api.CommandContext.TypingStatus;
import com.tterrag.k9.commands.api.CommandException;
import com.tterrag.k9.util.Requirements;
import com.tterrag.k9.util.Requirements.RequiredType;
import com.tterrag.k9.util.Threads;

import discord4j.core.object.entity.GuildChannel;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.TextChannel;
import discord4j.core.object.entity.User;
import discord4j.core.object.util.Permission;
import discord4j.core.object.util.Snowflake;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Command
public class CommandKickClear extends CommandBase {
    
    // TODO allow for "varargs" arguments instead of hacking this with mentions
    @SuppressWarnings("unused")
    private static final Argument<String> ARG_MENTION = new SentenceArgument("mentions", "One or more users to kickclear", false);

    public CommandKickClear() {
        super("kickclear", false);
    }
    
    private volatile boolean waiting, confirmed;
    private volatile Thread blockedThread;
    
    @Override
    public Mono<?> process(CommandContext ctx) {
        if (!ctx.getGuildId().isPresent()) {
            return ctx.error("Kickclear is not available in DMs.");
        }
        
        if (ctx.getArgs().size() < 1) {
            if (waiting && !confirmed) {
                confirmed = true;
                blockedThread.interrupt();
                return Mono.empty();
            } else {
                return ctx.error("Invalid number of arguments.");
            }
        }
        
        GuildChannel channel = (GuildChannel) ctx.getChannel().block();
        Message confirmation = ctx.reply("This will kick and delete messages for the last 24 hrs! Say `!kickclear` again to confirm.").block();
        blockedThread = Thread.currentThread();
        waiting = true;
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            // ignore, confirmation has occurred
        }
        waiting = false;
        blockedThread = null;
        
        try {
            if (confirmed) {
                try (TypingStatus typing = ctx.setTyping()) {
                    for (User user : ctx.getMessage().getUserMentions().collectList().block()) {
                        channel.getGuild().block().kick(user.getId());
                        Flux<Snowflake> toDelete = ((TextChannel)channel).getMessagesAfter(Snowflake.of(Instant.now().minus(Duration.ofDays(1))))
                                .filter(m -> m.getAuthor().get().getId().equals(user.getId()))
                                .map(Message::getId);
                        ((TextChannel)channel).bulkDelete(toDelete);
                    }
                }
            }

            Mono<?> ret = ctx.getMessage().delete()
                    .then(confirmation.delete());
            if (confirmed) {
                Message msg = ctx.reply("Cleared and kicked user(s).").block();
                Threads.sleep(5000);
                ret = ret.then(msg.delete());
            }
            return ret;
        } finally {
            // Avoid state corruption by exception
            confirmed = false;
        }
    }
    
    @Override
    public Requirements requirements() {
        return Requirements.builder()
                .with(Permission.KICK_MEMBERS, RequiredType.ALL_OF)
                .with(Permission.MANAGE_MESSAGES, RequiredType.ALL_OF)
                .build();
    }

    @Override
    public String getDescription() {
        return "Kicks and clears recent history from the channel of the provided users.";
    }
}
