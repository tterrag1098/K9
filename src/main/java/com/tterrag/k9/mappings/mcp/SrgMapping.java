package com.tterrag.k9.mappings.mcp;

import com.tterrag.k9.mappings.MappingType;
import com.tterrag.k9.util.annotation.Nullable;

import lombok.Getter;
import lombok.Value;
import lombok.experimental.NonFinal;

@Value
@NonFinal
public class SrgMapping implements IntermediateMapping {
    
    @Getter(onMethod = @__(@Override))
    MappingType type;
    
    @Getter(onMethod = @__(@Override))
    String original;
    
    @Getter(onMethod = @__(@Override))
    String intermediate;
    
    @Nullable String originalDesc, intermediateDesc;
    
    @Getter(onMethod = @__(@Override))
    String owner;
    
    @Getter(onMethod = @__(@Override))
    boolean isStatic;

    @Override
    public @Nullable String getDesc() { return getIntermediateDesc(); }
    
    @Override
    public String formatMessage(String mcver) {
        return "";
    }
}
