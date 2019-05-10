package com.tterrag.k9.commands.api;

import com.tterrag.k9.util.annotation.Nullable;

public interface Flag {
    
    char name();
    
    String longFormName();
    
    boolean needsValue();
    
    default boolean canHaveValue() {
        return true;
    }

    default @Nullable String getDefaultValue() {
        return null;
    }

    String description();
}
