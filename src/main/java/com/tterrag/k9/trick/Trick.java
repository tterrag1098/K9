package com.tterrag.k9.trick;

import com.tterrag.k9.commands.api.CommandContext;
import com.tterrag.k9.util.BakedMessage;

@FunctionalInterface
public interface Trick {
    
    BakedMessage process(CommandContext ctx, Object... args);

}
