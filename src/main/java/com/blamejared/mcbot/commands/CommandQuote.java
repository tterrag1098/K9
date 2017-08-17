package com.blamejared.mcbot.commands;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import com.blamejared.mcbot.commands.api.Command;
import com.blamejared.mcbot.commands.api.CommandContext;
import com.blamejared.mcbot.commands.api.CommandException;
import com.blamejared.mcbot.commands.api.CommandPersisted;
import com.blamejared.mcbot.commands.api.Flag;
import com.google.common.collect.Lists;
import com.google.gson.reflect.TypeToken;

@Command
public class CommandQuote extends CommandPersisted<Map<Integer, String>> {
    
    private static final Flag FLAG_LS = new SimpleFlag("ls", false);
    private static final Flag FLAG_ADD = new SimpleFlag("add", false);
    private static final Flag FLAG_REMOVE = new SimpleFlag("remove", true);
    
    public CommandQuote() {
        super("quote", false, Lists.newArrayList(FLAG_LS, FLAG_ADD, FLAG_REMOVE), HashMap::new);
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
    public void process(CommandContext ctx) throws CommandException {
        if (ctx.hasFlag(FLAG_LS)) {
            Map<Integer, String> quotes = storage.get(ctx.getMessage());
            int page = ctx.argCount() < 1 ? 1 : Integer.valueOf(ctx.getArg(0));
            int min = (page - 1) * 20;
            if (page < 1 || min > quotes.keySet().stream().mapToInt(Integer::valueOf).max().orElse(0)) {
                throw new CommandException("Page out of range!");
            }
            int max = page * 20;
            StringBuilder builder = new StringBuilder();
            builder.append("List of quotes (Page " + page + "):\n");
            quotes.entrySet().stream().filter(e -> e.getKey() > min && e.getKey() <= max).forEach(e -> {
                builder.append(e.getKey()).append(") ").append(e.getValue()).append("\n");
            });
            ctx.getMessage().getChannel().sendMessage(builder.toString());
            return;
        } else if (ctx.hasFlag(FLAG_ADD)) {
            Map<Integer, String> quotes = storage.get(ctx.getMessage());
            quotes.put(quotes.keySet().stream().mapToInt(Integer::intValue).max().orElse(0) + 1, escapeMentions(ctx.getMessage().getGuild(), ctx.getMessage().getContent().substring("!quote -add ".length())));
            ctx.getMessage().getChannel().sendMessage("Added quote!");
            return;
        } else if (ctx.hasFlag(FLAG_REMOVE)) {
            int index = Integer.parseInt(ctx.getFlag(FLAG_REMOVE));
            storage.get(ctx.getMessage()).remove(index);
            ctx.getMessage().getChannel().sendMessage("Removed quote!");
            return;
        }
        if(ctx.argCount() == 0) {
            Integer[] keys = storage.get(ctx.getMessage()).keySet().toArray(new Integer[0]);
            if (keys.length == 0) {
                throw new CommandException("There are no quotes!");
            }
            ctx.getMessage().getChannel().sendMessage(storage.get(ctx.getMessage()).get(keys[rand.nextInt(keys.length)]));
        } else {
            int id;
            try {
                id = Integer.parseInt(ctx.getArg(0));
            } catch (NumberFormatException e) {
                throw new CommandException(ctx.getArg(0) + " is not a number!");
            }
            String quote = storage.get(ctx.getMessage()).get(id);
            if (quote != null) {
                ctx.getMessage().getChannel().sendMessage(quote);
            } else {
                throw new CommandException("No quote for ID " + ctx.getArg(0));
            }
        }
    }
    
    @Override
    public String getUsage() {
        return "[quote_number]";
    }
}
