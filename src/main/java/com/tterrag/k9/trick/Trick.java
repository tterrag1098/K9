package com.tterrag.k9.trick;

import com.tterrag.k9.commands.api.CommandContext;

@FunctionalInterface
public interface Trick {
    
    String process(CommandContext ctx, Object... args);

}
