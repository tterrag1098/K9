package com.tterrag.k9.mappings.mcp;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.google.common.collect.Maps;
import com.tterrag.k9.util.NonNull;
import com.tterrag.k9.util.NullHelper;
import com.tterrag.k9.util.Nullable;

import gnu.trove.list.array.TIntArrayList;

public class McpVersionJson {
    
    public static class McpMappingsJson {
        
        private TIntArrayList snapshot;
        private TIntArrayList stable;
        
        public boolean hasSnapshot(String version) {
            return hasSnapshot(Integer.valueOf(version));
        }
        
        public boolean hasSnapshot(int version) {
            return snapshot.contains(version);
        }
        
        public int latestSnapshot() {
            return snapshot.get(0);
        }
        
        public boolean hasStable(String version) {
            return hasStable(Integer.valueOf(version));
        }
        
        public boolean hasStable(int version) {
            return stable.contains(version);
        }
        
        public int latestStable() {
            return stable.size() > 0 ? stable.get(0) : -1;
        }
    }
    
    private final TreeMap<@NonNull String, McpMappingsJson> versionToList;

    public McpVersionJson(Map<String, McpMappingsJson> data) {
        this.versionToList = Maps.newTreeMap(McpVersionJson::compareVersions);
        this.versionToList.putAll(data);
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

    public @Nullable McpMappingsJson getMappings(String mcversion) {
        return versionToList.get(mcversion);
    }
    
    public @NonNull Set<@NonNull String> getVersions() {
        return NullHelper.notnullJ(versionToList.descendingKeySet(), "TreeMap#descendingKeySet");
    }
    
    public @NonNull String getLatestVersion() {
        return getVersions().iterator().next();
    }
}
