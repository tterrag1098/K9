package com.tterrag.k9.commands.api;


public interface Flag {
    
    char name();
    
    String longFormName();
    
    boolean needsValue();
    
    default boolean canHaveValue() {
        return true;
    }

    default String getDefaultValue() {
        return null;
    }

    String description();
}
