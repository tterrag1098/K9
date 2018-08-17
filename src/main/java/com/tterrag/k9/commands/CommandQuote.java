package com.tterrag.k9.commands;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

import org.apache.commons.lang3.time.DurationFormatUtils;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.reflect.TypeToken;
import com.tterrag.k9.K9;
import com.tterrag.k9.commands.CommandQuote.Quote;
import com.tterrag.k9.commands.api.Argument;
import com.tterrag.k9.commands.api.Command;
import com.tterrag.k9.commands.api.CommandContext;
import com.tterrag.k9.commands.api.CommandException;
import com.tterrag.k9.commands.api.CommandPersisted;
import com.tterrag.k9.commands.api.Flag;
import com.tterrag.k9.util.ListMessageBuilder;
import com.tterrag.k9.util.NullHelper;
import com.tterrag.k9.util.Nullable;
import com.tterrag.k9.util.PaginatedMessageFactory.PaginatedMessage;
import com.tterrag.k9.util.RequestHelper;
import com.tterrag.k9.util.Requirements;
import com.tterrag.k9.util.Requirements.RequiredType;
import com.tterrag.k9.util.Threads;
import com.vdurmont.emoji.Emoji;
import com.vdurmont.emoji.EmojiManager;

import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TObjectIntHashMap;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.val;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.api.internal.json.objects.EmbedObject;
import sx.blah.discord.handle.impl.events.guild.channel.message.reaction.ReactionAddEvent;
import sx.blah.discord.handle.impl.obj.ReactionEmoji;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.handle.obj.IUser;
import sx.blah.discord.handle.obj.Permissions;
import sx.blah.discord.util.EmbedBuilder;
import sx.blah.discord.util.RequestBuffer;
import sx.blah.discord.util.RequestBuilder;
import sx.blah.discord.util.RequestBuffer.IVoidRequest;

@Command
public class CommandQuote extends CommandPersisted<Map<Integer, Quote>> {
    
    private interface BattleMessageSupplier {
        
        EmbedObject getMessage(long duration, long remaining);
    }
    private class BattleManager {

        private class BattleThread extends Thread {
            
            private final CommandContext ctx;
            
            AtomicLong time;
            
            AtomicInteger queued = new AtomicInteger(1);
            
            BattleThread(CommandContext ctx, long time) {
                this.ctx = ctx;
                this.time = new AtomicLong(time);
            }
            
            @Override
            public synchronized void start() {
                battles.put(ctx.getChannel(), this);
                super.start();
            }
            
            @Override
            public void run() {
                                
                while (queued.get() != 0) {
                    
                    if (queued.decrementAndGet() < 0) {
                        queued.set(-1);
                    }
                    
                    // Copy storage map so as to not alter it
                    Map<Integer, Quote> tempMap = Maps.newHashMap(storage.get(ctx));
                    int q1 = randomQuote(tempMap);
                    // Make sure the same quote isn't picked twice
                    tempMap.remove(q1);
                    int q2 = randomQuote(tempMap);
        
                    Quote quote1 = storage.get(ctx).get(q1);
                    Quote quote2 = storage.get(ctx).get(q2);
                    
                    IMessage result = runBattle(ctx, ONE, TWO, (duration, remaining) -> getBattleMessage(q1, q2, quote1, quote2, duration, remaining));
                    if (result == null) {
                        break; // Battle canceled
                    }
                       
                    int votes1 = result.getReactionByEmoji(ONE).getCount();
                    int votes2 = result.getReactionByEmoji(TWO).getCount();
                    
                    // If there are less than three votes, call it off
                    if (votes1 + votes2 - 2 < 3) {
                        ctx.replyBuffered("That's not enough votes for me to commit murder, sorry.");
                        RequestBuffer.request(result::delete);
                    } else if (votes1 == votes2) {
                        ctx.replyBuffered("It's a tie, we're all losers today.");
                        RequestBuffer.request(result::delete);
                    } else {
                        int winner = votes1 > votes2 ? q1 : q2;
                        int loser = winner == q1 ? q2 : q1;
                        Quote winnerQuote = winner == q1 ? quote1 : quote2;
                        winnerQuote.onWinBattle();
                        Quote loserQuote = winner == q1 ? quote2 : quote1;
                        
                        result.delete();
                        IMessage runoffResult = runBattle(ctx, KILL, SPARE, (duration, remaining) -> getRunoffMessage(loser, loserQuote, duration, remaining));
                        if (runoffResult == null) {
                            break; // Battle canceled;
                        }
                        
                        EmbedBuilder results = new EmbedBuilder()
                                .appendField(CROWN + " Quote #" + winner + " is the winner, with " + (Math.max(votes1, votes2) - 1) + " votes! " + CROWN, winnerQuote.toString(), false);
                        votes1 = runoffResult.getReactionByEmoji(KILL).getCount();
                        votes2 = runoffResult.getReactionByEmoji(SPARE).getCount();
                        if (votes1 + votes2 - 2 <= 3 || votes1 <= votes2) {
                            loserQuote.onSpared();
                            results.appendField(SPARE + " Quote #" + loser + " has been spared! For now... " + SPARE, loserQuote.toString(), false);
                        } else {
                            storage.get(ctx).remove(loser);
                            results.appendField(SKULL + " Here lies quote #" + loser + ". May it rest in peace. " + SKULL, loserQuote.toString(), false);
                        }
                        RequestBuffer.request(runoffResult::delete);
                        ctx.replyBuffered(results.build());
                    }
                }
                
                battles.remove(ctx.getChannel());
            
            }
            
            private @Nullable IMessage runBattle(CommandContext ctx, ReactionEmoji choice1, ReactionEmoji choice2, BattleMessageSupplier msgSupplier) {
                
                final long time = this.time.get(); // Make sure this stays the same throughout this battle stage

                IMessage msg = ctx.replyBuffered(msgSupplier.getMessage(time, time)).get();
      
                final long sentTime = System.currentTimeMillis();
                final long endTime = sentTime + time;
                
                allBattles.add(msg);
                RequestHelper.requestOrdered(
                        () -> msg.addReaction(choice1),
                        () -> msg.addReaction(choice2)
                );
                
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
                    EmbedObject e = msgSupplier.getMessage(time, remaining);
                    RequestBuffer.request(() -> msg.edit(e));
                    try {
                        // Update the time remaining at half, or 5 seconds, whichever is higher
                        Thread.sleep(Math.min(remaining, Math.max(remaining / 2L, 5000)));
                    } catch (InterruptedException ex) {
                        return cancel(msg);
                    }
                }
                
                allBattles.remove(msg);
                return ctx.getChannel().fetchMessage(msg.getLongID());
            }
            
            private <T> T cancel(IMessage msg) {
                RequestHelper.requestOrdered(
                    () -> msg.edit("All battles canceled."),
                    () -> msg.removeAllReactions());
                allBattles.remove(msg);
                return null;
            }
        }
        
        private final Map<IChannel, BattleThread> battles = Maps.newConcurrentMap();
        private final Set<IMessage> allBattles = Sets.newConcurrentHashSet();

        private final ReactionEmoji ONE = getUnicodeEmoji("one");
        private final ReactionEmoji TWO = getUnicodeEmoji("two");

        private final ReactionEmoji KILL = getUnicodeEmoji("skull_crossbones");
        private final ReactionEmoji SPARE = getUnicodeEmoji("innocent");

        private final ReactionEmoji CROWN = getUnicodeEmoji("crown");
        private final ReactionEmoji SKULL = getUnicodeEmoji("skull");
        
        private ReactionEmoji getUnicodeEmoji(String alias) {
            return ReactionEmoji.of(EmojiManager.getForAlias(alias).getUnicode());
        }

        @EventSubscriber
        public void onReactAdd(ReactionAddEvent event) {
            ReactionEmoji emoji = event.getReaction().getEmoji();
            IMessage msg = event.getMessage();
            if (msg != null && allBattles.contains(msg)) {
                if (!emoji.equals(ONE) && !emoji.equals(TWO) && !emoji.equals(KILL) && !emoji.equals(SPARE)) {
                    RequestBuffer.request(() -> msg.removeReaction(event.getUser(), event.getReaction()));
                } else if (!event.getUser().equals(K9.instance.getOurUser())) {
                    msg.getReactions().stream().filter(r -> !r.getEmoji().equals(emoji) && r.getUserReacted(event.getUser())).forEach(r ->
                        RequestBuffer.request(() -> msg.removeReaction(event.getUser(), r))
                    );
                }
            }
        }
        
        public boolean canStart(CommandContext ctx) {
            return !battles.containsKey(ctx.getChannel());
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
        
        private EmbedObject appendRemainingTime(EmbedBuilder builder, long duration, long remaining) {
            return builder.withFooterText(
                        "This battle will last " + DurationFormatUtils.formatDurationWords(duration, true, true) + " | " +
                        "Remaining: " + formatDuration(remaining)
                    ).build();
        }
        
        private EmbedObject getBattleMessage(int q1, int q2, Quote quote1, Quote quote2, long duration, long remaining) {
            EmbedBuilder builder = new EmbedBuilder()
                    .withTitle("QUOTE BATTLE")
                    .withDesc("Vote for the quote you want to win!")
                    .appendField("Quote 1", "#" + q1 + ": " + quote1, false)
                    .appendField("Quote 2", "#" + q2 + ": " + quote2, false);
            return appendRemainingTime(builder, duration, remaining);
        }
        
        private EmbedObject getRunoffMessage(int q, Quote quote, long duration, long remaining) {
            EmbedBuilder builder = new EmbedBuilder()
                    .withTitle("Kill or Spare?")
                    .withDesc("Quote #" + q + " has lost the battle. Should it be spared a grisly death?\n"
                            + "Vote " + KILL + " to kill, or " + SPARE + " to spare!")
                    .appendField("Quote #" + q, quote.toString(), true);
            return appendRemainingTime(builder, duration, remaining);
        }
        
        private long getTime(CommandContext ctx) throws CommandException {
            if (ctx.hasFlag(FLAG_BATTLE_TIME)) {
                try {
                    return TimeUnit.SECONDS.toMillis(Long.parseLong(ctx.getFlag(FLAG_BATTLE_TIME)));
                } catch (NumberFormatException e) {
                    throw new CommandException(e);
                }
            } else {
                return TimeUnit.MINUTES.toMillis(1);
            }
        }
        
        public void updateTime(CommandContext ctx) throws CommandException {
            BattleThread battle = battles.get(ctx.getChannel());
            if (battle != null) {
                battle.time.set(getTime(ctx));
            } else {
                throw new CommandException("No battle(s) running in this channel!");
            }
        }
        
        public void battle(CommandContext ctx) throws CommandException {
            if (!battleManager.canStart(ctx)) {
                throw new CommandException("Cannot start a battle, one already exists in this channel! To queue battles, use -s.");
            }
            if (storage.get(ctx).size() < 2) {
                throw new CommandException("There must be at least two quotes to battle!");
            }
            new BattleThread(ctx, getTime(ctx)).start();
        }

        public void cancel(CommandContext ctx) throws CommandException {
            if (battles.containsKey(ctx.getChannel())) {
                battles.get(ctx.getChannel()).interrupt();
            } else {
                throw new CommandException("There is no battle to cancel!");
            }
        }

        public void enqueueBattles(CommandContext ctx, int numBattles) throws CommandException {
            if (!battles.containsKey(ctx.getChannel())) {
                battle(ctx);
                if (numBattles > 0) {
                    numBattles--;
                }
            }
            if (battles.containsKey(ctx.getChannel())) {
                BattleThread battle = battles.get(ctx.getChannel());
                if (numBattles == -1) {
                    battle.queued.set(-1);
                } else {
                    battle.queued.addAndGet(numBattles);
                }
                if (ctx.hasFlag(FLAG_BATTLE_TIME)) {
                    updateTime(ctx);
                }
            } else {
                throw new CommandException("Could not start battle for unknown reason");
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
        
        public Quote(String quote, String quotee, IUser owner) {
            this(quote, quotee);
            this.owner = owner.getLongID();
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
    
    private static final Requirements REMOVE_PERMS = Requirements.builder().with(Permissions.MANAGE_MESSAGES, RequiredType.ALL_OF).build();
    
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
    public void onRegister() {
        super.onRegister();
        K9.instance.getDispatcher().registerListener(battleManager);
    }
    
    private final Pattern IN_QUOTES_PATTERN = Pattern.compile("\".*\"");

    @Override
    public void gatherParsers(GsonBuilder builder) {
        super.gatherParsers(builder);
        builder.registerTypeAdapter(Quote.class, (JsonDeserializer<Quote>) (json, typeOfT, context) -> {
            if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
                String quote = json.getAsString();
                if (IN_QUOTES_PATTERN.matcher(quote.trim()).matches()) {
                    quote = quote.trim().replace("\"", "");
                }
                int lastDash = quote.lastIndexOf('-');
                String author = lastDash < 0 ? "Anonymous" : quote.substring(lastDash + 1);
                quote = lastDash < 0 ? quote : quote.substring(0, lastDash);
                // run this twice in case the quotes were only around the "quote" part
                if (IN_QUOTES_PATTERN.matcher(quote.trim()).matches()) {
                    quote = quote.trim().replace("\"", "");
                }
                return new Quote(quote.trim(), author.trim(), K9.instance.getOurUser());
            }
            return new Gson().fromJson(json, Quote.class);
        });
    }
    
    Random rand = new Random();

    @Override
    public void process(CommandContext ctx) throws CommandException {
        if (ctx.hasFlag(FLAG_LS)) {
            Map<Integer, Quote> quotes = storage.get(ctx.getMessage());
            
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
                        throw new CommandException("Page argument out of range!");
                    }
                }
            } catch (NumberFormatException e) {
                throw new CommandException(ctx.getFlag(FLAG_LS) + " is not a valid number!");
            }

            msg.setPage(pageTarget);
            msg.send();
            return;
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

            Map<Integer, Quote> quotes = storage.get(ctx.getMessage());
            int id = quotes.keySet().stream().mapToInt(Integer::intValue).max().orElse(0) + 1;
            quotes.put(id, new Quote(ctx.sanitize(quote), ctx.sanitize(author), ctx.getAuthor()));
            ctx.replyBuffered("Added quote #" + id + "!");
            return;
        } else if (ctx.hasFlag(FLAG_REMOVE)) {
            if (!REMOVE_PERMS.matches(ctx.getAuthor(), ctx.getGuild())) {
                throw new CommandException("You do not have permission to remove quotes!");
            }
            int index = Integer.parseInt(ctx.getFlag(FLAG_REMOVE));
            Quote removed = storage.get(ctx.getMessage()).remove(index);
            if (removed != null) {
                ctx.replyBuffered("Removed quote!");
            } else {
                throw new CommandException("No quote for ID " + index);
            }
            return;
        }
        
        boolean canDoBattles = REMOVE_PERMS.matches(ctx.getAuthor(), ctx.getGuild());
        if (ctx.hasFlag(FLAG_BATTLE_CANCEL)) {
            if (!canDoBattles) {
                throw new CommandException("You do not have permission to cancel battles!");
            }
            battleManager.cancel(ctx);
            ctx.getMessage().delete();
            return;
        }
        
        if (ctx.hasFlag(FLAG_BATTLE)) {
            if (!canDoBattles) {
                throw new CommandException("You do not have permission to start battles!");
            }
            if (ctx.hasFlag(FLAG_BATTLE_SERIES)) {
                int numBattles;
                String value = ctx.getFlag(FLAG_BATTLE_SERIES);
                try {
                    numBattles = "infinite".equals(value) ? -1 : Integer.parseInt(ctx.getFlag(FLAG_BATTLE_SERIES));
                } catch (NumberFormatException e) {
                    throw new CommandException(e);
                }
                battleManager.enqueueBattles(ctx, numBattles);
                ctx.reply("Queued " + value + " quote battles.");
            } else {
                battleManager.battle(ctx);
            }
            return;
        }
        
        // Naked -t flag, just update the current battle/queue
        if (ctx.hasFlag(FLAG_BATTLE_TIME)) {
            battleManager.updateTime(ctx);
            ctx.replyBuffered("Updated battle time for ongoing battle(s).");
            return;
        }
        
        String quoteFmt = "#%d: %s";
        if(ctx.argCount() == 0) {
            Integer[] keys = storage.get(ctx.getMessage()).keySet().toArray(new Integer[0]);
            if (keys.length == 0) {
                throw new CommandException("There are no quotes!");
            }
            int id = rand.nextInt(keys.length);
            ctx.reply(String.format(quoteFmt, keys[id], storage.get(ctx).get(keys[id])));
        } else {
            int id = ctx.getArg(ARG_ID);
            Quote quote = storage.get(ctx.getMessage()).get(id);
            if (quote != null) {
                if (ctx.hasFlag(FLAG_INFO)) {
                    IUser owner = K9.instance.fetchUser(quote.getOwner());
                    EmbedObject info = new EmbedBuilder()
                            .withTitle("Quote #" + id)
                            .appendField("Text", quote.getQuote(), true)
                            .appendField("Quotee", quote.getQuotee(), true)
                            .appendField("Creator", owner.mention(), true)
                            .appendField("Battle Weight", "" + quote.getWeight(), true)
                            .build();
                    ctx.replyBuffered(info);
                } else if (ctx.hasFlag(FLAG_CREATOR)) {
                    if (!REMOVE_PERMS.matches(ctx.getAuthor(), ctx.getGuild())) {
                        throw new CommandException("You do not have permission to update quote creators.");
                    }
                    String creatorName = NullHelper.notnull(ctx.getFlag(FLAG_CREATOR), "CommandContext#getFlag");
                    IUser creator = null;
                    try {
                        creator = K9.instance.fetchUser(Long.parseLong(creatorName));
                    } catch (NumberFormatException e) {
                        if (!ctx.getMessage().getMentions().isEmpty()) {
                            creator = ctx.getMessage().getMentions().stream().filter(u -> creatorName.contains("" + u.getLongID())).findFirst().orElse(null);
                        }
                    }
                    if (creator != null) {
                        quote.setOwner(creator.getLongID());
                        ctx.replyBuffered("Updated creator for quote #" + id);
                    } else {
                        throw new CommandException(creatorName + " is not a valid user!");
                    }
                } else {
                    ctx.replyBuffered(String.format(quoteFmt, id, quote));
                }
            } else {
                throw new CommandException("No quote for ID " + id);
            }
        }
    }
    
    @Override
    public String getDescription() {
        return "A way to store and retrieve quotes.";
    }
}
