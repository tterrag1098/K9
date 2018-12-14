package com.tterrag.k9.mappings;

import java.io.IOException;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.tterrag.k9.util.NonNull;
import com.tterrag.k9.util.NullHelper;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public abstract class AbstractMappingDatabase<T extends Mapping> implements MappingDatabase<T> {
    
    @Getter
    private final String minecraftVersion;
    
    private final Map<MappingType, ListMultimap<String, @NonNull T>> mappings = new EnumMap<>(MappingType.class);
        
    protected abstract List<T> parseMappings() throws NoSuchVersionException, IOException;
    
    protected boolean addMapping(T mapping) {
        Multimap<String, T> table = getTable(mapping.getType());
        String name = mapping.getName();
        String key = mapping.getIntermediate();
        
        // This is an entirely new intermediate, add it no matter what
        if (name == null || !table.containsKey(key)) {
            return table.put(key, mapping);
        }
        
        // Find any existing duplicate
        Optional<T> existing = table.get(key).stream().filter(m -> name.equals(m.getName())).findFirst();
        if (existing.isPresent()) { // If there is a duplicate it, replace it only if the new mapping's owner is extant and shorter than the existing one
            T old = existing.get();
            table.remove(key, old);
            String owner = old.getOwner();
            String newOwner = mapping.getOwner();
            if (owner != null && newOwner != null && newOwner.length() < owner.length()) {
                return table.put(key, mapping);
            } else {
                return table.put(key, old);
            }
        } else { // Otherwise just replace it
            return table.put(key, mapping);
        }
    }
    
    protected ListMultimap<String, T> getTable(MappingType type) {
        return NullHelper.notnullJ(mappings.computeIfAbsent(type, t -> ArrayListMultimap.create()), "Map#computeIfAbsent");
    }
    
    @Override
    public void reload() throws IOException, NoSuchVersionException {
        parseMappings().forEach(this::addMapping);
    }
    
    @Override
    public Collection<@NonNull T> lookup(MappingType type) {
        return NullHelper.notnullL(getTable(type).values(), "Multimap#values");
    }

    @Override
    public Collection<@NonNull T> lookup(MappingType type, String search) {
        return NullHelper.notnullL(getTable(type).get(search), "Multimap#get");
    }
}
