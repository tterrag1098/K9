package com.blamejared.mcbot.commands;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import lombok.val;

import com.blamejared.mcbot.commands.api.Command;
import com.blamejared.mcbot.commands.api.CommandContext;
import com.blamejared.mcbot.commands.api.CommandException;
import com.blamejared.mcbot.commands.api.CommandPersisted;
import com.blamejared.mcbot.commands.api.Flag;
import com.blamejared.mcbot.util.BakedMessage;
import com.blamejared.mcbot.util.PaginatedMessageFactory;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.gson.reflect.TypeToken;

@Command
public class CommandQuote extends CommandPersisted<Map<Integer, String>> {
    
    private static final Flag FLAG_LS = new SimpleFlag("ls", false);
    private static final Flag FLAG_ADD = new SimpleFlag("add", false);
    private static final Flag FLAG_REMOVE = new SimpleFlag("remove", true);
    
    private static final int PER_PAGE = 10;
    
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
            int count = 0;
            StringBuilder builder = null;
            PaginatedMessageFactory.Builder messagebuilder = PaginatedMessageFactory.INSTANCE.builder(ctx.getChannel());
            for (val e : quotes.entrySet()) {
            	int page = (count / PER_PAGE) + 1;
            	if (count % PER_PAGE == 0) {
            		if (builder != null) {
            			messagebuilder.addPage(new BakedMessage().withContent(builder.toString()));
            		}
            		builder = new StringBuilder();
            		builder.append("List of quotes (Page " + page + "/" + (quotes.size() / PER_PAGE) + "):\n");
            	}
                builder.append(e.getKey()).append(") ").append(e.getValue()).append("\n");
                count++;
            }
            messagebuilder.addPage(new BakedMessage().withContent(builder.toString()));
            messagebuilder.setParent(ctx.getMessage()).build().send();
            return;
        } else if (ctx.hasFlag(FLAG_ADD)) {
            Map<Integer, String> quotes = storage.get(ctx.getMessage());
            String quote = Joiner.on(' ').join(ctx.getArgs());
            quotes.put(quotes.keySet().stream().mapToInt(Integer::intValue).max().orElse(0) + 1, ctx.sanitize(quote));
            ctx.reply("Added quote!");
            return;
        } else if (ctx.hasFlag(FLAG_REMOVE)) {
            int index = Integer.parseInt(ctx.getFlag(FLAG_REMOVE));
            storage.get(ctx.getMessage()).remove(index);
            ctx.reply("Removed quote!");
            return;
        }
        if(ctx.argCount() == 0) {
            Integer[] keys = storage.get(ctx.getMessage()).keySet().toArray(new Integer[0]);
            if (keys.length == 0) {
                throw new CommandException("There are no quotes!");
            }
            ctx.reply(storage.get(ctx).get(keys[rand.nextInt(keys.length)]));
        } else {
            int id;
            try {
                id = Integer.parseInt(ctx.getArg(0));
            } catch (NumberFormatException e) {
                throw new CommandException(ctx.getArg(0) + " is not a number!");
            }
            String quote = storage.get(ctx.getMessage()).get(id);
            if (quote != null) {
                ctx.reply(quote);
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
