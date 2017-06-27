package com.blamejared.mcbot.commands;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.blamejared.mcbot.commands.api.Command;
import com.blamejared.mcbot.commands.api.CommandException;
import com.blamejared.mcbot.commands.api.CommandPersisted;
import com.google.gson.reflect.TypeToken;

import sx.blah.discord.handle.obj.IMessage;

@Command
public class CommandQuote extends CommandPersisted<Map<Integer, String>> {
    
    public CommandQuote() {
        super("quote", false, HashMap::new);
//        quotes.put(id++, "But noone cares - HellFirePVP");
//        quotes.put(id++, "CRAFTTWEAKER I MEANT CRAFTTWEAKER - Drullkus");
//        quotes.put(id++, "oh yeah im dumb - Kit");
//        quotes.put(id++, "i call zenscripts \"mt scripts\" - Kit");
//        quotes.put(id++, "yes - Shadows");
    }
    
    @Override
    protected TypeToken<Map<Integer, String>> getDataType() {
        return new TypeToken<Map<Integer, String>>(){};
    }

    Random rand = new Random();
    
    @Override
    public void process(IMessage message, List<String> flags, List<String> args) throws CommandException {
        for(String flag : flags) {
            if(flag.equalsIgnoreCase("ls")) {
                StringBuilder builder = new StringBuilder();
                builder.append("List of quotes:\n");
                storage.get(message).forEach((k, v) -> {
                    builder.append(k).append(") ").append(v).append("\n");
                });
                message.getChannel().sendMessage(builder.toString());
                return;
            } else if(flag.equalsIgnoreCase("add")) {
                Map<Integer, String> quotes = storage.get(message);
                quotes.put(quotes.keySet().stream().max(Integer::compare).orElse(0) + 1, escapeMentions(message.getGuild(), message.getContent().substring("!quote -add ".length())));
                message.getChannel().sendMessage("Added quote!");
                return;
            } else if(flag.startsWith("remove=")) {
                int index = Integer.parseInt(flag.split("remove=")[1]);
                storage.get(message).remove(index);
                message.getChannel().sendMessage("Removed quote!");
                return;
            }
        }
        if(args.isEmpty()) {
            Integer[] keys = storage.get(message).keySet().toArray(new Integer[0]);
            if (keys.length == 0) {
                throw new CommandException("There are no quotes!");
            }
            message.getChannel().sendMessage(storage.get(message).get(keys[rand.nextInt(keys.length)]));
        } else {
            int id;
            try {
                id = Integer.parseInt(args.get(0));
            } catch (NumberFormatException e) {
                throw new CommandException(args.get(0) + " is not a number!");
            }
            String quote = storage.get(message).get(id);
            if (quote != null) {
                message.getChannel().sendMessage(quote);
            } else {
                throw new CommandException("No quote for ID " + args.get(0));
            }
        }
    }
    
    @Override
    public String getUsage() {
        return "[-add] <id>/<quote>";
    }
}
