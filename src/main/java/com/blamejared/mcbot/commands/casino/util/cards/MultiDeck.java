package com.blamejared.mcbot.commands.casino.util.cards;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class MultiDeck extends Deck {

    public MultiDeck(int count) {
        super(getCards(count));
    }

    private final static Collection<Card> getCards(int deckcount) {
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < deckcount; i++) {
            cards.addAll(new Deck().getCards());
        }
        return cards;
    }
}
