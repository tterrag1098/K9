package com.blamejared.mcbot.commands;

import com.blamejared.mcbot.commands.api.*;
import sx.blah.discord.handle.obj.IMessage;

import java.util.List;

@Command
public class CommandSlap extends CommandBase {
    
    public CommandSlap() {
        super("slap", false);
    }
    
    @Override
    public void process(IMessage message, List<String> flags, List<String> args) throws CommandException {
        if(args.size() < 1) {
            throw new CommandException("Not enough arguments.");
        }
        
        StringBuilder builder = new StringBuilder(message.getAuthor().getName());
        builder.append(" slapped ").append(args.get(0)).append(" with a large trout!");
        if(!validateMessage(builder.toString())){
            message.getChannel().sendMessage("Unable to send a message mentioning a user!");
            return;
        }
        message.getChannel().sendMessage(builder.toString());
    }
    
    public String getUsage() {
        return "!slap <user>";
    }
}
