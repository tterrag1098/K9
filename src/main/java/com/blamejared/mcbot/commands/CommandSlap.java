package com.blamejared.mcbot.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.blamejared.mcbot.commands.api.Command;
import com.blamejared.mcbot.commands.api.CommandException;
import com.blamejared.mcbot.commands.api.CommandPersisted;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.gson.reflect.TypeToken;

import sx.blah.discord.handle.obj.IMessage;

@Command
public class CommandSlap extends CommandPersisted<List<String>> {
	
	private List<String> options = Lists.newArrayList();
    private Random rand = new Random();

    public CommandSlap() {
        super("slap", false, ArrayList::new);
        options.add("with a large trout!");
        options.add("with a big bat!");
        options.add("with a frying pan!");
        options.add("like a little bitch!");
    }
    
    @Override
    public TypeToken<List<String>> getDataType() {
        return new TypeToken<List<String>>(){};
    }
    
    @Override
    public void process(IMessage message, List<String> flags, List<String> args) throws CommandException {
        if(args.size() < 1) {
            throw new CommandException("Not enough arguments.");
        }
        
        if (flags.contains("add")) {
        	storage.get(message.getGuild()).add(Joiner.on(' ').join(args));
        	message.getChannel().sendMessage("Added new slap suffix.");
        	return;
        } 
        if (flags.contains("ls")) {
        }
        StringBuilder builder = new StringBuilder(message.getAuthor().getName());
        List<String> suffixes = Lists.newArrayList(options);
        suffixes.addAll(storage.get(message.getGuild()));
        
        builder.append(" slapped ").append(Joiner.on(' ').join(args)).append(" " + suffixes.get(rand.nextInt(suffixes.size())));
        message.getChannel().sendMessage(escapeMentions(message.getGuild(), builder.toString()));
    }
    
    public String getUsage() {
        return "<user>";
    }
}
