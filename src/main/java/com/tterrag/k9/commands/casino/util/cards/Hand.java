package com.tterrag.k9.commands.casino.util.cards;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public interface Hand {

    public void deal(Card card);

    /**
     * Returns this hand to the given deck.
     * <p>
     * This in effect clears this hand, and adds its old cards to the passed deck. This is analogous to folding a hand
     * in poker, etc.
     * 
     * @param deck
     */
    public void returnTo(Deck deck);

    public List<Card> getCards();

    int size();
    
    public class SimpleHand implements Hand {
        private final List<Card> cards = new ArrayList<>();
        
        @Override
        public void deal(Card card) {
            this.cards.add(card);
        }
        
        @Override
        public void returnTo(Deck deck) {
            deck.addAll(cards);
            cards.clear();
        }
        
        @Override
        public List<Card> getCards() {
            return Collections.unmodifiableList(cards);
        }
        
        @Override
        public int size() {
            return cards.size();
        }
    }

}
