package com.blamejared.mcbot.commands.casino;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import com.blamejared.mcbot.commands.api.Argument;
import com.blamejared.mcbot.commands.api.Command;
import com.blamejared.mcbot.commands.api.CommandBase;
import com.blamejared.mcbot.commands.api.CommandContext;
import com.blamejared.mcbot.commands.api.CommandException;
import com.blamejared.mcbot.commands.api.Flag;
import com.blamejared.mcbot.commands.casino.game.Blackjack;
import com.blamejared.mcbot.commands.casino.game.Game;
import com.blamejared.mcbot.commands.casino.game.GameAction;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import sx.blah.discord.handle.obj.IChannel;

@Command
public class CommandCasino extends CommandBase {
    
    private static final Map<String, Function<IChannel, Game<?>>> gameCreators = ImmutableMap.<String, Function<IChannel, Game<?>>>builder()
            .put("bj", chan -> new Blackjack(chan, 8, true))
            .build();
    
    private static final Flag FLAG_JOIN = new SimpleFlag("join", "Join a type of game", true);
    private static final Flag FLAG_LEAVE = new SimpleFlag("leave", "Leave the current game", false);
    private static final Flag FLAG_CONTINUE = new SimpleFlag("next", "Continue the current game", false);

    private static final Argument<String> ARG_ACTION = new WordArgument("action", "The action to perform in the current game.", false) {

        @Override
        public boolean required(Collection<Flag> flags) {
            return flags.isEmpty();
        }
    };

    public Map<IChannel, Game<?>> tables = new HashMap<>();

    public CommandCasino() {
        super("casino", false, Lists.newArrayList(FLAG_JOIN, FLAG_LEAVE, FLAG_CONTINUE), Lists.newArrayList(ARG_ACTION));
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public void process(CommandContext ctx) throws CommandException {
        if (ctx.hasFlag(FLAG_JOIN)) {
            Game<?> game = tables.get(ctx.getChannel());
            if (game == null) {
                game = gameCreators.get(ctx.getFlag(FLAG_JOIN)).apply(ctx.getChannel());
                game.addUser(ctx.getAuthor());
                game.restart();
                tables.put(ctx.getChannel(), game);
                game.getIntro().send(ctx.getChannel());
                printGame(game, ctx);
            } else {
                if (game.addUser(ctx.getAuthor())) {
                    ctx.reply("Joining a table in progress, wait for next round...");
                } else {
                    throw new CommandException("Cannot join a table you are already in!");
                }
            }
        } else if (ctx.hasFlag(FLAG_LEAVE)) {
            Game<?> game = tables.get(ctx.getChannel());
            if (game != null) {
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
            ctx.reply("Game over! Use `!casino -next` to continue, or `!casino -leave` to leave the table.");
        } else {
            ctx.reply("Options: " + Joiner.on(", ").join(game.getPossibleActions().stream().map(GameAction::getName).toArray(String[]::new)));
        }
    }

    @Override
    public String getDescription() {
        return "Got some money you don't want anymore? Come spend it here, at the casino!";
    }
}
