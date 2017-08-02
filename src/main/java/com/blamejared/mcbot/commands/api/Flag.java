package com.blamejared.mcbot.commands.api;


public interface Flag {
    
    String name();
    
    String longFormName();
    
    boolean hasValue();

}
