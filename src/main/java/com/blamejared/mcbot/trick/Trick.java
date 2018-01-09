package com.blamejared.mcbot.trick;

@FunctionalInterface
public interface Trick {
    
    String process(Object... args);

}
