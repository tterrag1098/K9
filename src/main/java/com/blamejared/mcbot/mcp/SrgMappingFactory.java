package com.blamejared.mcbot.mcp;

import com.blamejared.mcbot.mcp.ISrgMapping.*;
import com.blamejared.mcbot.util.DefaultNonNull;
import com.blamejared.mcbot.util.NonNull;
import com.blamejared.mcbot.util.Nullable;

import lombok.Getter;

public class SrgMappingFactory {
    
    @DefaultNonNull
    private static class FieldMapping extends SrgMappingBase {
        
        private FieldMapping(String notch, String SRG, @Nullable String owner) {
            super(MappingType.FIELD, notch, SRG, owner);
        }
    }
    
    @DefaultNonNull
    private static class ClassMapping extends SrgMappingBase {

        public ClassMapping(String notch, String SRG) {
            super(MappingType.CLASS, notch, SRG, null);
        }
        
        @SuppressWarnings("null")
        @Override
        public String toString() {
            return super.toString().replace("null: ", "");
        }
    }
    
    @DefaultNonNull
    public static class ParamMapping extends SrgMappingBase {
        
        public ParamMapping(String SRG, @Nullable String owner) {
            super(MappingType.PARAM, "", SRG, owner);
        }
        
    }
    
    public static class MethodMapping extends SrgMappingBase {
        
        @Getter
        private final @NonNull String notchDesc, srgDesc;
        
        public MethodMapping(@NonNull String notch, @NonNull String notchDesc, @NonNull String SRG, @NonNull String srgDesc, @Nullable String owner) {
            super(MappingType.METHOD, notch, SRG, owner);
            this.notchDesc = notchDesc;
            this.srgDesc = srgDesc;
        }
        
    }

    @SuppressWarnings("null")
    public ISrgMapping create(MappingType type, String line) {
        @NonNull String[] data = line.trim().split("\\s+");
        switch(type) {
            case CLASS:
                return new ClassMapping(data[0], data[1]);
            case FIELD:
                String owner = data[1].substring(0, data[1].lastIndexOf('/'));
                return new FieldMapping(data[0].substring(data[0].lastIndexOf('/') + 1), data[1].replace(owner + "/", ""), owner);
            case METHOD:
                owner = data[2].substring(0, data[2].lastIndexOf('/'));
                return new MethodMapping(data[0].substring(data[0].lastIndexOf('/') + 1), data[1], data[2].replace(owner + "/", ""), data[3], owner);
            default:
                return null;
        }
    }

}
