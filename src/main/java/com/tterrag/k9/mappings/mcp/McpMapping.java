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
    @NonFinal
    class Impl implements McpMapping {

        @Getter(onMethod = @__(@Override))
        MappingType type;

        @Getter(onMethod = @__(@Override))
        String original, intermediate, name;

        @Getter(onMethod = @__({ @Override, @Nullable }))
        String desc, owner;
        
        @Getter(onMethod = @__(@Override))
        boolean isStatic;

        @Getter(onMethod = @__({ @Override, @Nullable }))
        String comment;

        @Getter(onMethod = @__(@Override))
        Side side;
    }
}
