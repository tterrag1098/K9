package com.tterrag.k9.util;

import java.util.function.BiFunction;
import java.util.function.Function;

import org.reactivestreams.Publisher;

import reactor.core.publisher.Flux;
import reactor.core.publisher.GroupedFlux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

public class Fluxes {
    
    public static <A, B, C> Function<Flux<A>, Flux<C>> flatZipWith(Flux<? extends B> b, BiFunction<A, B, Publisher<C>> combinator) {
        return in -> in.zipWith(b, combinator).flatMap(Function.identity());
    }
    
    public static <T, R> Function<? super Flux<T>, ? extends Publisher<Tuple2<T, R>>> zipWhen(Function<T, Mono<? extends R>> valueMapper) {
        return in -> in.flatMap(k -> Mono.just(k).zipWhen(valueMapper));
    }
    
    public static <T, R, U> Function<Flux<T>, Flux<U>> zipWhen(Function<T, Mono<? extends R>> valueMapper, BiFunction<T, R, ? extends U> combinator) {
        return in -> in.flatMap(k -> Mono.just(k).zipWhen(valueMapper, combinator));
    }
    
    public static <T, R, K> Function<Flux<T>, Flux<GroupedFlux<K, R>>> groupWith(Flux<K> keys, BiFunction<K, T, Mono<? extends R>> valueMapper) {
        return in -> in.flatMap(t -> keys.transform(Fluxes.<K, R>zipWhen(k -> valueMapper.apply(k, t)))).groupBy(Tuple2::getT1, Tuple2::getT2);
    }
}
