package com.tterrag.k9.mappings;

import java.io.IOException;
import java.util.Collection;

import com.tterrag.k9.util.NonNull;

public interface MappingDatabase<@NonNull T extends Mapping> {
    
    void reload() throws IOException, NoSuchVersionException;
    
    Collection<T> lookup(MappingType type);
    
    Collection<T> lookup(MappingType type, String search);

}
