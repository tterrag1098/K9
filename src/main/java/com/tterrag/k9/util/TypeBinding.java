package com.tterrag.k9.util;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Primitives;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.experimental.Wither;
import net.jodah.typetools.TypeResolver;
import reactor.core.publisher.Mono;

/**
 * General purpose class to create "bindings" for a java type, to be used in user-facing APIs (currently just clojure).
 * <p>
 * A binding is, in its simplest form, a map of strings to object components. But this class functions more as a
 * blueprint to create bindings. The actual binding can be made using {@link #toMap(Object)} and its overloads.
 * <p>
 * Basic usage might look like the following:
 * <blockquote><pre>{@code
 * TypeBinding<Color> colorBinding = new TypeBinding<Color>("Color")
 *     .bind("red", Color::getRed)
 *     .bind("green", Color::getGreen)
 *     .bind("blue", Color::getBlue);
 * }</pre></blockquote>
 * And then later:
 * <blockquote><pre>{@code
 * Color myColor = new Color(0, 255, 0);
 * Map<String, Object> binding = colorBinding.toMap(myColor);
 * }</pre></blockquote>
 * <p>
 * The class keeps track of all defined bindings and their types, even recursively, and represents it with a nice
 * tree format from {@link #toString()}.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class TypeBinding<T> {

    private interface Binding<T> {

        <R extends Map<K, ?>, K> Object create(T in, Function<Map<K, Object>, R> mapConverter, Function<String, K> keyConverter);

    }

    @Value
    private class ObjectBinding implements Binding<T> {

        Function<T, ?> creator;
        Class<?> resultClass;
        boolean optional;

        ObjectBinding(Function<T, ?> creator, boolean optional) {
            this(creator, getResultType(creator), optional);
        }

        ObjectBinding(Function<T, ?> creator, Class<?> resultClass, boolean optional) {
            Preconditions.checkArgument(resultClass != Optional.class, "Optional should not be used as a binding value, please use bindOptional()!");
            Preconditions.checkArgument(resultClass != OptionalInt.class, "OptionalInt should not be used as a binding value, please use bindOptionalInt()!");
            this.creator = creator;
            this.resultClass = Primitives.unwrap(resultClass);
            this.optional = optional;
        }

        @Override
        public <R extends Map<K, ?>, K> Object create(T in, Function<Map<K, Object>, R> mapConverter, Function<String, K> keyConverter) {
            return creator.apply(in);
        }
    }
    
    @RequiredArgsConstructor(access = AccessLevel.PROTECTED)
    private abstract class RecursiveBinding<S> implements Binding<T> {
        
        @Getter
        private final TypeBinding<S> child;
        
    }

    private class SingleRecursiveBinding<S> extends RecursiveBinding<S> {

        private final Function<T, S> converter;
        
        SingleRecursiveBinding(TypeBinding<S> child, Function<T, S> converter) {
            super(child);
            this.converter = converter;
        }

        @Override
        public <R extends Map<K, ?>, K> Object create(T in, Function<Map<K, Object>, R> mapConverter, Function<String, K> keyConverter) {
            S res = converter.apply(in);
            return res == null ? null : getChild().toMap(res, mapConverter, keyConverter);
        }
    }

    private class ManyRecursiveBinding<S> extends RecursiveBinding<S> {

        private final Function<T, Collection<S>> converter;
        
        ManyRecursiveBinding(TypeBinding<S> child, Function<T, Collection<S>> converter) {
            super(child.withTypeName(child.getTypeName() + "[]"));
            this.converter = converter;
        }

        @Override
        public <R extends Map<K, ?>, K> Object create(T in, Function<Map<K, Object>, R> mapConverter, Function<String, K> keyConverter) {
            Collection<S> res = converter.apply(in);
            return res == null ? null : res.stream().map(v -> getChild().toMap(v, mapConverter, keyConverter)).collect(Collectors.toList());
        }
    }

    @Wither
    @Getter
    private final String typeName;
    private final Map<String, Binding<T>> bindings;
    
    public TypeBinding(String typeName) {
        this(typeName, new LinkedHashMap<>());
    }

    private Class<?> getResultType(Function<T, ?> f) {
        return TypeResolver.resolveRawArguments(Function.class, f.getClass())[1];
    }

    private TypeBinding<T> bind(String name, Binding<T> binding) {
        bindings.put(name, binding);
        return this;
    }

    public <R> TypeBinding<T> bind(String name, Function<T, R> creator) {
        return bind(name, new ObjectBinding(creator, false));
    }
    
    public <R> TypeBinding<T> bind(String name, Function<T, R> creator, Class<? extends R> realType) {
        return bind(name, new ObjectBinding(creator, realType, false));
    }

    public <R> Mono<TypeBinding<T>> bind(String name, Mono<Function<T, R>> creator) {
        return creator.map(c -> bind(name, c));
    }

    public <R> TypeBinding<T> bindOptional(String name, Function<T, Optional<R>> val, Class<? extends R> realType) {
        return bind(name, new ObjectBinding(val.andThen(o -> o.orElse(null)), realType, true));
    }

    public TypeBinding<T> bindOptionalInt(String name, Function<T, OptionalInt> val) {
        return bind(name, new ObjectBinding(val.andThen(o -> o.isPresent() ? (Integer) o.getAsInt() : null), int.class, true));
    }
    
    public TypeBinding<T> bindOptionalLong(String name, Function<T, OptionalLong> val) {
        return bind(name, new ObjectBinding(val.andThen(o -> o.isPresent() ? (Long) o.getAsLong() : null), long.class, true));
    }

    public <R> TypeBinding<T> bindRecursive(String name, Function<T, R> converter, TypeBinding<R> creator) {
        return bind(name, new SingleRecursiveBinding<>(creator, converter));
    }

    public <R> TypeBinding<T> bindRecursiveMany(String name, Function<T, Collection<R>> converter, TypeBinding<R> creator) {
        return bind(name, new ManyRecursiveBinding<>(creator, converter));
    }

    public <R> TypeBinding<T> bindRecursiveOptional(String name, Function<T, Optional<R>> converter, TypeBinding<R> creator) {
        return bind(name, new SingleRecursiveBinding<>(creator.withTypeName(creator.getTypeName() + "?"), converter.andThen(o -> o.orElse(null))));
    }

    public Map<String, Object> toMap(T in) {
        return toMap(in, Function.identity());
    }

    public <R extends Map<String, ?>> R toMap(T in, Function<Map<String, Object>, R> mapConverter) {
        return toMap(in, mapConverter, Function.identity());
    }

    public <R extends Map<K, ?>, K> R toMap(T in, Function<Map<K, Object>, R> mapConverter, Function<String, K> keyConverter) {
        return mapConverter.apply(bindings.entrySet().stream().map(e -> Pair.of(e.getKey(), e.getValue().create(in, mapConverter, keyConverter))).filter(p -> p.getRight() != null).collect(
                Collectors.toMap(p -> keyConverter.apply(p.getLeft()), Pair::getRight)));
    }

    @SuppressWarnings("unchecked")
    private String toStringRecursive(String indent) {
        String eol = "\n" + indent;
        StringBuilder ret = new StringBuilder(getTypeName()).append(eol);
        Iterator<Entry<String, Binding<T>>> iter = bindings.entrySet().iterator();
        while (iter.hasNext()) {
            Entry<String, Binding<T>> e = iter.next();
            Binding<T> binding = e.getValue();
            char treeChar = iter.hasNext() ? '\u251C' : '\u2514';
            if (binding instanceof TypeBinding.ObjectBinding) {
                TypeBinding<T>.ObjectBinding objectBinding = (TypeBinding<T>.ObjectBinding) binding;
                String resultName = objectBinding.getResultClass().getSimpleName();
                if (objectBinding.isOptional()) {
                    resultName += "?";
                }
                ret.append(treeChar).append(e.getKey()).append(": ").append(resultName).append(eol);
            } else if (binding instanceof TypeBinding.RecursiveBinding) {
                ret.append(treeChar).append(e.getKey()).append(": ").append(((TypeBinding<T>.RecursiveBinding<?>) binding).getChild().toStringRecursive(indent + (iter.hasNext() ? "\u2502  " : "   "))).append(eol);
            } else {
                throw new IllegalArgumentException();
            }
        }
        return ret.substring(0, ret.length() - eol.length());
    }

    @Override
    public String toString() {
        return toStringRecursive("");
    }
}
