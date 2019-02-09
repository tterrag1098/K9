package com.tterrag.k9.util;

import java.util.function.BiFunction;
import java.util.function.Function;

import org.reactivestreams.Publisher;

import reactor.core.publisher.Flux;

public class Fluxes {
    
    public static <A, B, C> Function<Flux<A>, Flux<C>> flatZipWith(Flux<? extends B> b, BiFunction<A, B, Publisher<C>> combinator) {
        return in -> in.zipWith(b, combinator).flatMap(Function.identity());
    }
}
