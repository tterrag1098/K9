package com.tterrag.k9.mappings.yarn;

import com.google.common.net.UrlEscapers;

import lombok.Value;

@Value
public class MinecraftVersion {
    
    String version;
    boolean stable;
    
    public String getVersionEncoded() {
        return UrlEscapers.urlFragmentEscaper().escape(getVersion());
    }
}
