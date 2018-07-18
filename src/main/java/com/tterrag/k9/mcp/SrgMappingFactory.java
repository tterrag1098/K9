package com.tterrag.k9.mcp;

import com.tterrag.k9.mcp.ISrgMapping.MappingType;
import com.tterrag.k9.mcp.ISrgMapping.SrgMappingBase;
import com.tterrag.k9.util.NullHelper;

import lombok.Getter;
import reactor.util.annotation.Nullable;

public class SrgMappingFactory {
    
    private static class FieldMapping extends SrgMappingBase {
        
        private FieldMapping(String notch, String SRG, @Nullable String owner) {
            super(MappingType.FIELD, notch, SRG, owner);
        }
    }
    
    private static class ClassMapping extends SrgMappingBase {

        public ClassMapping(String notch, String SRG) {
            super(MappingType.CLASS, notch, SRG, null);
        }
        
        @Override
        public String toString() {
            return NullHelper.notnullJ(super.toString().replace("null: ", ""), "String#replace");
        }
    }
    
    public static class ParamMapping extends SrgMappingBase {
        
        public ParamMapping(String SRG, @Nullable String owner) {
            super(MappingType.PARAM, "", SRG, owner);
        }
        
    }
    
    public static class MethodMapping extends SrgMappingBase {
        
        @Getter
        private final String notchDesc, srgDesc;
        
        public MethodMapping(String notch, String notchDesc, String SRG, String srgDesc, @Nullable String owner) {
            super(MappingType.METHOD, notch, SRG, owner);
            this.notchDesc = notchDesc;
            this.srgDesc = srgDesc;
        }
        
    }

    public ISrgMapping create(MappingType type, String line) {
        String[] data = NullHelper.notnullJ(line.trim().split("\\s+"), "String#split");
        switch(type) {
            case CLASS:
                return new ClassMapping(data[0], data[1]);
            case FIELD:
                String owner = data[1].substring(0, data[1].lastIndexOf('/'));
                return new FieldMapping(
                        NullHelper.notnullJ(data[0].substring(data[0].lastIndexOf('/') + 1), "String#substring"), 
                        NullHelper.notnullJ(data[1].replace(owner + "/", ""), "String#replcae"), 
                        owner);
            case METHOD:
                owner = data[2].substring(0, data[2].lastIndexOf('/'));
                return new MethodMapping(
                        NullHelper.notnullJ(data[0].substring(data[0].lastIndexOf('/') + 1), "String#substring"), 
                        data[1], 
                        NullHelper.notnullJ(data[2].replace(owner + "/", ""), "String#replace"), 
                        data[3], 
                        owner);
            default:
                return null;
        }
    }

}
