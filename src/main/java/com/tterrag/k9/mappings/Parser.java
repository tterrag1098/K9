package com.tterrag.k9.mappings;

import java.io.IOException;
import java.util.List;

@FunctionalInterface
public interface Parser<T, R> {

    List<R> parse(T input) throws IOException;
    
}
