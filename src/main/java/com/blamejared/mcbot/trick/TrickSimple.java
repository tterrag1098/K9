package com.blamejared.mcbot.trick;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class TrickSimple implements Trick {
    
    private final String pattern;

    @Override
    public String process(Object... args) {
        return String.format(pattern, args);
    }
}
