package com.tterrag.k9.mappings;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

import com.tterrag.k9.util.NonNull;

public interface MappingDatabase<@NonNull T extends Mapping> {
    
    void reload() throws IOException, NoSuchVersionException;
    
    default Collection<T> lookup(MappingType type) {
        return Arrays.stream(NameType.values()).flatMap(by -> lookup(by, type).stream()).collect(Collectors.toList());
    }
    
    default Collection<T> lookup(MappingType type, String search) {
        return Arrays.stream(NameType.values()).flatMap(by -> lookup(by, type, search).stream()).collect(Collectors.toList());
    }

    Collection<T> lookup(NameType by, MappingType type);

    Collection<T> lookup(NameType by, MappingType type, String search);
}
