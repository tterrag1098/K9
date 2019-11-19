package com.tterrag.k9.util;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import reactor.core.publisher.Mono;

public class Monos {

    public static <A, B, C> Function<Mono<A>, Mono<C>> flatZipWith(Mono<? extends B> b, BiFunction<A, B, Mono<C>> combinator) {
        return in -> in.zipWith(b, combinator).flatMap(Function.identity());
    }

    public static <A, B, C> Function<Mono<A>, Mono<C>> flatZipWhen(Function<A, Mono<? extends B>> rightGenerator, BiFunction<A, B, Mono<C>> combinator) {
        return in -> in.zipWhen(rightGenerator, combinator).flatMap(Function.identity());
    }

    public static <T> Function<Mono<T>, Mono<Optional<T>>> asOptional() {
        return in -> in.map(Optional::of).defaultIfEmpty(Optional.empty());
    }

    public static <T, R> Function<Mono<T>, Mono<R>> mapOptional(Function<T, Optional<R>> conv) {
        return in -> in.flatMap(t -> Mono.justOrEmpty(conv.apply(t)));
    }

    public static <A, B, C> Function<Mono<A>, Mono<C>> zipOptional(Optional<? extends B> b, BiFunction<A, B, C> combinator) {
        return in -> in.zipWith(Mono.justOrEmpty(b), combinator);
    }

    public static <T> Function<Mono<T>, Mono<T>> precondition(Predicate<T> validator, Supplier<? extends Throwable> exceptionFactory) {
        return in -> in.handle((t, sink) -> { if (validator.test(t)) sink.next(t); else sink.error(exceptionFactory.get()); });
    }
}
