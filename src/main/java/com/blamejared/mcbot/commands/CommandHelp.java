package com.blamejared.mcbot.commands;

import com.blamejared.mcbot.commands.api.Command;
import com.blamejared.mcbot.commands.api.CommandBase;
import com.blamejared.mcbot.commands.api.CommandContext;
import com.blamejared.mcbot.commands.api.CommandException;
import com.blamejared.mcbot.commands.api.CommandRegistrar;
import com.blamejared.mcbot.commands.api.ICommand;
import com.blamejared.mcbot.listeners.ChannelListener;

@Command
public class CommandHelp extends CommandBase {

    public CommandHelp() {
        super("help", false);
    }
    
    @Override
    public void process(CommandContext ctx) throws CommandException {
        if (ctx.argCount() < 1) {
            throw new CommandException("Not enough arguments.");
        }
        ICommand command = CommandRegistrar.INSTANCE.findCommand(ctx.getArg(0));
        ctx.reply(command == null ? "No such command." : ChannelListener.PREFIX_CHAR + command.getName() + " " + command.getUsage());
    }
    
    public String getUsage() {
        return "<command>";
    }
}
