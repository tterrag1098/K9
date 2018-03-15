package com.tterrag.k9.trick;

import com.tterrag.k9.commands.api.CommandContext;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class TrickSimple implements Trick {
    
    private final String pattern;

    @Override
    public String process(CommandContext ctx, Object... args) {
        return String.format(pattern, args);
    }
}
