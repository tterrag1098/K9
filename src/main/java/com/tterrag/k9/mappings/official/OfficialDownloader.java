package com.tterrag.k9.mappings.official;

import com.beust.jcommander.internal.Lists;
import com.tterrag.k9.mappings.MappingDownloader;
import com.tterrag.k9.util.annotation.Nullable;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
public class OfficialDownloader extends MappingDownloader<OfficialMapping, OfficialDatabase> {
    public static final OfficialDownloader INSTANCE = new OfficialDownloader();
    private static final String MANIFEST_JSON = "https://launchermeta.mojang.com/mc/game/version_manifest.json";

    private OfficialDownloader() {
        super("official", OfficialDatabase::new, 1);
    }

    @Nullable
    @Getter
    private ManifestJson manifest;

    @Override
    protected Set<String> getMinecraftVersionsInternal() {
        List<String> releases = manifest.getReleaseVersions();
        releases = releases.subList(0, releases.indexOf("1.14.4") + 1);
        List<String> snapshots = manifest.getSnapshotVersions();
        snapshots = snapshots.subList(0, snapshots.indexOf("19w36a") + 1);

        Set<String> versions = new HashSet<>(releases);
        versions.addAll(snapshots);
        return versions;
    }

    @Override
    protected String getLatestMinecraftVersionInternal(boolean stable) {
        return stable ? manifest.getLatest().getRelease() : manifest.getLatest().getSnapshot();
    }

    private Mono<ManifestJson> getManifestJson() {
        return HttpClient.create()
                .get()
                .uri(MANIFEST_JSON)
                .responseSingle(($, content) -> content.asString()
                        .map(s -> getGson().fromJson(s, ManifestJson.class)));
    }

    @Override
    protected Mono<Void> updateVersions() {
        return Mono.fromRunnable(() -> log.info("Running Official mappings update check..."))
                .then(this.getManifestJson())
                .doOnNext(m -> this.manifest = m)
                .then();
    }

    @Override
    protected Mono<Void> checkUpdates(String version) {
        return updateVersions().then(Mono.fromSupplier(() -> this.manifest))
                .filter(m -> this.getMinecraftVersionsInternal().contains(version))
                .flatMap(m -> Mono.justOrEmpty(m.getVersionInfo(version)))
                .flatMap(m -> m.getJson(getGson()))
                .flatMap(versionJson -> Mono.fromCallable(() -> {
                    Path versionFolder = getDataFolder().resolve(version);
                    File mappingsFolder = versionFolder.resolve("mappings").toFile();
                    URL clientUrl = versionJson.getDownloads().get("client_mappings").getUrl();
                    URL serverUrl = versionJson.getDownloads().get("server_mappings").getUrl();
                    List<URL> mappingUrls = Lists.newArrayList(clientUrl, serverUrl);

                    if (!mappingsFolder.exists()) {
                        mappingsFolder.mkdirs();
                    }

                    File[] folderContents = mappingsFolder.listFiles();
                    if (folderContents != null && folderContents.length > 0) {
                        log.debug("Official MC {} mappings up to date", version);
                        return null;
                    }

                    log.info("Found missing Official mappings for MC {}. Downloading.", version);
                    for (URL mappingsUrl : mappingUrls) {
                        String filename = mappingsUrl.getPath().substring(mappingsUrl.getPath().lastIndexOf('/') + 1);
                        FileUtils.copyURLToFile(mappingsUrl, new File(mappingsFolder, filename));
                    }
                    remove(version);

                    return null;
                }));
    }
}
