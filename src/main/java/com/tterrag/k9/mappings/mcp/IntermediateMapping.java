package com.tterrag.k9.mappings.mcp;

import com.tterrag.k9.mappings.Mapping;

public interface IntermediateMapping extends Mapping {
    
    /**
     * Only meaningful for methods.
     * 
     * @return If this method is static.
     */
    default boolean isStatic() { return false; }

}
