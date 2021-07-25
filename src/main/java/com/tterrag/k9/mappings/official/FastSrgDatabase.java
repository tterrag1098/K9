package com.tterrag.k9.mappings.official;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.tterrag.k9.mappings.MappingDownloader;
import com.tterrag.k9.mappings.MappingType;
import com.tterrag.k9.mappings.NameType;
import com.tterrag.k9.mappings.NoSuchVersionException;
import com.tterrag.k9.mappings.srg.SrgDatabase;
import com.tterrag.k9.mappings.srg.SrgMapping;

public class FastSrgDatabase extends SrgDatabase {

    private final Map<String, SrgMapping> classMap = new HashMap<>();
    private final ListMultimap<String, SrgMapping> childrenMap = ArrayListMultimap.create();

    public FastSrgDatabase(String mcver) throws NoSuchVersionException {
        super(mcver);
    }

    @Override
    protected boolean addMapping(SrgMapping mapping) {
        if (mapping.getType() == MappingType.CLASS) {
            classMap.put(mapping.getOriginal(), mapping);
        } else {
            childrenMap.put(mapping.getOwner(NameType.ORIGINAL), mapping);
        }
        return super.addMapping(mapping);
    }

    public SrgMapping getClassMapping(String original) {
        return classMap.get(original);
    }

    public List<SrgMapping> getChildren(String owner) {
        return childrenMap.get(owner);
    }
}
