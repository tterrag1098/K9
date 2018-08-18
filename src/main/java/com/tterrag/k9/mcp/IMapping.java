package com.tterrag.k9.mcp;

import com.tterrag.k9.mcp.ISrgMapping.MappingType;
import com.tterrag.k9.util.NonNull;
import com.tterrag.k9.util.Nullable;

public interface IMapping {
    
    @NonNull MappingType getType();

    @Nullable String getNotch();

    @NonNull String getSRG();
        
    @Nullable String getMCP();
    
}
