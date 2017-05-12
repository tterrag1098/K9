package com.blamejared.mcbot.commands;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Random;

import sx.blah.discord.handle.obj.IMessage;

import com.blamejared.mcbot.commands.api.Command;
import com.blamejared.mcbot.commands.api.CommandBase;
import com.blamejared.mcbot.commands.api.CommandException;
import com.google.common.base.Joiner;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

@Command
public class CommandSlap extends CommandBase {

	private Multimap<String, String> customSlaps = HashMultimap.create();
	
	private List<String> options = Lists.newArrayList();
    private Random rand = new Random();

    public CommandSlap() {
        super("slap", false);
        options.add("with a large trout!");
        options.add("with a big bat!");
        options.add("with a frying pan!");
        options.add("like a little bitch!");
    }

    @Override
    public void readJson(File dataFolder, Gson gson) {
    	super.readJson(dataFolder, gson);
    	Map<String, List<String>> slaps = saveHelper.fromJson("slaps.json", new TypeToken<Map<String, List<String>>>(){});
    	if (slaps != null) {
    		slaps.entrySet().forEach(e -> customSlaps.putAll(e.getKey(), e.getValue()));
    	}
    }
    
    @Override
    public void writeJson(File dataFolder, Gson gson) {
    	super.writeJson(dataFolder, gson);
    	saveHelper.writeJson("slaps.json", customSlaps.asMap(), new TypeToken<Map<String, Collection<String>>>(){});
    }
    
    @Override
    public void process(IMessage message, List<String> flags, List<String> args) throws CommandException {
        if(args.size() < 1) {
            throw new CommandException("Not enough arguments.");
        }
        
        if (flags.contains("add")) {
        	customSlaps.put(message.getGuild().getID(), Joiner.on(' ').join(args));
        	message.getChannel().sendMessage("Added new slap suffix.");
        	return;
        } 
        if (flags.contains("ls")) {
        }
        StringBuilder builder = new StringBuilder(message.getAuthor().getName());
        List<String> suffixes = Lists.newArrayList(options);
        suffixes.addAll(customSlaps.get(message.getGuild().getID()));
        
        builder.append(" slapped ").append(Joiner.on(' ').join(args)).append(" " + suffixes.get(rand.nextInt(suffixes.size())));
        message.getChannel().sendMessage(escapeMentions(message.getGuild(), builder.toString()));
    }
    
    public String getUsage() {
        return "<user>";
    }
}
