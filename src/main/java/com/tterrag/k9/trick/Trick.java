package com.tterrag.k9.trick;

import com.tterrag.k9.commands.api.CommandContext;
import com.tterrag.k9.util.BakedMessage;

import reactor.core.publisher.Mono;

@FunctionalInterface
public interface Trick {
    
    Mono<BakedMessage> process(CommandContext ctx, Object... args);

}
