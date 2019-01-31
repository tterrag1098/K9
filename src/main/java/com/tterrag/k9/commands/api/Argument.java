package com.tterrag.k9.commands.api;

import java.util.Collection;
import java.util.regex.Pattern;

import com.tterrag.k9.util.Patterns;

public interface Argument<T> {

    String name();
    
    String description();
    
    T parse(String input);
    
    default Pattern pattern() {
        return Patterns.MATCH_ALL;
    }
    
    boolean required(Collection<Flag> flags);
}
