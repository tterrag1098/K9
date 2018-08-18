package com.tterrag.k9.mcp;

import com.tterrag.k9.util.NonNull;
import com.tterrag.k9.util.Nullable;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

public interface ISrgMapping extends IMapping {
    
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
    
    @Override
    @NonNull
    String getNotch();
    
    /**
     * @return
     * For classes, null.<p>
     * For everything else, the class owner of the member.
     */
    @Nullable String getOwner();
    
    /**
     * @return For method mappings, the method descriptor (in srg names). Otherwise, null.
     */
    @Nullable String getDesc();
    
    @RequiredArgsConstructor
    public static abstract class SrgMappingBase implements ISrgMapping {
        
        private final @Getter @NonNull MappingType type;
        private final @Getter @NonNull String notch, SRG;
        private final @Getter @Nullable String owner;
        
        @Override
        public String getMCP() {
            return null;
        }

        @Override
        public String toString() {
            return owner + ": " + notch + " <=> " + SRG;
        }
        
        @Override
        public String getDesc() {
            return null;
        }
    }

}
