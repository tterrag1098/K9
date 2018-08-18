package com.tterrag.k9.mcp;

import com.tterrag.k9.mcp.ISrgMapping.MappingType;
import com.tterrag.k9.util.NonNull;

import lombok.Value;

public interface IMCPMapping extends IMapping {
    
    enum Side {
        CLIENT,
        SERVER,
        BOTH
    }
    
    @Override
    @NonNull
    String getMCP();
    
    @NonNull String getComment();

    @NonNull Side getSide();
    
    @Value
    public static class Impl implements IMCPMapping {
        private final MappingType type;
        private final String SRG, MCP, comment;
        private final Side side;
        
        @Override
        public String getNotch() {
            return null;
        }
    }
}
