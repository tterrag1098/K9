package com.blamejared.mcbot.srg;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

public interface ISrgMapping {
    
    @Getter
    @RequiredArgsConstructor
    public enum MappingType {
        FIELD('f', "FD"),
        METHOD('m', "MD"),
        CLASS('c', "CL");
        
        private final char key;
        private final String srgKey;
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
