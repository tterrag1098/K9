package com.tterrag.k9.mappings.yarn;

import lombok.Value;

@Value
public class MappingsVersion {
    
    MinecraftVersion gameVersion;
    String separator;
    int build;
    String maven;
    String version;
        
    public String getMavenUrl(String base, String classifier, String ext) {
        String[] maven = getMaven().split(":");
        return base + maven[0].replace(".", "/") + "/" + maven[1] + "/" + maven[2] + "/yarn-" + maven[2] + "-" + classifier + "." + ext; 
    }
}
