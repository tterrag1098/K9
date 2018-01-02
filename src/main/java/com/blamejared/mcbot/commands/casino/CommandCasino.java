package com.blamejared.mcbot.commands.casino;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import com.blamejared.mcbot.commands.api.Argument;
import com.blamejared.mcbot.commands.api.Command;
import com.blamejared.mcbot.commands.api.CommandContext;
import com.blamejared.mcbot.commands.api.CommandException;
import com.blamejared.mcbot.commands.api.CommandPersisted;
import com.blamejared.mcbot.commands.api.Flag;
import com.blamejared.mcbot.commands.casino.game.Blackjack;
import com.blamejared.mcbot.commands.casino.game.Game;
import com.blamejared.mcbot.commands.casino.game.GameAction;
import com.blamejared.mcbot.commands.casino.util.chips.Player;
import com.blamejared.mcbot.commands.casino.util.chips.Wallet;
import com.blamejared.mcbot.listeners.CommandListener;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.gson.reflect.TypeToken;

import sx.blah.discord.handle.obj.IChannel;

@Command
public class CommandCasino extends CommandPersisted<Map<Long, Wallet>> {
    
    private static final Map<String, Function<IChannel, Game<?>>> gameCreators = ImmutableMap.<String, Function<IChannel, Game<?>>>builder()
            .put("bj", chan -> new Blackjack(chan, 8, true))
            .build();
    
    private static final Flag FLAG_JOIN = new SimpleFlag("join", "Join a type of game", true, "");
    private static final Flag FLAG_LEAVE = new SimpleFlag("leave", "Leave the current game", false);
    private static final Flag FLAG_CONTINUE = new SimpleFlag("next", "Continue the current game", false);

    private static final Argument<Integer> ARG_BET = new IntegerArgument("bet", "The bet for the next hand.", false); 
    private static final Argument<String> ARG_ACTION = new WordArgument("action", "The action to perform in the current game.", false) {

        @Override
        public boolean required(Collection<Flag> flags) {
            return flags.isEmpty();
        }
    };

    public Map<IChannel, Game<?>> tables = new HashMap<>();

    public CommandCasino() {
        super("casino", false, HashMap::new);
    }
    
    @Override
    protected TypeToken<Map<Long, Wallet>> getDataType() {
        return new TypeToken<Map<Long, Wallet>>(){};
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public void process(CommandContext ctx) throws CommandException {
        Wallet wallet = storage.get(ctx).computeIfAbsent(ctx.getAuthor().getLongID(), id -> new Wallet());
        if (ctx.hasFlag(FLAG_JOIN)) {
            if (wallet.chips() == 0) {
                if (wallet.payday()) {
                    ctx.reply("Looks like you're out of chips. Thankfully, it's payday!");
                } else {
                    throw new CommandException("You have no chips!");
                }
            }
            Game<?> game = tables.get(ctx.getChannel());
            Player player = new Player(ctx.getAuthor(), wallet);
            player.setBet(ctx.getArgOrElse(ARG_BET, (int) Math.min(50, wallet.chips())));
            if (game == null) {
                if (ctx.getFlag(FLAG_JOIN).isEmpty()) {
                    throw new CommandException("No table in progress.");
                }
                game = gameCreators.get(ctx.getFlag(FLAG_JOIN)).apply(ctx.getChannel());
                game.addPlayer(player);
                game.restart();
                tables.put(ctx.getChannel(), game);
                game.getIntro().send(ctx.getChannel());
                printGame(game, ctx);
            } else {
                if (game.addPlayer(player)) {
                    ctx.reply("Joining a table in progress, wait for next round...");
                } else {
                    throw new CommandException("Cannot join a table you are already in!");
                }
            }
        } else if (ctx.hasFlag(FLAG_LEAVE)) {
            Game<?> game = tables.get(ctx.getChannel());
            if (game != null) {
                game.removePlayer(ctx.getAuthor());
                if (game.isEmpty()) {
                    tables.remove(ctx.getChannel());
                    ctx.reply("Everyone has left the game, the table has ended.");
                } else {
                    ctx.reply("You left the game of " + game.getClass().getSimpleName());
                }
            }
        } else if (ctx.hasFlag(FLAG_CONTINUE)) {
            Game<?> game = tables.get(ctx.getChannel());
            if (game == null) {
                throw new CommandException("No ongoing game!");
            }
            game.restart();
            printGame(game, ctx);
        } else {
            Game<?> game = tables.get(ctx.getChannel());
            if (game == null) {
                throw new CommandException("No ongoing game!");
            }
            GameAction action = game.getPossibleActions().stream().filter(a -> a.getName().equals(ctx.getArg(ARG_ACTION))).findFirst().orElseThrow(() -> new CommandException("No such action!"));
            action.accept(game);
            printGame(game, ctx);
        }
    }
    
    @SuppressWarnings({ "null", "rawtypes", "unchecked" })
    private void printGame(Game<?> game, CommandContext ctx) {
        game.getGameState().send(ctx.getChannel());
        Set<GameAction> actions = (Set<GameAction>) game.getPossibleActions();
        if (actions.isEmpty()) {
            String prefix = CommandListener.getPrefix(ctx.getGuild());
            ctx.reply("Game over! Use `" + prefix + "casino -next` to continue, or `" + prefix + "casino -leave` to leave the table.");
        } else {
            ctx.reply("Options: " + Joiner.on(", ").join(game.getPossibleActions().stream().map(GameAction::getName).toArray(String[]::new)));
        }
    }

    @Override
    public String getDescription() {
        return "Got some money you don't want anymore? Come spend it here, at the casino!";
    }
}
