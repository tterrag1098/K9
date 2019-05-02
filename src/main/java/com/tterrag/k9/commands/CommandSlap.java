package com.tterrag.k9.commands;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Random;

import com.google.common.collect.Lists;
import com.google.gson.reflect.TypeToken;
import com.tterrag.k9.commands.api.Argument;
import com.tterrag.k9.commands.api.Command;
import com.tterrag.k9.commands.api.CommandContext;
import com.tterrag.k9.commands.api.CommandPersisted;
import com.tterrag.k9.commands.api.Flag;
import com.tterrag.k9.util.ListMessageBuilder;
import com.tterrag.k9.util.Requirements;
import com.tterrag.k9.util.Requirements.RequiredType;

import discord4j.core.object.util.Permission;
import reactor.core.publisher.Mono;

@Command
public class CommandSlap extends CommandPersisted<List<String>> {
    
    private static final Flag FLAG_ADD = new SimpleFlag('a', "add", "Adds a new slap.", true);
    private static final Flag FLAG_REMOVE = new SimpleFlag('r', "remove", "Removes a slap.", true);
    private static final Flag FLAG_LS = new SimpleFlag('l', "ls", "Lists all current slap strings.", false);
    
    private static final Argument<String> ARG_TARGET = new SentenceArgument("target", "The target of the slap.", true) {
        
        @Override
        public boolean required(Collection<Flag> flags) {
            return flags.isEmpty();
        }
    };
    
    private static final Requirements ADD_PERMS = Requirements.builder().with(Permission.MANAGE_MESSAGES, RequiredType.ALL_OF).build();
    
    private static final int PER_PAGE = 10;
    
    private static final List<String> DEFAULTS = Arrays.asList("with a large trout!", "with a big bat!", "with a frying pan!");

    private final Random rand = new Random();

    public CommandSlap() {
        super("slap", false, () -> Lists.newArrayList(DEFAULTS));
    }
    
    @Override
    public TypeToken<List<String>> getDataType() {
        return new TypeToken<List<String>>(){};
    }
    
    @Override
    public Mono<?> process(CommandContext ctx) {
        if (ctx.hasFlag(FLAG_LS)) {
            return new ListMessageBuilder<String>("custom slap suffixes").addObjects(storage.get(ctx).block()).objectsPerPage(PER_PAGE).build(ctx).send();
        }

        if (ctx.hasFlag(FLAG_ADD)) {
            if (!ADD_PERMS.matches(ctx).block()) {
                return ctx.error("You do not have permission to add slaps!");
            }
        	return storage.get(ctx)
        	        .doOnNext(list -> list.add(ctx.getFlag(FLAG_ADD)))
        	        .then(ctx.reply("Added new slap suffix."))
        	        .switchIfEmpty(ctx.error("Cannot add slap suffixes in DMs."));
        }
        if (ctx.hasFlag(FLAG_REMOVE)) {
            if (!ADD_PERMS.matches(ctx).block()) {
                return ctx.error("You do not have permission to remove slaps!");
            }
            int idx;
            try {
                idx = Integer.parseInt(ctx.getFlag(FLAG_REMOVE)) - 1;
            } catch (NumberFormatException e) {
                return ctx.error("Not a valid number.");
            }
            return storage.get(ctx)
                    .flatMap(suffixes -> {
                        if (idx < 0 || idx >= suffixes.size()) {
                            return ctx.error("Index out of range.");
                        }
                        String removed = suffixes.remove(idx);
                        return ctx.reply("Removed slap suffix: \"" + removed + '"');
                    })
                    .switchIfEmpty(ctx.error("Cannot remove slap suffixes in DMs."));
        }
        
        String target = ctx.getArg(ARG_TARGET).trim();
        String botname = ctx.getClient().getSelf().flatMap(ctx::getDisplayName).block();
        boolean nou = target.equalsIgnoreCase(botname) || ctx.getMessage().getUserMentions().any(u -> u.getId().equals(ctx.getClient().getSelfId().get())).block();
        String slapper = ctx.getDisplayName().block();
        StringBuilder builder = new StringBuilder(nou ? target : slapper);
        List<String> suffixes = storage.get(ctx).defaultIfEmpty(DEFAULTS).block();
        
        builder.append(" slapped ").append(nou ? slapper : target).append(" " + suffixes.get(rand.nextInt(suffixes.size())));
        return ctx.reply(builder.toString());
    }
    
    @Override
    public String getDescription() {
        return "For when someone just needs a good slap.";
    }
}
