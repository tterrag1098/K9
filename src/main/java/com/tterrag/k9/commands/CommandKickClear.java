package com.tterrag.k9.commands;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import com.tterrag.k9.commands.api.Argument;
import com.tterrag.k9.commands.api.Command;
import com.tterrag.k9.commands.api.CommandBase;
import com.tterrag.k9.commands.api.CommandContext;
import com.tterrag.k9.commands.api.CommandException;
import com.tterrag.k9.util.Requirements;
import com.tterrag.k9.util.Requirements.RequiredType;
import com.tterrag.k9.util.Threads;

import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.handle.obj.IUser;
import sx.blah.discord.handle.obj.Permissions;

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
    public void process(CommandContext ctx) throws CommandException {
        if (ctx.getArgs().size() < 1) {
            if (waiting && !confirmed) {
                confirmed = true;
                blockedThread.interrupt();
                return;
            } else {
                throw new CommandException("Invalid number of arguments.");
            }
        }
        
        IChannel channel = ctx.getChannel();
        IMessage confirmation = ctx.reply("This will kick and delete messages for the last 24 hrs! Say `!kickclear` again to confirm.");
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
                channel.setTypingStatus(true);
                for (IUser user : ctx.getMessage().getMentions()) {
                    channel.getGuild().kickUser(user);
                    List<IMessage> toDelete = channel.getMessageHistoryTo(LocalDateTime.now().minus(Duration.ofDays(1))).stream()
                            .filter(m -> m.getAuthor().getLongID() == user.getLongID())
                            .collect(Collectors.toList());
                    if (!toDelete.isEmpty()) {
                        channel.bulkDelete(toDelete);
                    }
                }
            }

            ctx.getMessage().delete();
            confirmation.delete();
            if (confirmed) {
                IMessage msg = ctx.reply("Cleared and kicked user(s).");
                Threads.sleep(5000);
                msg.delete();
            }
        } finally {
            // Avoid state corruption by exception
            confirmed = false;
        }
    }
    
    @Override
    public Requirements requirements() {
        return Requirements.builder()
                .with(Permissions.KICK, RequiredType.ALL_OF)
                .with(Permissions.MANAGE_MESSAGES, RequiredType.ALL_OF)
                .build();
    }

    @Override
    public String getDescription() {
        return "Kicks and clears recent history from the channel of the provided users.";
    }
}
