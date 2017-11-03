package com.blamejared.mcbot.commands.casino.game;

import java.util.Set;

import com.blamejared.mcbot.util.BakedMessage;

import sx.blah.discord.handle.obj.IUser;

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
    boolean addUser(IUser user);

    BakedMessage getIntro();

    BakedMessage getGameState();

    Set<? extends GameAction<T>> getPossibleActions();

}
