package com.blamejared.mcbot.commands;

import com.blamejared.mcbot.commands.api.*;
import sx.blah.discord.handle.obj.IMessage;

import java.util.*;

@Command
public class CommandQuote extends CommandBase {
    
    private Map<Integer, String> quotes = new HashMap<>();
    
    public CommandQuote() {
        super("quote", false);
        int id = 0;
        quotes.put(id++, "But noone cares - HellFirePVP");
        quotes.put(id++, "CRAFTTWEAKER I MEANT CRAFTTWEAKER - Drullkus");
        quotes.put(id++, "oh yeah im dumb - Kit");
        quotes.put(id++, "i call zenscripts \"mt scripts\" - Kit");
        quotes.put(id++, "yes - Shadows");
    }
    
    
    Random rand = new Random();
    
    @Override
    public void process(IMessage message, List<String> flags, List<String> args) throws CommandException {
        for(String flag : flags) {
            if(flag.equalsIgnoreCase("ls")) {
                StringBuilder builder = new StringBuilder();
                quotes.forEach((key, val) -> {
                    builder.append(key).append(") ").append(val).append("\n");
                });
                message.getChannel().sendMessage(builder.toString());
                return;
            } else if(flag.equalsIgnoreCase("add")) {
                quotes.put(quotes.size(), message.getContent().substring("!quote -add ".length()));
                message.getChannel().sendMessage("Added quote!");
                return;
            } else if(flag.startsWith("remove=")) {
                int index = Integer.parseInt(flag.split("remove=")[1]);
                quotes.remove(index);
                message.getChannel().sendMessage("Removed quote!");
                return;
            }
        }
        if(args.isEmpty())
            message.getChannel().sendMessage(quotes.get(rand.nextInt(quotes.size())));
        else{
            message.getChannel().sendMessage(quotes.get(Integer.parseInt(args.get(0))));
        }
    }
    
    @Override
    public String getUsage() {
        return "[-add] <id>/<quote>";
    }
}
