package com.tterrag.k9.mappings.official;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;

import java.net.URL;
import java.util.Map;

@Data
@Setter(AccessLevel.NONE)
public class VersionJson {
    private Map<String, Download> downloads;

    @Data
    @Setter(AccessLevel.NONE)
    public static class Download {
        public String sha1;
        public int size;
        public URL url;
    }
}
