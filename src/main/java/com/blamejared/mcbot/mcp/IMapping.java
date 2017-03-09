package com.blamejared.mcbot.mcp;

import com.blamejared.mcbot.mcp.ISrgMapping.MappingType;

import lombok.Value;

public interface IMapping {
    
    enum Side {
        CLIENT,
        SERVER,
        BOTH
    }

    MappingType getType();
    
    String getSRG();
    
    String getMCP();
    
    String getComment();

    Side getSide();
    
    @Value
    public static class Impl implements IMapping {
        private final MappingType type;
        private final String SRG, MCP, comment;
        private final Side side;
    }
}
