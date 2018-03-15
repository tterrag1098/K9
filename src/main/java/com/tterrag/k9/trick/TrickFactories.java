package com.tterrag.k9.trick;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public enum TrickFactories {
    
    INSTANCE;
    
    private final Map<TrickType, Function<String, Trick>> factories = new HashMap<>();
    
    public Trick create(TrickType type, String input) {
        return Optional.ofNullable(factories.get(type)).map(f -> f.apply(input)).orElseThrow(IllegalArgumentException::new);
    }

    public void addFactory(TrickType type, Function<String, Trick> factory) {
        this.factories.put(type, factory);
    }
    
    public TrickType[] getTypes() {
        return factories.keySet().toArray(new TrickType[factories.size()]);
    }
}
