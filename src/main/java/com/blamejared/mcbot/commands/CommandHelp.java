package com.blamejared.mcbot.commands;

import java.util.Collections;

import com.blamejared.mcbot.commands.api.Argument;
import com.blamejared.mcbot.commands.api.Command;
import com.blamejared.mcbot.commands.api.CommandBase;
import com.blamejared.mcbot.commands.api.CommandContext;
import com.blamejared.mcbot.commands.api.CommandException;
import com.blamejared.mcbot.commands.api.CommandRegistrar;
import com.blamejared.mcbot.commands.api.ICommand;
import com.blamejared.mcbot.listeners.ChannelListener;
import com.google.common.collect.Lists;

@Command
public class CommandHelp extends CommandBase {
    
    private static final Argument<String> ARG_COMMAND = new WordArgument("command", "The command to get help on.", true);

    public CommandHelp() {
        super("help", false, Collections.emptyList(), Lists.newArrayList(ARG_COMMAND));
    }
    
    @Override
    public void process(CommandContext ctx) throws CommandException {
        ICommand command = CommandRegistrar.INSTANCE.findCommand(ctx.getArg(ARG_COMMAND));
        ctx.reply(command == null ? "No such command." : ChannelListener.PREFIX + command.getName() + " " + command.getUsage());
    }
    
    public String getUsage() {
        return "<command>";
    }
}
