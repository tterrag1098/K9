package com.tterrag.k9.mappings.mcp;

import java.util.Optional;

import com.google.common.collect.Multimap;
import com.tterrag.k9.mappings.FastIntLookupDatabase;
import com.tterrag.k9.mappings.Mapping;
import com.tterrag.k9.mappings.NameType;

public abstract class OverrideRemovingDatabase<T extends Mapping> extends FastIntLookupDatabase<T> {
    
    protected OverrideRemovingDatabase(String minecraftVersion) {
        super(minecraftVersion);
    }

    @Override
    protected boolean addMapping(T mapping) {
        Multimap<String, T> table = getTable(NameType.INTERMEDIATE, mapping.getType());
        String name = mapping.getName();
        String key = mapping.getIntermediate();
        
        // This is an entirely new intermediate, add it no matter what
        if (name == null || !table.containsKey(key)) {
            addToAll(mapping);
            return true;
        }
        
        // Find any existing duplicate
        Optional<T> existing = table.get(key).stream().filter(m -> name.equals(m.getName())).findFirst();
        if (existing.isPresent()) { // If there is a duplicate, replace it only if the new mapping's owner is extant and shorter than the existing one
            T old = existing.get();
            String owner = old.getOwner();
            String newOwner = mapping.getOwner();
            if (owner != null && newOwner != null && newOwner.length() < owner.length()) {
                removeFromAll(old);
                return super.addMapping(mapping);
            } else {
                return false;
            }
        } else { // Otherwise just replace it
            return super.addMapping(mapping);
        }
    }
}
