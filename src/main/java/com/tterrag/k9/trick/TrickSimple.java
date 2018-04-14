package com.tterrag.k9.trick;

import com.tterrag.k9.commands.api.CommandContext;
import com.tterrag.k9.util.BakedMessage;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class TrickSimple implements Trick {
    
    private final String pattern;

    @Override
    public BakedMessage process(CommandContext ctx, Object... args) {
        return new BakedMessage().withContent(String.format(pattern, args));
    }
}
