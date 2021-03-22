package com.tterrag.k9.mappings.official;

import com.google.gson.Gson;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.net.URL;
import java.util.Arrays;
import java.util.Set;
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
    public static class VersionInfo {
        private String id;
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

    public Set<VersionInfo> getAllVersions() {
        return Arrays.stream(versions).collect(Collectors.toSet());
    }

    public Set<VersionInfo> getReleaseVersions() {
        return Arrays.stream(versions).filter(VersionInfo::isRelease).collect(Collectors.toSet());
    }

    public Set<VersionInfo> getSnapshotVersions() {
        return Arrays.stream(versions).filter(VersionInfo::isSnapshot).collect(Collectors.toSet());
    }
}
