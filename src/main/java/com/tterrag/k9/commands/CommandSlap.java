package com.tterrag.k9.commands;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

import com.google.common.collect.Lists;
import com.google.gson.reflect.TypeToken;
import com.tterrag.k9.commands.api.Argument;
import com.tterrag.k9.commands.api.Command;
import com.tterrag.k9.commands.api.CommandContext;
import com.tterrag.k9.commands.api.CommandPersisted;
import com.tterrag.k9.commands.api.Flag;
import com.tterrag.k9.util.ListMessageBuilder;
import com.tterrag.k9.util.Monos;
import com.tterrag.k9.util.Requirements;
import com.tterrag.k9.util.Requirements.RequiredType;
import com.tterrag.k9.util.annotation.Nullable;

import discord4j.common.util.Snowflake;
import discord4j.rest.util.Permission;
import reactor.core.publisher.Mono;

@Command
public class CommandSlap extends CommandPersisted<CopyOnWriteArrayList<String>> {
    
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
    
    private static final CopyOnWriteArrayList<String> DEFAULTS = Lists.newCopyOnWriteArrayList(Arrays.asList("with a large trout!", "with a big bat!", "with a frying pan!"));

    private final Random rand = new Random();

    public CommandSlap() {
        super("slap", false, () -> Lists.newCopyOnWriteArrayList(DEFAULTS));
    }
    
    @Override
    public TypeToken<CopyOnWriteArrayList<String>> getDataType() {
        return new TypeToken<CopyOnWriteArrayList<String>>(){};
    }
    
    @Override
    public Mono<?> process(CommandContext ctx) {
        if (ctx.hasFlag(FLAG_LS)) {
            return storage.get(ctx)
                    .map(data -> ctx.getChannel().flatMap(channel -> new ListMessageBuilder<String>("custom slap suffixes")
                            .addObjects(data)
                            .objectsPerPage(PER_PAGE)
                            .build(channel, ctx.getMessage())
                            .send()))
                    .orElse(ctx.error("No custom slap suffixes in DMs"));
        }

        if (ctx.hasFlag(FLAG_ADD)) {
            return ADD_PERMS.matches(ctx)
                    .filter(b -> b)
                    .switchIfEmpty(ctx.error("You do not have permission to add slaps!"))
                    .transform(Monos.mapOptional($ -> storage.get(ctx)))
                    .switchIfEmpty(ctx.error("Cannot add slap suffixes in DMs."))
                    .doOnNext(list -> list.add(ctx.getFlag(FLAG_ADD)))
                    .flatMap($ -> ctx.reply("Added new slap suffix."));
        }
        if (ctx.hasFlag(FLAG_REMOVE)) {
            return ADD_PERMS.matches(ctx)
                    .filter(b -> b)
                    .switchIfEmpty(ctx.error("You do not have permission to remove slaps!"))
                    .map($ -> Integer.parseInt(ctx.getFlag(FLAG_REMOVE)) - 1)
                    .onErrorResume(NumberFormatException.class, $ -> ctx.error("Not a valid number."))
                    .flatMap(idx -> Mono.justOrEmpty(storage.get(ctx))
                            .switchIfEmpty(ctx.error("Cannot remove slap suffixes in DMs."))
                            .filter(suffixes -> idx >= 0 && idx < suffixes.size())
                            .switchIfEmpty(ctx.error("Index out of range."))
                            .flatMap(suffixes -> ctx.reply("Removed slap suffix: \"" + suffixes.remove(idx.intValue()) + '"')));
        }

        String target = ctx.getArg(ARG_TARGET).trim();
        
        return Mono.zip(ctx.getClient().getSelf().flatMap(ctx::getDisplayName), 
                        ctx.getDisplayName())
                .flatMap(t -> {
                    boolean hasSelfMention = ctx.getMessage().getUserMentions().stream().anyMatch(u -> u.getId().equals(ctx.getClient().getSelfId()));
                    boolean nou = target.equalsIgnoreCase(t.getT1()) || hasSelfMention;
                    StringBuilder builder = new StringBuilder(nou ? target : t.getT2());
                    List<String> suffixes = storage.get(ctx).orElse(DEFAULTS);
                    builder.append(" slapped ").append(nou ? t.getT2() : target).append(" " + suffixes.get(rand.nextInt(suffixes.size())));
                    return ctx.reply(builder.toString());
                });
    }
    
    @Override
    public String getDescription(@Nullable Snowflake guildId) {
        return "For when someone just needs a good slap.";
    }
}
