package com.blamejared.mcbot.commands.casino.util.cards;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

@RequiredArgsConstructor
@Getter
@Accessors(fluent = true)
public enum Value {
    
    TWO(2),
    THREE(3),
    FOUR(4),
    FIVE(5),
    SIX(6),
    SEVEN(7),
    EIGHT(8),
    NINE(9),
    TEN(10),
    JACK(11, "J"),
    QUEEN(12, "Q"),
    KING(13, "K"),
    ACE(1, "A"),
    ;
    
    private final int numeric;
    private final String ident;
    
    private Value(int numeric) {
        this(numeric, Integer.toString(numeric));
    }
    
    public boolean isFace() {
        return this == JACK || this == QUEEN || this == KING;
    }

}
