package com.tterrag.k9.mappings.mcp;

import com.tterrag.k9.mappings.MappingDatabase;
import com.tterrag.k9.mappings.MappingType;
import com.tterrag.k9.mappings.NameType;
import com.tterrag.k9.mappings.SignatureHelper;
import com.tterrag.k9.util.annotation.Nullable;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.NonFinal;

@Value
@NonFinal
@RequiredArgsConstructor
public class SrgMapping implements IntermediateMapping {
    
    private static final SignatureHelper sigHelper = new SignatureHelper();
    
    transient MappingDatabase<? extends SrgMapping> db;
    
    @Getter(onMethod = @__(@Override))
    MappingType type;
    
    @Getter(onMethod = @__(@Override))
    String original;
    
    @Getter(onMethod = @__(@Override))
    String intermediate;
    
    @Nullable String originalDesc;
    
    @EqualsAndHashCode.Exclude
    @Nullable String intermediateDesc;
    
    @Getter(onMethod = @__(@Override))
    String owner;
    
    @Getter(onMethod = @__(@Override))
    @Setter // Temporary, used for tsrgv2 parsing
    @NonFinal
    boolean isStatic;

    transient volatile @NonFinal String originalOwner;
    
    @Override
    public @Nullable String getOwner(NameType name) {
        if (name == NameType.ORIGINAL) {
            if (originalOwner == null) {
                originalOwner = sigHelper.mapType(NameType.ORIGINAL, getOwner(), this, db).getInternalName();
            }
            return originalOwner;
        }
        return getOwner();
    }
    
    @Override
    public @Nullable String getDesc() { return getIntermediateDesc(); }
    
    @Override
    public @Nullable String getDesc(NameType name) {
        switch(name) {
            case INTERMEDIATE:
                return getDesc();
            case ORIGINAL:
                return originalDesc;
            case NAME:
            default:
                throw new IllegalArgumentException("Invalid name type");
        }
    }
    
    @Override
    public String formatMessage(String mcver) {
        return "";
    }
}
