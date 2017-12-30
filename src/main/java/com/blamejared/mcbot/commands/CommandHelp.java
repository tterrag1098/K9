package com.blamejared.mcbot.commands;

import java.util.Collections;

import com.blamejared.mcbot.commands.api.Argument;
import com.blamejared.mcbot.commands.api.Command;
import com.blamejared.mcbot.commands.api.CommandBase;
import com.blamejared.mcbot.commands.api.CommandContext;
import com.blamejared.mcbot.commands.api.CommandException;
import com.google.common.collect.Lists;

@Command
public class CommandHelp extends CommandBase {
    
    private static final Argument<String> ARG_COMMAND = new WordArgument("command", "The command to get help on.", false);

    public CommandHelp() {
        super("help", false, Collections.emptyList(), Lists.newArrayList(ARG_COMMAND));
    }

    @Override
    public void process(CommandContext ctx) throws CommandException {
        ctx.reply("This instance of the bot has been disabled. If you would like permission to invite the new bot, contact tterrag. Or better yet, just run your own instance, the bot is open source! https://github.com/tterrag1098/MCBot");
        return;
    }

    public String getDescription() {
        return "Displays help for a given command.";
    }
}
