package com.tterrag.k9.mappings.mcp;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.tterrag.k9.mappings.AbstractMappingDatabase;
import com.tterrag.k9.mappings.Mapping;
import com.tterrag.k9.mappings.MappingType;
import com.tterrag.k9.util.NonNull;
import com.tterrag.k9.util.NullHelper;


abstract class FastSrgLookupDatabase<T extends Mapping> extends AbstractMappingDatabase<T> {

    private final Map<MappingType, Multimap<Integer, T>> idFastLookup = new EnumMap<>(MappingType.class);
    
    FastSrgLookupDatabase(String minecraftVersion) {
        super(minecraftVersion);
    }
    
    @Override
    protected boolean addMapping(T mapping) {
        if (super.addMapping(mapping)) {
            String[] byUnderscores = mapping.getIntermediate().split("_");
            if (byUnderscores.length > 1) {
                try {
                    idFastLookup.computeIfAbsent(mapping.getType(), $ -> HashMultimap.create()).put(Integer.parseInt(byUnderscores[1]), mapping);
                } catch (NumberFormatException e) {}
            }
            return true;
        }
        return false;
    }

    protected Collection<@NonNull T> lookupFastSrg(MappingType type, String search) {
        try {
            // Fast track int lookups to a constant-time path for params
            int id = Integer.parseInt(search);
            Multimap<Integer, T> table = idFastLookup.get(type);
            if (table != null) {
                return NullHelper.notnullL(table.get(id), "Multimap#get");
            }
        } catch (NumberFormatException e) {}
        return Collections.emptyList();
    }
}
