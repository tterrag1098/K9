package com.tterrag.k9.util;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

import reactor.core.publisher.Mono;

public class Monos {
    
    public static <A, B, C> Function<Mono<A>, Mono<C>> flatZipWith(Mono<? extends B> b, BiFunction<A, B, Mono<C>> combinator) {
        return in -> in.zipWith(b, combinator).flatMap(Function.identity());
    }
    
    public static <T> Function<Mono<T>, Mono<Optional<T>>> asOptional() {
        return in -> in.map(Optional::of).defaultIfEmpty(Optional.empty());
    }
    
    public static <T, R> Function<Mono<T>, Mono<R>> mapOptional(Function<T, Optional<R>> conv) {
        return in -> in.flatMap(t -> Mono.justOrEmpty(conv.apply(t)));
    }
}
