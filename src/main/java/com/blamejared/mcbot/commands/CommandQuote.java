package com.blamejared.mcbot.commands;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

import com.blamejared.mcbot.commands.api.Command;
import com.blamejared.mcbot.commands.api.CommandException;
import com.blamejared.mcbot.commands.api.CommandPersisted;
import com.google.gson.reflect.TypeToken;

import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import sx.blah.discord.handle.obj.IMessage;

@Command
public class CommandQuote extends CommandPersisted<TIntObjectMap<String>> {
    
    public CommandQuote() {
        super("quote", false, TIntObjectHashMap::new);
//        quotes.put(id++, "But noone cares - HellFirePVP");
//        quotes.put(id++, "CRAFTTWEAKER I MEANT CRAFTTWEAKER - Drullkus");
//        quotes.put(id++, "oh yeah im dumb - Kit");
//        quotes.put(id++, "i call zenscripts \"mt scripts\" - Kit");
//        quotes.put(id++, "yes - Shadows");
    }
    
    @Override
    protected TypeToken<TIntObjectMap<String>> getDataType() {
        return new TypeToken<TIntObjectMap<String>>(){};
    }

    Random rand = new Random();
    
    @Override
    public void process(IMessage message, List<String> flags, List<String> args) throws CommandException {
        for(String flag : flags) {
            if(flag.equalsIgnoreCase("ls")) {
                StringBuilder builder = new StringBuilder();
                builder.append("List of quotes:\n");
                storage.get(message).forEachEntry((k, v) -> {
                    builder.append(k).append(") ").append(v).append("\n");
                    return true;
                });
                message.getChannel().sendMessage(builder.toString());
                return;
            } else if(flag.equalsIgnoreCase("add")) {
                TIntObjectMap<String> quotes = storage.get(message);
                quotes.put(Arrays.stream(quotes.keys()).max().orElse(1), escapeMentions(message.getGuild(), message.getContent().substring("!quote -add ".length())));
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
            int[] keys = storage.get(message).keys();
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
