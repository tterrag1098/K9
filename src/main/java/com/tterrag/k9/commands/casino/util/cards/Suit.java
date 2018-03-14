package com.tterrag.k9.commands.casino.util.cards;

import static com.tterrag.k9.commands.casino.util.cards.Color.*;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum Suit {
    
    SPADES(BLACK, '\u2664'),
    HEARTS(RED, '\u2661'),
    DIAMOND(RED, '\u2662'),
    CLUBS(BLACK, '\u2667'),
    ;
    
    private final Color color;
    private final char symbol;

    public boolean isRed() {
        return color == RED;
    }
    
    public boolean isBlack() {
        return !isRed();
    }
}
