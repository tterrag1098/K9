package com.tterrag.k9.commands;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import org.apache.commons.lang3.time.DurationFormatUtils;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.reflect.TypeToken;
import com.tterrag.k9.commands.CommandQuote.Quote;
import com.tterrag.k9.commands.api.Argument;
import com.tterrag.k9.commands.api.Command;
import com.tterrag.k9.commands.api.CommandContext;
import com.tterrag.k9.commands.api.CommandPersisted;
import com.tterrag.k9.commands.api.Flag;
import com.tterrag.k9.util.EmbedCreator;
import com.tterrag.k9.util.ListMessageBuilder;
import com.tterrag.k9.util.NullHelper;
import com.tterrag.k9.util.PaginatedMessageFactory.PaginatedMessage;
import com.tterrag.k9.util.Requirements;
import com.tterrag.k9.util.Requirements.RequiredType;
import com.tterrag.k9.util.annotation.Nullable;

import discord4j.core.DiscordClient;
import discord4j.core.event.domain.message.ReactionAddEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.TextChannel;
import discord4j.core.object.entity.User;
import discord4j.core.object.reaction.ReactionEmoji;
import discord4j.core.object.util.Permission;
import discord4j.core.object.util.Snowflake;
import discord4j.core.spec.EmbedCreateSpec;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.val;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Command
public class CommandQuote extends CommandPersisted<Map<Integer, Quote>> {
    
    private interface BattleMessageSupplier {
        
        Consumer<EmbedCreateSpec> getMessage(long duration, long remaining);
    
    }
    
    private class BattleManager {

        private class BattleThread extends Thread {
            
            private final CommandContext ctx;
            
            AtomicLong time;
            
            AtomicInteger queued = new AtomicInteger(1);
            
            BattleThread(CommandContext ctx, long time) {
                this.ctx = ctx;
                this.time = new AtomicLong(time);
                this.setUncaughtExceptionHandler((thread, ex) -> log.error("Battle thread terminated unexpectedly", ex));
            }
            
            @Override
            public synchronized void start() {
                battles.put(ctx.getChannelId(), this);
                super.start();
            }
            
            @Override
            public void run() {
                                
                try {
                    while (queued.get() != 0) {
                        
                        if (queued.decrementAndGet() < 0) {
                            queued.set(-1);
                        }
                        
                        // Copy storage map so as to not alter it
                        Map<Integer, Quote> tempMap = Maps.newHashMap(storage.get(ctx).block());
                        int q1 = randomQuote(tempMap);
                        // Make sure the same quote isn't picked twice
                        tempMap.remove(q1);
                        int q2 = randomQuote(tempMap);
            
                        Quote quote1 = storage.get(ctx).block().get(q1);
                        Quote quote2 = storage.get(ctx).block().get(q2);
                        
                        Message result = runBattle(ctx, ONE, TWO, (duration, remaining) -> getBattleMessage(q1, q2, quote1, quote2, duration, remaining));
                        if (result == null) {
                            break; // Battle canceled
                        }
                           
                        long votes1 = result.getReactors(ONE).count().block();
                        long votes2 = result.getReactors(TWO).count().block();
                        
                        // If there are less than three votes, call it off
                        if (votes1 + votes2 - 2 < 3) {
                            ctx.replyFinal("That's not enough votes for me to commit murder, sorry.");
                            result.delete().subscribe();
                        } else if (votes1 == votes2) {
                            ctx.replyFinal("It's a tie, we're all losers today.");
                            result.delete().subscribe();
                        } else {
                            int winner = votes1 > votes2 ? q1 : q2;
                            int loser = winner == q1 ? q2 : q1;
                            Quote winnerQuote = winner == q1 ? quote1 : quote2;
                            winnerQuote.onWinBattle();
                            Quote loserQuote = winner == q1 ? quote2 : quote1;
                            
                            result.delete();
                            Message runoffResult = runBattle(ctx, KILL, SPARE, (duration, remaining) -> getRunoffMessage(loser, loserQuote, duration, remaining));
                            if (runoffResult == null) {
                                break; // Battle canceled;
                            }
                            
                            EmbedCreator.Builder results = EmbedCreator.builder()
                                    .field(CROWN + " Quote #" + winner + " is the winner, with " + (Math.max(votes1, votes2) - 1) + " votes! " + CROWN, winnerQuote.toString(), false);
                            votes1 = result.getReactors(KILL).count().block();
                            votes2 = result.getReactors(SPARE).count().block();
                            if (votes1 + votes2 - 2 <= 3 || votes1 <= votes2) {
                                loserQuote.onSpared();
                                results.field(SPARE + " Quote #" + loser + " has been spared! For now... " + SPARE, loserQuote.toString(), false);
                            } else {
                                storage.get(ctx).block().remove(loser);
                                results.field(SKULL + " Here lies quote #" + loser + ". May it rest in peace. " + SKULL, loserQuote.toString(), false);
                            }
                            runoffResult.delete().subscribe();
                            ctx.replyFinal(results.build());
                        }
                    }
                } finally {
                    battles.remove(ctx.getChannelId());
                }
            }
            
            private @Nullable Message runBattle(CommandContext ctx, ReactionEmoji choice1, ReactionEmoji choice2, BattleMessageSupplier msgSupplier) {
                
                final long time = this.time.get(); // Make sure this stays the same throughout this battle stage

                Message msg = ctx.reply(msgSupplier.getMessage(time, time)).block();
      
                final long sentTime = System.currentTimeMillis();
                final long endTime = sentTime + time;
                
                allBattles.add(msg);
                try {
                    msg.addReaction(choice1).then(msg.addReaction(choice2)).subscribe();
                    
                    // Wait at least 2 seconds before initial update
                    try {
                        Thread.sleep(Math.min(time, 2000));
                    } catch (InterruptedException e) {
                        return cancel(msg);
                    }
    
                    // Update remaining time every 5 seconds
                    long sysTime;
                    while ((sysTime = System.currentTimeMillis()) <= endTime) {
                        long remaining = endTime - sysTime;
                        Consumer<EmbedCreateSpec> e = msgSupplier.getMessage(time, remaining);
                        msg.edit(spec -> spec.setEmbed(e)).subscribe();
                        try {
                            // Update the time remaining at half, or 5 seconds, whichever is higher
                            Thread.sleep(Math.min(remaining, Math.max(remaining / 2L, 5000)));
                        } catch (InterruptedException ex) {
                            return cancel(msg);
                        }
                    }
                } finally {
                    allBattles.remove(msg);
                }
                return ctx.getChannel().ofType(TextChannel.class).flatMap(c -> c.getMessageById(msg.getId())).block();
            }
            
            private <T> @Nullable T cancel(Message msg) {
                msg.edit(spec -> spec.setContent("All battles canceled.")).subscribe();
                msg.removeAllReactions().subscribe();
                allBattles.remove(msg);
                return null;
            }
        }
        
        private final Map<Snowflake, BattleThread> battles = Maps.newConcurrentMap();
        private final Set<Message> allBattles = Sets.newConcurrentHashSet();

        private final ReactionEmoji ONE = ReactionEmoji.unicode("\u0031\u20E3"); // ASCII 1 + COMBINING ENCLOSING KEYCAP
        private final ReactionEmoji TWO = ReactionEmoji.unicode("\u0032\u20E3"); // ASCII 2 + COMBINING ENCLOSING KEYCAP

        private final ReactionEmoji KILL = ReactionEmoji.unicode("\u2620"); // SKULL AND CROSSBONES
        private final ReactionEmoji SPARE = ReactionEmoji.unicode("\uD83D\uDE07"); // SMILING FACE WITH HALO

        private final ReactionEmoji CROWN = ReactionEmoji.unicode("\uD83D\uDC51"); // CROWN
        private final ReactionEmoji SKULL = ReactionEmoji.unicode("\uD83D\uDC80"); // SKULL

        public void onReactAdd(ReactionAddEvent event) {
            ReactionEmoji emoji = event.getEmoji();
            Message msg = event.getMessage().block();
            if (msg != null && allBattles.contains(msg)) {
                if (!emoji.equals(ONE) && !emoji.equals(TWO) && !emoji.equals(KILL) && !emoji.equals(SPARE)) {
                    msg.removeReaction(emoji, event.getUserId()).subscribe();
                } else if (!event.getUserId().equals(event.getClient().getSelfId().get())) {
                    msg.getReactions().stream()
                            .filter(r -> !r.getEmoji().equals(emoji))
                            .filter(r -> msg.getReactors(r.getEmoji())
                                    .filter(u -> u.getId().equals(event.getUserId()))
                                    .hasElements()
                                    .block())
                            .forEach(r -> msg.removeReaction(r.getEmoji(), event.getUserId()).subscribe());
                }
            }
        }
        
        public boolean canStart(CommandContext ctx) {
            return !battles.containsKey(ctx.getChannelId());
        }
        
        private int randomQuote(Map<Integer, Quote> map) {
            int totalWeight = map.values().stream().mapToInt(Quote::getWeight).sum();
            int choice = rand.nextInt(totalWeight);
            for (val e : map.entrySet()) {
                if (choice < e.getValue().getWeight()) {
                    return e.getKey();
                }
                choice -= e.getValue().getWeight();
            }
            return -1;
        }
        
        private String formatDuration(long ms) {
            String fmt = ms >= TimeUnit.HOURS.toMillis(1) ? "H:mm:ss" : ms >= TimeUnit.MINUTES.toMillis(1) ? "m:ss" : "s's'";
            return DurationFormatUtils.formatDuration(ms, fmt);
        }
        
        private Consumer<EmbedCreateSpec> appendRemainingTime(EmbedCreator.Builder builder, long duration, long remaining) {
            return builder.footerText(
                        "This battle will last " + DurationFormatUtils.formatDurationWords(duration, true, true) + " | " +
                        "Remaining: " + formatDuration(remaining)
                    ).build();
        }
        
        private Consumer<EmbedCreateSpec> getBattleMessage(int q1, int q2, Quote quote1, Quote quote2, long duration, long remaining) {
            EmbedCreator.Builder builder = EmbedCreator.builder()
                    .title("QUOTE BATTLE")
                    .description("Vote for the quote you want to win!")
                    .field("Quote 1", "#" + q1 + ": " + quote1, false)
                    .field("Quote 2", "#" + q2 + ": " + quote2, false);
            return appendRemainingTime(builder, duration, remaining);
        }
        
        private Consumer<EmbedCreateSpec> getRunoffMessage(int q, Quote quote, long duration, long remaining) {
            EmbedCreator.Builder builder = EmbedCreator.builder()
                    .title("Kill or Spare?")
                    .description("Quote #" + q + " has lost the battle. Should it be spared a grisly death?\n"
                            + "Vote " + KILL + " to kill, or " + SPARE + " to spare!")
                    .field("Quote #" + q, quote.toString(), true);
            return appendRemainingTime(builder, duration, remaining);
        }
        
        private Mono<Long> getTime(CommandContext ctx) {
            if (ctx.hasFlag(FLAG_BATTLE_TIME)) {
                try {
                    return Mono.just(TimeUnit.SECONDS.toMillis(Long.parseLong(ctx.getFlag(FLAG_BATTLE_TIME))));
                } catch (NumberFormatException e) {
                    return ctx.error(e);
                }
            } else {
                return Mono.just(TimeUnit.MINUTES.toMillis(1));
            }
        }
        
        public Mono<Void> updateTime(CommandContext ctx) {
            BattleThread battle = battles.get(ctx.getChannelId());
            if (battle != null) {
                return getTime(ctx).doOnNext(battle.time::set).then();
            } else {
                return ctx.error("No battle(s) running in this channel!");
            }
        }
        
        public Mono<Void> battle(CommandContext ctx) {
            if (!battleManager.canStart(ctx)) {
                return ctx.error("Cannot start a battle, one already exists in this channel! To queue battles, use -s.");
            }
            if (storage.get(ctx).block().size() < 2) {
                return ctx.error("There must be at least two quotes to battle!");
            }
            return getTime(ctx).doOnNext(time -> new BattleThread(ctx, time).start()).then();
        }

        public Mono<Void> cancel(CommandContext ctx) {
            if (battles.containsKey(ctx.getChannelId())) {
                battles.get(ctx.getChannelId()).interrupt();
            } else {
                return ctx.error("There is no battle to cancel!");
            }
            return Mono.empty();
        }

        public Mono<Void> enqueueBattles(CommandContext ctx, int numBattles) {
            Mono<Void> battleResult = Mono.empty();
            if (!battles.containsKey(ctx.getChannelId())) {
                battleResult = battle(ctx);
                if (numBattles > 0) {
                    numBattles--;
                }
            }
            if (battles.containsKey(ctx.getChannelId())) {
                BattleThread battle = battles.get(ctx.getChannelId());
                if (numBattles == -1) {
                    battle.queued.set(-1);
                } else {
                    battle.queued.addAndGet(numBattles);
                }
                if (ctx.hasFlag(FLAG_BATTLE_TIME)) {
                    return battleResult.then(updateTime(ctx));
                } else {
                    return battleResult;
                }
            } else {
                return ctx.error("Could not start battle for unknown reason");
            }
        }
    }
    
    @RequiredArgsConstructor
    @EqualsAndHashCode
    @Getter
    static class Quote {
        
        private static final String QUOTE_FORMAT = "\"%s\" - %s";
        
        private final String quote, quotee;
        
        @Setter
        private long owner;
        @Setter
        private int weight = 1024;
        
        public Quote(String quote, String quotee, User owner) {
            this(quote, quotee);
            this.owner = owner.getId().asLong();
        }
        
        public void onWinBattle() {
            weight /= 2;
        }
        
        public void onSpared() {
            weight = (int) Math.min(Integer.MAX_VALUE, weight * 2L);
        }
        
        @Override
        public String toString() {
            return String.format(QUOTE_FORMAT, getQuote(), getQuotee());
        }
    }
    
    private static final Flag FLAG_LS = new SimpleFlag('l', "list", "Lists all current quotes.", true, "0");
    private static final Flag FLAG_ADD = new SimpleFlag('a', "add", "Adds a new quote.", true);
    private static final Flag FLAG_REMOVE = new SimpleFlag('r', "remove", "Removes a quote by its ID.", true);
    private static final Flag FLAG_BATTLE = new SimpleFlag('b', "battle", "Get ready to rrruuummmbbbllleee!", false);
    private static final Flag FLAG_BATTLE_TIME = new SimpleFlag('t', "time", "The amount of time (in seconds) the battle will last. Will update the time of the current queue.", true);
    private static final Flag FLAG_BATTLE_CANCEL = new SimpleFlag('x', "cancel", "Cancel the ongoing battle or battle series", false);
    private static final Flag FLAG_BATTLE_SERIES = new SimpleFlag('q', "queue", "Use in combination with -b, queues a number of battles to run in this channel. Value should be a number or \"infinite\".", true);
    private static final Flag FLAG_INFO = new SimpleFlag('i', "info", "Shows extra info about a quote.", false);
    private static final Flag FLAG_CREATOR = new SimpleFlag('c', "creator", "Used to update the creator for a quote, only usable by moderators.", true);
    
    private static final Argument<Integer> ARG_ID = new IntegerArgument("quote", "The id of the quote to display.", false);
    
    private static final int PER_PAGE = 10;
    
    private static final Requirements REMOVE_PERMS = Requirements.builder().with(Permission.MANAGE_MESSAGES, RequiredType.ALL_OF).build();
    
    private final BattleManager battleManager = new BattleManager();
    
    public CommandQuote() {
        super("quote", false, HashMap::new);
//        quotes.put(id++, "But noone cares - HellFirePVP");
//        quotes.put(id++, "CRAFTTWEAKER I MEANT CRAFTTWEAKER - Drullkus");
//        quotes.put(id++, "oh yeah im dumb - Kit");
//        quotes.put(id++, "i call zenscripts \"mt scripts\" - Kit");
//        quotes.put(id++, "yes - Shadows");
    }
    
    @Override
    protected TypeToken<Map<Integer, Quote>> getDataType() {
        return new TypeToken<Map<Integer, Quote>>(){};
    }
    
    @Override
    public void onRegister(DiscordClient client) {
        super.onRegister(client);
        client.getEventDispatcher().on(ReactionAddEvent.class).subscribe(battleManager::onReactAdd);
    }
    
    Random rand = new Random();

    @Override
    public Mono<?> process(CommandContext ctx) {
        if (!ctx.getGuildId().isPresent()) {
            return ctx.error("Quotes are not available in DMs.");
        }
        if (ctx.hasFlag(FLAG_LS)) {
            Map<Integer, Quote> quotes = storage.get(ctx.getMessage()).block();
            
            PaginatedMessage msg = new ListMessageBuilder<Entry<Integer, Quote>>("quotes")
                    .addObjects(quotes.entrySet())
                    .indexFunc((e, i) -> e.getKey())
                    .stringFunc(e -> e.getValue().toString())
                    .objectsPerPage(PER_PAGE)
                    .build(ctx);
            
            int pageTarget = 0;
            int maxPages = msg.size();
            try {
                String pageStr = ctx.getFlag(FLAG_LS);
                if (pageStr != null) {
                    pageTarget = Integer.parseInt(ctx.getFlag(FLAG_LS)) - 1;
                    if (pageTarget < 0 || pageTarget >= maxPages) {
                        return ctx.error("Page argument out of range!");
                    }
                }
            } catch (NumberFormatException e) {
                return ctx.error(ctx.getFlag(FLAG_LS) + " is not a valid number!");
            }

            msg.setPageNumber(pageTarget);
            return msg.send();
        } 
        if (ctx.hasFlag(FLAG_ADD)) {
            String quote = ctx.getFlag(FLAG_ADD);
            String author = "Anonymous";
            if (quote != null) {
                int idx = quote.lastIndexOf('-');
                if (idx > 0) {
                    author = quote.substring(idx + 1).trim();
                    quote = quote.substring(0, idx).trim();
                }
            }

            Map<Integer, Quote> quotes = storage.get(ctx.getMessage()).block();
            int id = quotes.keySet().stream().mapToInt(Integer::intValue).max().orElse(0) + 1;
            quotes.put(id, new Quote(ctx.sanitize(quote).block(), ctx.sanitize(author).block(), ctx.getAuthor().get()));
            return ctx.reply("Added quote #" + id + "!");
        } else if (ctx.hasFlag(FLAG_REMOVE)) {
            if (!REMOVE_PERMS.matches(ctx).block()) {
                return ctx.error("You do not have permission to remove quotes!");
            }
            int index = Integer.parseInt(ctx.getFlag(FLAG_REMOVE));
            Quote removed = storage.get(ctx.getMessage()).block().remove(index);
            if (removed != null) {
                return ctx.reply("Removed quote!");
            } else {
                return ctx.error("No quote for ID " + index);
            }
        }
        
        boolean canDoBattles = REMOVE_PERMS.matches(ctx).block();
        if (ctx.hasFlag(FLAG_BATTLE_CANCEL)) {
            if (!canDoBattles) {
                return ctx.error("You do not have permission to cancel battles!");
            }
            return battleManager.cancel(ctx).then(ctx.getMessage().delete());
        }
        
        if (ctx.hasFlag(FLAG_BATTLE)) {
            if (!canDoBattles) {
                return ctx.error("You do not have permission to start battles!");
            }
            if (ctx.hasFlag(FLAG_BATTLE_SERIES)) {
                int numBattles;
                String value = ctx.getFlag(FLAG_BATTLE_SERIES);
                try {
                    numBattles = "infinite".equals(value) ? -1 : Integer.parseInt(ctx.getFlag(FLAG_BATTLE_SERIES));
                } catch (NumberFormatException e) {
                    return ctx.error(e);
                }
                return battleManager.enqueueBattles(ctx, numBattles)
                        .then(ctx.reply("Queued " + value + " quote battles."));
            } else {
                return battleManager.battle(ctx);
            }
        }
        
        // Naked -t flag, just update the current battle/queue
        if (ctx.hasFlag(FLAG_BATTLE_TIME)) {
            return battleManager.updateTime(ctx).then(ctx.reply("Updated battle time for ongoing battle(s)."));
        }
        
        String quoteFmt = "#%d: %s";
        if(ctx.argCount() == 0) {
            Integer[] keys = storage.get(ctx.getMessage()).block().keySet().toArray(new Integer[0]);
            if (keys.length == 0) {
                return ctx.error("There are no quotes!");
            }
            int id = rand.nextInt(keys.length);
            return ctx.reply(String.format(quoteFmt, keys[id], storage.get(ctx).block().get(keys[id])));
        } else {
            int id = ctx.getArg(ARG_ID);
            Quote quote = storage.get(ctx.getMessage()).block().get(id);
            if (quote != null) {
                if (ctx.hasFlag(FLAG_INFO)) {
                    User owner = ctx.getClient().getUserById(Snowflake.of(quote.getOwner())).block();
                    EmbedCreator info = EmbedCreator.builder()
                            .title("Quote #" + id)
                            .field("Text", quote.getQuote(), true)
                            .field("Quotee", quote.getQuotee(), true)
                            .field("Creator", owner.getMention(), true)
                            .field("Battle Weight", "" + quote.getWeight(), true)
                            .build();
                    return ctx.reply(info);
                } else if (ctx.hasFlag(FLAG_CREATOR)) {
                    if (!REMOVE_PERMS.matches(ctx).block()) {
                        return ctx.error("You do not have permission to update quote creators.");
                    }
                    String creatorName = NullHelper.notnull(ctx.getFlag(FLAG_CREATOR), "CommandContext#getFlag");
                    User creator = null;
                    try {
                        creator = ctx.getClient().getUserById(Snowflake.of(Long.parseLong(creatorName))).block();
                    } catch (NumberFormatException e) {
                        if (!ctx.getMessage().getUserMentionIds().isEmpty()) {
                            creator = ctx.getMessage().getUserMentions()
                                         .filter(u -> creatorName.contains("" + u.getId().asLong()))
                                         .next()
                                         .block();
                        }
                    }
                    if (creator != null) {
                        quote.setOwner(creator.getId().asLong());
                        return ctx.reply("Updated creator for quote #" + id);
                    } else {
                        return ctx.error(creatorName + " is not a valid user!");
                    }
                } else {
                    return ctx.reply(String.format(quoteFmt, id, quote));
                }
            } else {
                return ctx.error("No quote for ID " + id);
            }
        }
    }
    
    @Override
    public String getDescription(CommandContext ctx) {
        return "A way to store and retrieve quotes.";
    }
}
