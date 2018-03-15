package com.tterrag.k9.commands;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
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
import com.tterrag.k9.util.BakedMessage;
import com.tterrag.k9.util.NullHelper;
import com.tterrag.k9.util.PaginatedMessageFactory;
import com.tterrag.k9.util.PaginatedMessageFactory.PaginatedMessage;
import com.tterrag.k9.util.RequestHelper;
import com.tterrag.k9.util.Requirements;
import com.tterrag.k9.util.Requirements.RequiredType;
import com.tterrag.k9.util.Threads;
import com.vdurmont.emoji.Emoji;
import com.vdurmont.emoji.EmojiManager;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.val;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.api.internal.json.objects.EmbedObject;
import sx.blah.discord.handle.impl.events.guild.channel.message.reaction.ReactionAddEvent;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.handle.obj.IUser;
import sx.blah.discord.handle.obj.Permissions;
import sx.blah.discord.util.EmbedBuilder;
import sx.blah.discord.util.RequestBuffer;

@Command
public class CommandQuote extends CommandPersisted<Map<Integer, Quote>> {
    
    private interface BattleMessageSupplier {
        
        EmbedObject getMessage(long duration, long remaining);
    }
    
    private class BattleManager {
        
        private final Map<IChannel, IMessage> battles = Maps.newConcurrentMap();
        private final Set<IMessage> allBattles = Sets.newConcurrentHashSet();

        private final Emoji ONE = EmojiManager.getForAlias("one");
        private final Emoji TWO = EmojiManager.getForAlias("two");

        private final Emoji KILL = EmojiManager.getForAlias("skull_crossbones");
        private final Emoji SPARE = EmojiManager.getForAlias("innocent");

        private final Emoji CROWN = EmojiManager.getForAlias("crown");
        private final Emoji SKULL = EmojiManager.getForAlias("skull");

        @EventSubscriber
        public void onReactAdd(ReactionAddEvent event) {
            Emoji emoji = EmojiManager.getByUnicode(event.getReaction().getName());
            if (allBattles.contains(event.getMessage())) {
                IMessage msg = event.getMessage();
                if (emoji != ONE && emoji != TWO && emoji != KILL && emoji != SPARE) {
                    RequestBuffer.request(() -> msg.removeReaction(event.getUser(), event.getReaction()));
                } else if (!event.getUser().equals(K9.instance.getOurUser())) {
                    event.getMessage().getReactions().stream().filter(r -> !r.getEmoji().equals(event.getReaction()) && r.getUserReacted(event.getUser())).forEach(r ->
                        RequestBuffer.request(() -> event.getMessage().removeReaction(event.getUser(), r))
                    );
                }
            }
        }
        
        public boolean canStart(CommandContext ctx) {
            return !battles.containsKey(ctx.getChannel());
        }
        
        private int randomQuote(Map<Integer, Quote> map) throws CommandException {
            int totalWeight = map.values().stream().mapToInt(Quote::getWeight).sum();
            int choice = rand.nextInt(totalWeight);
            for (val e : map.entrySet()) {
                if (choice < e.getValue().getWeight()) {
                    return e.getKey();
                }
                choice -= e.getValue().getWeight();
            }
            throw new CommandException("Ran out of quotes? This should not happen!");
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
                            + "Vote " + KILL.getUnicode() + " to kill, or " + SPARE.getUnicode() + " to spare!")
                    .appendField("Quote #" + q, quote.toString(), true);
            return appendRemainingTime(builder, duration, remaining);
        }
        
        private IMessage runBattle(CommandContext ctx, long time, Emoji choice1, Emoji choice2, BattleMessageSupplier msgSupplier) {

            IMessage msg;
            battles.put(ctx.getChannel(), msg = ctx.replyBuffered(msgSupplier.getMessage(time, time)).get());
  
            final long sentTime = System.currentTimeMillis();
            final long endTime = sentTime + time;
            
            allBattles.add(msg);
            RequestHelper.requestOrdered(
                    () -> msg.addReaction(choice1),
                    () -> msg.addReaction(choice2)
            );
            
            // Wait at least 2 seconds before initial update
            Threads.sleep(Math.min(time, 2000));

            // Update remaining time every 5 seconds
            long sysTime;
            while ((sysTime = System.currentTimeMillis()) <= endTime) {
                long remaining = endTime - sysTime;
                EmbedObject e = msgSupplier.getMessage(time, remaining);
                RequestBuffer.request(() -> msg.edit(e));
                Threads.sleep(Math.min(remaining, 5000));
            }
            
            allBattles.remove(msg);
            return ctx.getChannel().getMessageByID(msg.getLongID());
        }
        
        public void battle(CommandContext ctx) throws CommandException {
            if (storage.get(ctx).size() < 2) {
                throw new CommandException("There must be at least two quotes to battle!");
            }
            
            final long time;
            if (ctx.hasFlag(FLAG_BATTLE_TIME)) {
                try {
                    time = TimeUnit.SECONDS.toMillis(Long.parseLong(ctx.getFlag(FLAG_BATTLE_TIME)));
                } catch (NumberFormatException e) {
                    throw new CommandException(e);
                }
            } else {
                time = TimeUnit.MINUTES.toMillis(1);
            }
            
            // Copy storage map so as to not alter it
            Map<Integer, Quote> tempMap = Maps.newHashMap(storage.get(ctx));
            int q1 = randomQuote(tempMap);
            // Make sure the same quote isn't picked twice
            tempMap.remove(q1);
            int q2 = randomQuote(tempMap);
            
            new Thread(() -> {
    
                Quote quote1 = storage.get(ctx).get(q1);
                Quote quote2 = storage.get(ctx).get(q2);
                
                IMessage result = runBattle(ctx, time, ONE, TWO, (duration, remaining) -> getBattleMessage(q1, q2, quote1, quote2, duration, remaining));
                   
                int votes1 = result.getReactionByUnicode(ONE).getCount();
                int votes2 = result.getReactionByUnicode(TWO).getCount();
                
                // If there are less than three votes, call it off
                if (votes1 + votes2 - 2 < 3) {
                    ctx.replyBuffered("That's not enough votes for me to commit murder, sorry.");
                    result.delete();
                } else if (votes1 == votes2) {
                    ctx.replyBuffered("It's a tie, we're all losers today.");
                    result.delete();
                } else {
                    int winner = votes1 > votes2 ? q1 : q2;
                    int loser = winner == q1 ? q2 : q1;
                    Quote winnerQuote = winner == q1 ? quote1 : quote2;
                    winnerQuote.onWinBattle();
                    Quote loserQuote = winner == q1 ? quote2 : quote1;
                    
                    result.delete();
                    IMessage runoffResult = runBattle(ctx, time, KILL, SPARE, (duration, remaining) -> getRunoffMessage(loser, loserQuote, duration, remaining));
                    
                    EmbedBuilder results = new EmbedBuilder()
                            .appendField(CROWN.getUnicode() + " Quote #" + winner + " is the winner, with " + (Math.max(votes1, votes2) - 1) + " votes! " + CROWN.getUnicode(), winnerQuote.toString(), false);
                    votes1 = runoffResult.getReactionByUnicode(KILL).getCount();
                    votes2 = runoffResult.getReactionByUnicode(SPARE).getCount();
                    if (votes1 + votes2 - 2 <= 3 || votes1 <= votes2) {
                        loserQuote.onSpared();
                        results.appendField(SPARE.getUnicode() + " Quote #" + loser + " has been spared! For now... " + SPARE.getUnicode(), loserQuote.toString(), false);
                    } else {
                        storage.get(ctx).remove(loser);
                        results.appendField(SKULL.getUnicode() + " Here lies quote #" + loser + ". May it rest in peace. " + SKULL.getUnicode(), loserQuote.toString(), false);
                    }
                    runoffResult.delete();
                    ctx.replyBuffered(results.build());
                }
                
                battles.remove(ctx.getChannel());
            }).start();
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
    private static final Flag FLAG_BATTLE_TIME = new SimpleFlag('t', "time", "The amount of time (in seconds) the battle will last.", true);
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
            
            int pageTarget = 0;
            int maxPages = ((quotes.size() - 1) / PER_PAGE) + 1;
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
            		builder.append("List of quotes (Page " + page + "/" + maxPages + "):\n");
            	}
            	if (builder != null) {
            	    builder.append(e.getKey()).append(") ").append(e.getValue()).append("\n");
            	}
                count++;
            }
            if (builder != null) {
                messagebuilder.addPage(new BakedMessage().withContent(builder.toString()));
            }
            PaginatedMessage msg = messagebuilder.setParent(ctx.getMessage()).build();
            msg.setPage(pageTarget);
            msg.send();
            return;
        } else if (ctx.hasFlag(FLAG_ADD)) {
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
            ctx.reply("Added quote #" + id + "!");
            return;
        } else if (ctx.hasFlag(FLAG_REMOVE)) {
            if (!REMOVE_PERMS.matches(ctx.getAuthor(), ctx.getGuild())) {
                throw new CommandException("You do not have permission to remove quotes!");
            }
            int index = Integer.parseInt(ctx.getFlag(FLAG_REMOVE));
            Quote removed = storage.get(ctx.getMessage()).remove(index);
            if (removed != null) {
                ctx.reply("Removed quote!");
            } else {
                throw new CommandException("No quote for ID " + index);
            }
            return;
        } else if (ctx.hasFlag(FLAG_BATTLE)) {
            if (!REMOVE_PERMS.matches(ctx.getAuthor(), ctx.getGuild())) {
                throw new CommandException("You do not have permission to start a battle!");
            }
            if (!battleManager.canStart(ctx)) {
                throw new CommandException("Cannot start a battle, one already exists in this channel!");
            }
            battleManager.battle(ctx);
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
