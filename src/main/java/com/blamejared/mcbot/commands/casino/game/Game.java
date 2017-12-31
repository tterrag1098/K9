package com.blamejared.mcbot.commands.casino.game;

import java.util.Set;

import com.blamejared.mcbot.commands.casino.util.chips.Player;
import com.blamejared.mcbot.util.BakedMessage;

public interface Game<T extends Game<T>> {

    /**
     * Reset for a new hand. For instance, if a hand of blackjack or poker has finished, this will clear the player
     * hands and place them in the discard pile. Do not recreate decks, but shuffling is allowed.
     */
    void restart();

    /**
     * Adds a user to this table/game, if the user cannot join at this time, it should let them "wait" at the table
     * until the next round begins.
     * 
     * @param user
     */
    boolean addPlayer(Player user);

    BakedMessage getIntro();

    BakedMessage getGameState();

    Set<? extends GameAction<T>> getPossibleActions();

}
