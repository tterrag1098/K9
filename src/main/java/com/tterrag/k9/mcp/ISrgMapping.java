package com.tterrag.k9.mcp;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import reactor.util.annotation.Nullable;

public interface ISrgMapping {
    
    @Getter
    @RequiredArgsConstructor
    public enum MappingType {
        CLASS('c', "CL", null, null),
        FIELD('f', "FD", "fields", CLASS),
        METHOD('m', "MD", "methods", CLASS),
        PARAM('p', null, "params", METHOD),
        ;
        
        private final char key;
        private final @Nullable String srgKey;
        private final @Nullable String csvName;
        private final @Nullable MappingType parent;
    }
    
    MappingType getType();
    
    String getNotch();
    
    String getSRG();
    
    /**
     * For classes, meaningless.
     * For everything else, the class owner of the member.
     */
    @Nullable String getOwner();
    
    @RequiredArgsConstructor
    public static abstract class SrgMappingBase implements ISrgMapping {
        
        private final @Getter MappingType type;
        private final @Getter String notch, SRG;
        private final @Getter @Nullable String owner;
        
        @Override
        public String toString() {
            return owner + ": " + notch + " <=> " + SRG;
        }
    }

}
