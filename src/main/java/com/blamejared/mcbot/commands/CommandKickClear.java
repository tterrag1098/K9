package com.blamejared.mcbot.commands;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import com.blamejared.mcbot.commands.api.Command;
import com.blamejared.mcbot.commands.api.CommandBase;
import com.blamejared.mcbot.commands.api.CommandException;
import com.blamejared.mcbot.util.Requirements;
import com.blamejared.mcbot.util.Requirements.RequiredType;
import com.blamejared.mcbot.util.Threads;

import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.handle.obj.IUser;
import sx.blah.discord.handle.obj.Permissions;

@Command
public class CommandKickClear extends CommandBase {

    public CommandKickClear() {
        super("kickclear", false);
    }
    
    private volatile boolean waiting, confirmed;
    private volatile Thread blockedThread;
    
    @Override
    public void process(IMessage message, List<String> flags, List<String> args) throws CommandException {
        if (args.size() < 1) {
            if (waiting && !confirmed) {
                confirmed = true;
                blockedThread.interrupt();
                return;
            } else {
                throw new CommandException("Invalid number of arguments.");
            }
        }
        
        IChannel channel = message.getChannel();
        IMessage confirmation = channel.sendMessage("This will kick and delete messages for the last 24 hrs! Say `!kickclear` again to confirm.");
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
                for (IUser user : message.getMentions()) {
                    channel.getGuild().kickUser(user);
                    List<IMessage> toDelete = channel.getMessageHistoryTo(LocalDateTime.now().minus(Duration.ofDays(1))).stream()
                            .filter(m -> m.getAuthor().getLongID() == user.getLongID())
                            .collect(Collectors.toList());
                    if (!toDelete.isEmpty()) {
                        channel.bulkDelete(toDelete);
                    }
                }
            }

            message.delete();
            confirmation.delete();
            if (confirmed) {
                IMessage msg = channel.sendMessage("Cleared and kicked user(s).");
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
    public String getUsage() {
        return "<user> [user2] [user3] ... - Kicks and clears recent history from the channel of the provided users.";
    }
}
