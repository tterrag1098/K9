package com.blamejared.mcbot.commands;

import com.blamejared.mcbot.commands.api.*;
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
        StringBuilder arg = new StringBuilder("http://lmgtfy.com/?q=");
        args.forEach(ar -> arg.append(ar).append("+"));
        arg.deleteCharAt(arg.lastIndexOf("+"));
        message.getChannel().sendMessage(arg.toString());
    }
    
    public String getUsage() {
        return "!lmgtfy <question>";
    }
}
