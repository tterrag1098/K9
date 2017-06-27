package com.blamejared.mcbot.commands;

import java.util.List;

import com.blamejared.mcbot.commands.api.Command;
import com.blamejared.mcbot.commands.api.CommandBase;
import com.blamejared.mcbot.commands.api.CommandException;
import com.blamejared.mcbot.commands.api.CommandRegistrar;
import com.blamejared.mcbot.commands.api.ICommand;
import com.blamejared.mcbot.listeners.ChannelListener;

import sx.blah.discord.handle.obj.IMessage;

@Command
public class CommandHelp extends CommandBase {

    public CommandHelp() {
        super("help", false);
    }
    
    @Override
    public void process(IMessage message, List<String> flags, List<String> args) throws CommandException {
        if (args.size() < 1) {
            throw new CommandException("Not enough arguments.");
        }
        ICommand command = CommandRegistrar.INSTANCE.findCommand(args.get(0));
        message.getChannel().sendMessage(command == null ? "No such command." : ChannelListener.PREFIX_CHAR + command.getName() + " " + command.getUsage());
    }
    
    public String getUsage() {
        return "<command>";
    }
}
