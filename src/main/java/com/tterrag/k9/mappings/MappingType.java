package com.tterrag.k9.mappings;

import com.tterrag.k9.util.Nullable;

import lombok.Getter;

@Getter
public enum MappingType {
    CLASS('c', "CL", null, null),
    FIELD('f', "FD", "fields", CLASS),
    METHOD('m', "MD", "methods", CLASS),
    PARAM('p', null, "params", METHOD),
    ;
    
    private final char key;
    private final @Nullable String srgKey;
    private final @Nullable @Getter(onMethod = @__({@Nullable})) String csvName;
    private final @Nullable MappingType parent;
    
    private MappingType(char key, @Nullable String srgKey, @Nullable String csvName, @Nullable MappingType parent) {
        this.key = key;
        this.srgKey = srgKey;
        this.csvName = csvName;
        this.parent = parent;
    }
}