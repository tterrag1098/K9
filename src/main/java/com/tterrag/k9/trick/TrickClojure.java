package com.tterrag.k9.trick;

import com.tterrag.k9.commands.CommandClojure;
import com.tterrag.k9.commands.api.CommandContext;
import com.tterrag.k9.commands.api.CommandException;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class TrickClojure implements Trick {
    
    private final CommandClojure clj;
    private final String code;
    
    @Override
    public String process(CommandContext ctx, Object... args) {
        try {
            return clj.exec(ctx, String.format(code, args)).toString();
        } catch (CommandException e) {
            e.printStackTrace();
            return "Error evaluating trick: " + e.getLocalizedMessage();
        }
    }
}
