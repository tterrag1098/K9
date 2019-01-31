package com.tterrag.k9.mappings;

import java.util.function.Function;

import com.tterrag.k9.util.Nullable;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum NameType {
    ORIGINAL(Mapping::getOriginal),
    INTERMEDIATE(Mapping::getIntermediate),
    NAME(Mapping::getName);
    
    private final Function<Mapping, String> getter;
    
    public @Nullable String get(Mapping mapping) {
        return getter.apply(mapping);
    }
}