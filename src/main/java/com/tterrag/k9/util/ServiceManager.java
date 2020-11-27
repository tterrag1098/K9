package com.tterrag.k9.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import org.reactivestreams.Publisher;

import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.Event;
import it.unimi.dsi.fastutil.objects.Object2BooleanArrayMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import lombok.Getter;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
public class ServiceManager {

    private interface ServiceInitializer<R, P extends Publisher<R>> {

        String getName();

        P start(GatewayDiscordClient client);
    }

    @Value
    private static class BasicInitializer<R, P extends Publisher<R>> implements ServiceInitializer<R, P> {

        @Getter(onMethod = @__({ @Override }))
        String name;
        Supplier<P> factory;

        @Override
        public P start(GatewayDiscordClient client) {
            return getFactory().get();
        }
    }

    @Value
    private static class EventInitializer<T extends Event, R, P extends Publisher<R>> implements ServiceInitializer<R, P> {
        
        @Getter(onMethod = @__({@Override}))
        String name;
        Class<T> eventClass;
        Function<Flux<T>, P> factory;

        @Override
        public P start(GatewayDiscordClient client) {
            return getFactory().apply(client.on(getEventClass()));
        }
    }

    private final List<ServiceInitializer<?, ?>> initializers = new ArrayList<>();

    private final Set<String> failedServices = new HashSet<>();

    public <T extends Event, R, P extends Publisher<R>> ServiceManager eventService(String name, Class<T> eventClass, Function<Flux<T>, P> factory) {
        this.initializers.add(new EventInitializer<>(name, eventClass, factory));
        return this;
    }

    public <R, P extends Publisher<R>> ServiceManager service(String name, Supplier<P> factory) {
        this.initializers.add(new BasicInitializer<>(name, factory));
        return this;
    }

    public Mono<GatewayDiscordClient> start(GatewayDiscordClient client) {
        return Mono.just(client)
                .flatMap(c -> Mono.when(initializers.stream()
                        .map(i -> startService(i, c))
                        .toArray(Publisher[]::new)))
                .thenReturn(client);
    }

    private <T extends Event, R, P extends Publisher<R>> Publisher<R> startService(ServiceInitializer<R, P> initializer, GatewayDiscordClient client) {
        return Flux.from(initializer.start(client)).onErrorResume(t -> {
            log.error("Service failed! Service name: " + initializer.getName(), t);
            failedServices.add(initializer.getName());
            return Mono.empty();
        });
    }

    public Object2BooleanMap<String> status() {
        Object2BooleanMap<String> ret = new Object2BooleanArrayMap<>(initializers.size());
        for (ServiceInitializer<?, ?> initializer : initializers) {
            ret.put(initializer.getName(), !failedServices.contains(initializer.getName()));
        }
        return ret;
    }
}
