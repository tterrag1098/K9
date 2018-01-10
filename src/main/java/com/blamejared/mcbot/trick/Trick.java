package com.blamejared.mcbot.trick;

import com.blamejared.mcbot.commands.api.CommandContext;

@FunctionalInterface
public interface Trick {
    
    String process(CommandContext ctx, Object... args);

}
