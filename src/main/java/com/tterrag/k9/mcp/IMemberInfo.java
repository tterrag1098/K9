package com.tterrag.k9.mcp;

import com.tterrag.k9.mcp.IMCPMapping.Side;
import com.tterrag.k9.util.Nullable;

public interface IMemberInfo extends ISrgMapping {
    
    @Nullable String getComment();
    
    @Nullable Side getSide();
    
}
