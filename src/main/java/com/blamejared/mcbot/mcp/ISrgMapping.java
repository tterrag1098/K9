package com.blamejared.mcbot.mcp;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

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
        private final String srgKey;
        private final String csvName;
        private final MappingType parent;
    }
    
    MappingType getType();
    
    String getNotch();
    
    String getSRG();
    
    /**
     * For classes, meaningless.
     * For everything else, the class owner of the member.
     */
    String getOwner();
    
    @RequiredArgsConstructor
    public static abstract class SrgMappingBase implements ISrgMapping {
        
        private final @Getter MappingType type;
        private final @Getter String notch, SRG, owner;
        
        @Override
        public String toString() {
            return owner + ": " + notch + " <=> " + SRG;
        }
    }

}
