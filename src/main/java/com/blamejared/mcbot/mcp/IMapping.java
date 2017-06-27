package com.blamejared.mcbot.mcp;

import com.blamejared.mcbot.mcp.ISrgMapping.MappingType;
import com.blamejared.mcbot.util.NonNull;

import lombok.Value;

public interface IMapping {
    
    enum Side {
        CLIENT,
        SERVER,
        BOTH
    }

    @NonNull MappingType getType();
    
    @NonNull String getSRG();
    
    @NonNull String getMCP();
    
    @NonNull String getComment();

    @NonNull Side getSide();
    
    @Value
    public static class Impl implements IMapping {
        private final MappingType type;
        private final String SRG, MCP, comment;
        private final Side side;
    }
}
