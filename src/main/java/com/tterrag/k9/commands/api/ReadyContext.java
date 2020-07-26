package com.tterrag.k9.commands.api;

import java.io.File;

import com.google.gson.Gson;
import com.tterrag.k9.K9;

import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.Event;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

@RequiredArgsConstructor
public class ReadyContext {
    
    @Getter
    private final K9 k9;
    private final GatewayDiscordClient gateway;
    @Getter
    private final File dataFolder;
    @Getter
    private final Gson gson;
    
    public <E extends Event> Flux<E> on(Class<E> cls) {
        return gateway.on(cls);
    }
}
