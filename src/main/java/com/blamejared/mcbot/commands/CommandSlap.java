package com.blamejared.mcbot.commands;

import com.blamejared.mcbot.commands.api.*;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

import sx.blah.discord.handle.obj.IMessage;

import java.util.*;

@Command
public class CommandSlap extends CommandBase {
    private List<String> options = Lists.newArrayList();
    private Random rand = new Random();
    public CommandSlap() {
        super("slap", false);
        options.add(" with a large trout!");
        options.add(" with a big bat!");
        options.add(" with a frying pan!");
        options.add(" like a little bitch!");
        options.add(" like you in yo bed");
    }
    
    @Override
    public void process(IMessage message, List<String> flags, List<String> args) throws CommandException {
        if(args.size() < 1) {
            throw new CommandException("Not enough arguments.");
        }
        
        StringBuilder builder = new StringBuilder(message.getAuthor().getName());
        builder.append(" slapped ").append(Joiner.on(' ').join(args)).append(options.get(rand.nextInt(options.size())));
        message.getChannel().sendMessage(escapeMentions(message.getGuild(), builder.toString()));
    }
    
    public String getUsage() {
        return "<user>";
    }
}
