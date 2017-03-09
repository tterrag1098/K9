package com.blamejared.mcbot.mcp;

import java.util.Map;
import java.util.Set;

import gnu.trove.list.array.TIntArrayList;

public class VersionJson {
    
    public static class MappingsJson {
        
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
    
    private Map<String, MappingsJson> versionToList;

    public VersionJson(Map<String, MappingsJson> data) {
        this.versionToList = data;
    }

    public MappingsJson getMappings(String mcversion) {
        return versionToList.get(mcversion);
    }
    
    public Set<String> getVersions() {
        return versionToList.keySet();
    }
}
