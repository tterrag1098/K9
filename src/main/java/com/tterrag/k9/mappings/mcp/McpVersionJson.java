package com.tterrag.k9.mappings.mcp;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.tterrag.k9.util.NullHelper;
import com.tterrag.k9.util.annotation.NonNull;

import it.unimi.dsi.fastutil.ints.IntArrayList;

public class McpVersionJson {
    
    public static class McpMappingsJson {
        
        private IntArrayList snapshot;
        private IntArrayList stable;
        
        public boolean hasSnapshot(String version) {
            return hasSnapshot(Integer.valueOf(version));
        }
        
        public boolean hasSnapshot(int version) {
            return snapshot.contains(version);
        }
        
        public int latestSnapshot() {
            return snapshot.getInt(0);
        }
        
        public boolean hasStable(String version) {
            return hasStable(Integer.valueOf(version));
        }
        
        public boolean hasStable(int version) {
            return stable.contains(version);
        }
        
        public int latestStable() {
            return stable.size() > 0 ? stable.getInt(0) : -1;
        }
    }
    
    private static final Set<String> INVALID_VERSIONS = ImmutableSet.of("1.14.4", "1.15", "1.15.2", "1.16");
    
    private final TreeMap<@NonNull String, McpMappingsJson> versionToList;

    public McpVersionJson(Map<String, McpMappingsJson> data) {
        this.versionToList = Maps.newTreeMap(McpVersionJson::compareVersions);
        this.versionToList.putAll(data);
        INVALID_VERSIONS.forEach(versionToList::remove);
    }
    
    private static int compareVersions(String version1, String version2) {
        int[] v1 = toVersionNumbers(version1);
        int[] v2 = toVersionNumbers(version2);
        for (int i = 0; i < v1.length && i < v2.length; i++) {
            if (v2[i] > v1[i]) {
                return -1;
            } else if (v2[i] < v1[i]) {
                return 1;
            }
        }
        return v1.length - v2.length;
    }
    
    private static int @NonNull[] toVersionNumbers(String version) {
        return Arrays.stream(version.split("\\.")).mapToInt(Integer::parseInt).toArray();
    }

    public Optional<McpMappingsJson> getMappings(String mcversion) {
        return Optional.ofNullable(versionToList.get(mcversion));
    }
    
    public @NonNull Set<@NonNull String> getVersions() {
        return NullHelper.notnullJ(versionToList.descendingKeySet(), "TreeMap#descendingKeySet");
    }
    
    public @NonNull String getLatestVersion(boolean stable) {
        return versionToList.descendingMap().entrySet().stream()
                .filter(e -> !stable || e.getValue().latestStable() != -1)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow(IllegalStateException::new);
    }
    
    McpVersionJson mergeWith(McpVersionJson other) {
        for (Map.Entry<String, McpMappingsJson> version : other.versionToList.entrySet()) {
            McpMappingsJson mappings = getMappings(version.getKey()).orElse(null);
            if (mappings != null) {
                return this; // Not merging within a version
            } else {
                versionToList.put(version.getKey(), version.getValue());
            }
        }
        return this;
    }
}
