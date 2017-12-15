package com.blamejared.mcbot.commands.casino.util.cards;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class Card {
    
    private final Suit suit;
    private final Value value;
    
    @Override
    public String toString() {
        return "" + value.ident() + suit.getSymbol();
    }

}
