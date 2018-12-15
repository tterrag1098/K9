package com.tterrag.k9.mappings;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.OptionalInt;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.tterrag.k9.util.NonNull;
import com.tterrag.k9.util.NullHelper;

public abstract class FastIntLookupDatabase<T extends Mapping> extends AbstractMappingDatabase<T> {

    private final Map<MappingType, Multimap<Integer, T>> idFastLookup = new EnumMap<>(MappingType.class);
    
    protected FastIntLookupDatabase(String minecraftVersion) {
        super(minecraftVersion);
    }
    
    protected OptionalInt getIntKey(T mapping) {
        String[] byUnderscores = mapping.getIntermediate().split("_");
        if (byUnderscores.length > 1) {
            try {
                return OptionalInt.of(Integer.parseInt(byUnderscores[1]));
            } catch (NumberFormatException e) {}
        }
        return OptionalInt.empty();
    }
    
    @Override
    protected boolean addMapping(T mapping) {
        if (super.addMapping(mapping)) {
            getIntKey(mapping).ifPresent(key -> idFastLookup.computeIfAbsent(mapping.getType(), $ -> HashMultimap.create()).put(key, mapping));
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
