package com.tterrag.k9.util;

import java.util.function.BiFunction;
import java.util.function.Function;

import reactor.core.publisher.Mono;

public class Monos {
    
    public static <A, B, C> Function<Mono<A>, Mono<C>> flatZipWith(Mono<? extends B> b, BiFunction<A, B, Mono<C>> combinator) {
        return in -> in.zipWith(b, combinator).flatMap(Function.identity());
    }
}
