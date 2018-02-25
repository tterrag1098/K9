package com.tterrag.k9.commands.api;

import java.util.Collection;
import java.util.regex.Pattern;

public interface Argument<T> {

    String name();
    
    String description();
    
    T parse(String input);
    
    static Pattern MATCH_ALL = Pattern.compile(".+$", Pattern.DOTALL);
    static Pattern MATCH_WORD = Pattern.compile("\\S+");
    
    default Pattern pattern() {
        return MATCH_ALL;
    }
    
    boolean required(Collection<Flag> flags);
}
