package com.tterrag.k9.util;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.apache.commons.lang3.ArrayUtils;

import com.google.common.collect.ImmutableMap;

import clojure.java.api.Clojure;
import clojure.lang.APersistentMap;
import clojure.lang.Associative;
import clojure.lang.Counted;
import clojure.lang.IFn;
import clojure.lang.ISeq;
import clojure.lang.PersistentHashMap;
import clojure.lang.PersistentVector;
import clojure.lang.Seqable;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class TypeBindingPersistentMap extends APersistentMap {

    private static final long serialVersionUID = -4249276771716260359L;

    private interface Excludes {

        // lombok doesn't like rawtype overrides
        void putAll(@SuppressWarnings("rawtypes") Map other);

        // Special casing
        Object invoke();
        
        Object applyTo(ISeq seq);
    }
    
    private final TypeBinding<?> binding;

    @Delegate(types = { APersistentMap.class, IFn.class, Associative.class, Seqable.class, Counted.class }, excludes = Excludes.class)
    private final APersistentMap delegate;
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static <T> TypeBindingPersistentMap create(TypeBinding<T> binding, T val) {
        return new TypeBindingPersistentMap(binding, (APersistentMap) binding.toMap(val, m -> (Map) PersistentHashMap.create(vectorize(m)), s -> Clojure.read(":" + s)));
    }
    
    private static final Map<Class<?>, Function<Object, Object[]>> ARRAY_BOXERS = ImmutableMap.<Class<?>, Function<Object, Object[]>>builder()
            .put(boolean[].class, o -> ArrayUtils.toObject((boolean[]) o))
            .put(byte[].class, o -> ArrayUtils.toObject((byte[]) o))
            .put(char[].class, o -> ArrayUtils.toObject((char[]) o))
            .put(double[].class, o -> ArrayUtils.toObject((double[]) o))
            .put(float[].class, o -> ArrayUtils.toObject((float[]) o))
            .put(int[].class, o -> ArrayUtils.toObject((int[]) o))
            .put(long[].class, o -> ArrayUtils.toObject((long[]) o))
            .put(short[].class, o -> ArrayUtils.toObject((short[]) o))
            .build();
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static <T extends Map> T vectorize(T map) {
        for (Entry e : (Set<Entry>) map.entrySet()) {
            if (e.getValue() instanceof PersistentHashMap) {
                continue;
            } else if (e.getValue() instanceof Iterable) {
                e.setValue(PersistentVector.create((Iterable<?>) e.getValue()));
            } else if (e.getValue() instanceof Object[]) {
                e.setValue(PersistentVector.create(e.getValue()));
            } else if (e.getValue().getClass().isArray()) { // primitive arrays
                e.setValue(PersistentVector.create(ARRAY_BOXERS.get(e.getValue().getClass()).apply(e.getValue())));
            }
        }
        return map;
    }
    
    @Override
    public Object invoke() {
        return delegate;
    }
    
    @Override
    public String toString() {
        return binding.toString();
    }
    
    @Override
    public void putAll(@SuppressWarnings("rawtypes") Map t) {
        delegate.putAll(t);
    }
}
