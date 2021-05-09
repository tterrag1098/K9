package com.tterrag.k9.mappings.official;

import com.google.gson.Gson;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Setter;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Setter(AccessLevel.NONE)
public class ManifestJson {
    public LatestInfo latest;
    public VersionInfo[] versions;

    @Data
    @Setter(AccessLevel.NONE)
    public static class LatestInfo {
        private String release;
        private String snapshot;
    }

    @Data
    @Setter(AccessLevel.NONE)
    @EqualsAndHashCode(onlyExplicitlyIncluded = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VersionInfo {
        @EqualsAndHashCode.Include
        private String id;
        @EqualsAndHashCode.Include
        private String type;
        private URL url;

        public boolean isRelease() {
            return "release".equals(type);
        }

        public boolean isSnapshot() {
            return "snapshot".equals(type);
        }

        public Mono<VersionJson> getJson(Gson gson) {
            return HttpClient.create()
                    .get()
                    .uri(url.toString())
                    .responseSingle(($, content) -> content.asString()
                            .map(s -> gson.fromJson(s, VersionJson.class)));
        }
    }

    public VersionInfo getVersionInfo(String versionId) {
        for (VersionInfo info : versions) {
            if (info.id.equals(versionId))
                return info;
        }
        return null;
    }

    public List<String> getAllVersions() {
        return Arrays.stream(versions).map(VersionInfo::getId).collect(Collectors.toList());
    }

    public List<String> getReleaseVersions() {
        return Arrays.stream(versions).filter(VersionInfo::isRelease).map(VersionInfo::getId).collect(Collectors.toList());
    }

    public List<String> getSnapshotVersions() {
        return Arrays.stream(versions).filter(VersionInfo::isSnapshot).map(VersionInfo::getId).collect(Collectors.toList());
    }
}
