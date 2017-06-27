package com.blamejared.mcbot.mcp;

import com.blamejared.mcbot.mcp.ISrgMapping.*;
import lombok.Getter;

public class SrgMappingFactory {
    
    private static class FieldMapping extends SrgMappingBase {
        
        private FieldMapping(String notch, String SRG, String owner) {
            super(MappingType.FIELD, notch, SRG, owner);
        }
    }
    
    private static class ClassMapping extends SrgMappingBase {

        public ClassMapping(String notch, String SRG) {
            super(MappingType.CLASS, notch, SRG, null);
        }
        
        @Override
        public String toString() {
            return super.toString().replace("null: ", "");
        }
    }
    
    public static class ParamMapping extends SrgMappingBase {
        
        public ParamMapping(String SRG, String owner) {
            super(MappingType.PARAM, "", SRG, owner);
        }
        
    }
    
    public static class MethodMapping extends SrgMappingBase {
        
        @Getter
        private final String notchDesc, srgDesc;
        
        public MethodMapping(String notch, String notchDesc, String SRG, String srgDesc, String owner) {
            super(MappingType.METHOD, notch, SRG, owner);
            this.notchDesc = notchDesc;
            this.srgDesc = srgDesc;
        }
        
    }

    public ISrgMapping create(MappingType type, String line) {
        String[] data = line.trim().split("\\s+");
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
