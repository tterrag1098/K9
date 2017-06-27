package com.blamejared.mcbot.mcp;

import com.blamejared.mcbot.util.NonNull;
import com.blamejared.mcbot.util.Nullable;

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
        private final @Nullable String srgKey;
        private final @Nullable String csvName;
        private final @Nullable MappingType parent;
    }
    
    @NonNull MappingType getType();
    
    @NonNull String getNotch();
    
    @NonNull String getSRG();
    
    /**
     * For classes, meaningless.
     * For everything else, the class owner of the member.
     */
    @Nullable String getOwner();
    
    @RequiredArgsConstructor
    public static abstract class SrgMappingBase implements ISrgMapping {
        
        private final @Getter @NonNull MappingType type;
        private final @Getter @NonNull String notch, SRG;
        private final @Getter @Nullable String owner;
        
        @Override
        public String toString() {
            return owner + ": " + notch + " <=> " + SRG;
        }
    }

}
