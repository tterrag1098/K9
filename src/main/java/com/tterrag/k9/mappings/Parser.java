package com.tterrag.k9.mappings;

import java.io.IOException;
import java.util.Collection;

@FunctionalInterface
public interface Parser<T, R> {

    Collection<R> parse(T input) throws IOException;
    
}
