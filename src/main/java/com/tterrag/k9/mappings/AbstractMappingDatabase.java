package com.tterrag.k9.mappings;

import java.io.IOException;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Table;
import com.google.common.collect.Tables;
import com.tterrag.k9.util.NullHelper;
import com.tterrag.k9.util.Patterns;
import com.tterrag.k9.util.annotation.NonNull;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public abstract class AbstractMappingDatabase<@NonNull T extends Mapping> implements MappingDatabase<T> {
    
    @Getter
    private final String minecraftVersion;
    
    private final Table<NameType, MappingType, ListMultimap<String, @NonNull T>> mappings = Tables.newCustomTable(new EnumMap<>(NameType.class), () -> new EnumMap<>(MappingType.class));

    protected abstract List<T> parseMappings() throws NoSuchVersionException, IOException;
    
    private void removeFromAll(T mapping) {
        for (NameType t : NameType.values()) {
            String name = t.get(mapping);
            if (name != null) {
                getTable(t, mapping.getType()).remove(name, mapping);
            }
        }
    }
    
    private void addToAll(T mapping) {
        for (NameType t : NameType.values()) {
            String name = t.get(mapping);
            if (name != null) {
                getTable(t, mapping.getType()).put(name, mapping);
            }
        }
    }
    
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
                addToAll(mapping);
                return true;
            } else {
                return false;
            }
        } else { // Otherwise just replace it
            addToAll(mapping);
            return true;
        }
    }
    
    protected ListMultimap<String, T> getTable(NameType by, MappingType type) {
        return NullHelper.notnullJ(mappings.row(by).computeIfAbsent(type, t -> ArrayListMultimap.create()), "Map#computeIfAbsent");
    }
    
    @Override
    public MappingDatabase<T> reload() throws IOException, NoSuchVersionException {
        parseMappings().forEach(this::addMapping);
        return this;
    }
    
    protected Collection<T> fuzzyLookup(NameType by, MappingType type, String search) {
        if (type == MappingType.CLASS && !Patterns.NOTCH_PARAM.matcher(search).matches()) {
            return getTable(by, type).values().stream().filter(m -> by.get(m) != null && by.get(m).endsWith(search)).collect(Collectors.toList());
        }
        return getTable(by, type).get(search);
    }
    
    @Override
    public Collection<T> lookup(NameType by, MappingType type) {
        return NullHelper.notnullL(getTable(by, type).values(), "Multimap#values");
    }
    
    @Override
    public Collection<T> lookup(NameType by, MappingType type, String search) {
        int lastDot = search.lastIndexOf('.');
        if (lastDot == -1) {
            return fuzzyLookup(by, type, search);
        } else if (type == MappingType.CLASS) {
            return fuzzyLookup(by, type, search.replace('.', '/'));
        }
        String ownerMatch = search.substring(0, lastDot);
        String name = search.substring(lastDot + 1);
        return fuzzyLookup(by, type, name).stream().filter(m -> m.getOwner() != null).filter(m -> m.getOwner().endsWith(ownerMatch)).collect(Collectors.toList());
    }
}
