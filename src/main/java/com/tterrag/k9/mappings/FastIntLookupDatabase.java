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
        return getIntKey(mapping.getIntermediate());
    }
    
    protected OptionalInt getIntKey(String name) {
        String[] byUnderscores = name.split("_");
        try {
            return OptionalInt.of(Integer.parseInt(byUnderscores.length > 1 ? byUnderscores[1] : name));
        } catch (NumberFormatException e) {}
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
    
    protected Collection<T> fastLookup(MappingType type, String search) {
        OptionalInt id = getIntKey(search);
        if (id.isPresent()) {
            // Fast track int lookups to a constant-time path for params
            Multimap<Integer, T> table = idFastLookup.get(type);
            if (table != null) {
                return NullHelper.notnullL(table.get(id.getAsInt()), "Multimap#get");
            }
        }
        return Collections.emptyList();
    }
    
    @Override
    public Collection<T> lookup(NameType by, MappingType type, String search) {
        if (by == NameType.INTERMEDIATE) {
            Collection<T> fast = fastLookup(type, search);
            if (!fast.isEmpty()) {
                return fast;
            }
        }
        return super.lookup(by, type, search);
    }
}
