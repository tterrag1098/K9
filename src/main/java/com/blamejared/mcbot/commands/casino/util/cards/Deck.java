package com.blamejared.mcbot.commands.casino.util.cards;

import java.util.Collection;
import java.util.Collections;
import java.util.Stack;

public class Deck {
    
    private final Stack<Card> cards = new Stack<>();
    
    public Deck() {
        for (Suit suit : Suit.values()) {
            for (Value value : Value.values()) {
                cards.push(new Card(suit, value));
            }
        }
    }
    
    public Deck(Collection<Card> cards) {
        this.cards.addAll(cards);
    }
    
    public Collection<Card> getCards() {
        return Collections.unmodifiableCollection(cards);
    }
    
    public void shuffle() {
        Collections.shuffle(cards);
    }
    
    public void deal(Hand hand) {
        hand.deal(cards.pop());
    }
    
    public void merge(Deck other) {
        this.cards.addAll(other.getCards());
        other.cards.clear();
    }

    public void add(Card card) {
        this.cards.push(card);
    }

    public void addAll(Collection<Card> cards) {
        this.cards.addAll(cards);
    }
    
    @Override
    public String toString() {
        return cards.toString();
    }
}
