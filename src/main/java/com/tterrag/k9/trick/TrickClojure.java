package com.tterrag.k9.trick;

import com.tterrag.k9.commands.CommandClojure;
import com.tterrag.k9.commands.api.CommandContext;
import com.tterrag.k9.commands.api.CommandException;
import com.tterrag.k9.util.BakedMessage;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class TrickClojure implements Trick {

    private final CommandClojure clj;
    private final String code;

    @Override
    public Mono<BakedMessage> process(CommandContext ctx, Object... args) {
        return clj.exec(ctx, String.format(code, args))
                .onErrorResume(CommandException.class, e -> Mono.just(new BakedMessage()
                        .withContent("Error evaluating trick: " + e.getLocalizedMessage())));
    }
}
