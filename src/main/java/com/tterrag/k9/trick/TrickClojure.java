package com.tterrag.k9.trick;

import com.tterrag.k9.commands.CommandClojure;
import com.tterrag.k9.commands.api.CommandContext;
import com.tterrag.k9.commands.api.CommandException;
import com.tterrag.k9.util.BakedMessage;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class TrickClojure implements Trick {
    
    private final CommandClojure clj;
    private final String code;
    
    @Override
    public BakedMessage process(CommandContext ctx, Object... args) {
        try {
            return clj.exec(ctx, String.format(code, args), args);
        } catch (CommandException e) {
            return new BakedMessage().withContent("Error evaluating trick: " + e.getLocalizedMessage());
        }
    }
}
