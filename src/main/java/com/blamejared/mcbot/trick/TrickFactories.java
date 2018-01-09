package com.blamejared.mcbot.trick;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public enum TrickFactories {
    
    INSTANCE;
    
    private final Map<String, Function<String, Trick>> factories = new HashMap<>();
    
    public Trick create(String type, String input) {
        return Optional.ofNullable(factories.get(type)).map(f -> f.apply(input)).orElseThrow(IllegalArgumentException::new);
    }

    public void addFactory(String type, Function<String, Trick> factory) {
        this.factories.put(type, factory);
    }
    
    public String[] getTypes() {
        return factories.keySet().toArray(new String[factories.size()]);
    }
}
