package com.blamejared.mcbot.commands.api;


public interface Flag {
    
    String name();
    
    String longFormName();
    
    boolean needsValue();
    
    default boolean canHaveValue() {
        return true;
    }

    default String getDefaultValue() {
        return null;
    }
}
