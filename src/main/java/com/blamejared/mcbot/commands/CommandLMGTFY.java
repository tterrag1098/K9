package com.blamejared.mcbot.commands;

import com.blamejared.mcbot.commands.api.*;
import com.google.common.base.Joiner;

import sx.blah.discord.handle.obj.IMessage;

import java.util.List;

@Command
public class CommandLMGTFY extends CommandBase {

    public CommandLMGTFY() {
        super("lmgtfy", false);
    }
    
    @Override
    public void process(IMessage message, List<String> flags, List<String> args) throws CommandException {
        if (args.size() < 1) {
            throw new CommandException("Not enough arguments.");
        }
        int iie = flags.contains("ie") ? 1 : 0;
        StringBuilder arg = new StringBuilder("http://lmgtfy.com/?iie=").append(iie).append("&q=");
        arg.append(Joiner.on('+').join(args));
        message.getChannel().sendMessage(escapeMentions(arg.toString()));
    }
    
    public String getUsage() {
        return "<question>";
    }
}
