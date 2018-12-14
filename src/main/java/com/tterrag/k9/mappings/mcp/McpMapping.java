package com.tterrag.k9.mappings.mcp;

import com.tterrag.k9.mappings.Mapping;
import com.tterrag.k9.mappings.MappingType;
import com.tterrag.k9.util.Nullable;

import lombok.Getter;
import lombok.Value;
import lombok.experimental.NonFinal;

public interface McpMapping extends IntermediateMapping {

    enum Side {
        CLIENT,
        SERVER,
        BOTH
    }

    String getComment();

    Side getSide();
    
    /**
     * @return The mapping that "owns" this one. Currently only meaningful for parameters.
     */
    default Mapping getParent() { return this; }

    @Value
    @Getter(onMethod = @__({ @Override }))
    @NonFinal
    class Impl implements McpMapping {

        MappingType type;

        String original, intermediate, name;

        @Nullable
        String desc, owner;
        
        boolean isStatic;

        @Nullable
        String comment;

        Side side;
    }
}
