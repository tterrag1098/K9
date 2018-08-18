package com.tterrag.k9.mcp;

import com.tterrag.k9.mcp.ISrgMapping.MappingType;
import com.tterrag.k9.mcp.ISrgMapping.SrgMappingBase;
import com.tterrag.k9.util.DefaultNonNull;
import com.tterrag.k9.util.NonNull;
import com.tterrag.k9.util.NullHelper;
import com.tterrag.k9.util.Nullable;

import lombok.Getter;

public class SrgMappingFactory {
    
    @DefaultNonNull
    public static class FieldMapping extends SrgMappingBase {
        
        public FieldMapping(String notch, String SRG, @Nullable String owner) {
            super(MappingType.FIELD, notch, SRG, owner);
        }
    }
    
    @DefaultNonNull
    public static class ClassMapping extends SrgMappingBase {

        public ClassMapping(String notch, String SRG) {
            super(MappingType.CLASS, notch, SRG, null);
        }
        
        @Override
        public String toString() {
            return NullHelper.notnullJ(super.toString().replace("null: ", ""), "String#replace");
        }
    }
    
    @DefaultNonNull
    public static class ParamMapping extends SrgMappingBase {
        
        public ParamMapping(String SRG, @Nullable String owner) {
            super(MappingType.PARAM, "", SRG, owner);
        }
        
    }
    
    public static class MethodMapping extends SrgMappingBase {
        
        protected final @NonNull String notchDesc, srgDesc;
        
        public MethodMapping(@NonNull String notch, @NonNull String notchDesc, @NonNull String SRG, @NonNull String srgDesc, @Nullable String owner) {
            super(MappingType.METHOD, notch, SRG, owner);
            this.notchDesc = notchDesc;
            this.srgDesc = srgDesc;
        }
        
        @Override
        public String getDesc() {
            return srgDesc;
        }
    }

    public ISrgMapping create(MappingType type, String line) {
        @NonNull String[] data = NullHelper.notnullJ(line.trim().split("\\s+"), "String#split");
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
