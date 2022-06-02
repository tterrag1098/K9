package com.tterrag.k9.mappings.mcp;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

import com.tterrag.k9.mappings.srg.SrgDownloader;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.io.Files;
import com.google.common.reflect.TypeToken;
import com.google.gson.GsonBuilder;
import com.tterrag.k9.mappings.DeserializeIntArrayList;
import com.tterrag.k9.mappings.MappingDownloader;
import com.tterrag.k9.mappings.mcp.McpVersionJson.McpMappingsJson;
import com.tterrag.k9.util.Monos;
import com.tterrag.k9.util.Patterns;
import com.tterrag.k9.util.annotation.Nullable;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

@Slf4j
public class McpDownloader extends MappingDownloader<McpMapping, McpDatabase> {
    
    public static final McpDownloader INSTANCE = new McpDownloader();

    private static final String VERSION_JSON = "https://maven.minecraftforge.net/de/oceanlabs/mcp/versions.json";
    private static final String MAPPINGS_URL = "https://maven.minecraftforge.net/de/oceanlabs/mcp/%1$s/%2$d-%3$s/%1$s-%2$d-%3$s.zip";
    
    // Temporary 1.16 mappings data
    private static final String TEMP_VERSION_JSON = "https://assets.tterrag.com/temp_mappings.json";
    private static final String TEMP_MAPPINGS_URL = "https://maven.minecraftforge.net/de/oceanlabs/mcp/%1$s/%2$d-%3$s/%1$s-%2$d-%3$s.zip";
    
    private McpDownloader() {
        super("mcp", McpDatabase::new, 1);
    }

    @Nullable
    private McpVersionJson versions;
    
    @Override
    protected Set<String> getMinecraftVersionsInternal() {
        return versions == null ? Collections.emptySet() : versions.getVersions();
    }
    
    @Override
    protected String getLatestMinecraftVersionInternal(boolean stable) {
        return versions == null ? "Unknown" : versions.getLatestVersion(stable);
    }
    
    @Override
    protected String getMappingsVersionInternal(String mcver, boolean stable) {
        McpMappingsJson json = versions.getMappings(mcver).orElse(null);
        if (stable && json != null && json.latestStable() == -1) {
            mcver = getLatestMinecraftVersionInternal(true);
            json = versions.getMappings(mcver).orElse(null);
        }

        if (json == null)
            return "Unknown";
        return (stable ? json.latestStable() : json.latestSnapshot()) + "-" + mcver;
    }

    @Override
    protected void collectParsers(GsonBuilder builder) {
        super.collectParsers(builder);
        DeserializeIntArrayList.register(builder);
    }

    private Mono<McpVersionJson> getVersions(String url) {
        return HttpClient.create()
            .get()
            .uri(url)
            .responseSingle(($, content) -> content.asString()
                    .map(s -> new McpVersionJson(getGson().fromJson(s, new TypeToken<Map<String, McpMappingsJson>>(){}.getType()))));
    }
    
    @Override
    protected Mono<Void> updateVersions() {
        return Mono.fromRunnable(() -> log.info("Running MCP update check..."))
                .then(getVersions(VERSION_JSON))
                .flatMap(vs -> getVersions(TEMP_VERSION_JSON).map(vs::mergeWith))
                .doOnNext(vs -> this.versions = vs)
                .then();
    }
    
    private int getMinVersion(String version) {
        String minversion = version.substring(version.indexOf('.') + 1, version.length());
        int seconddot = minversion.indexOf('.');
        if (seconddot != -1) {
            minversion = minversion.substring(0, seconddot);
        }
        return Integer.parseInt(minversion);
    }

    @Override
    protected Mono<Void> checkUpdates(String version) {
        return updateVersions().then(Mono.fromSupplier(() -> this.versions))
                .transform(Monos.mapOptional(vs -> vs.getMappings(version)))
                .flatMap(mappings -> SrgDownloader.INSTANCE.updateSrgs(version).thenReturn(mappings))
                .flatMap(mappings -> Mono.fromCallable(() -> {
                Path versionFolder = getDataFolder().resolve(version);

                // Download new CSVs if necessary
                File mappingsFolder = versionFolder.resolve("mappings").toFile();
                
                int mappingsVersion = mappings.latestStable() < 0 ? mappings.latestSnapshot() : mappings.latestStable();
                String mappingsChannel = mappings.latestStable() < 0 ? "mcp_snapshot" : "mcp_stable";
                String mappingsUrl = String.format(getMinVersion(version) == 16 ? TEMP_MAPPINGS_URL : MAPPINGS_URL, mappingsChannel, mappingsVersion, version);
                URL url = new URL(mappingsUrl);
    
                if (!mappingsFolder.exists()) {
                    mappingsFolder.mkdirs();
                }
                
                File[] folderContents = mappingsFolder.listFiles();
                if (folderContents != null && folderContents.length > 0) {
                    int currentVersion = getCurrentVersion(folderContents[0]);
                    if (currentVersion == mappingsVersion) {
                        log.debug("MCP MC {} mappings up to date: {} == {}", version, mappingsVersion, currentVersion);
                        return null;
                    } else {
                        folderContents[0].delete();
                    }
                }
                
                log.info("Found out of date or missing MCP mappings for MC {}. New version: {}", version, mappingsVersion);
                String filename = mappingsUrl.substring(mappingsUrl.lastIndexOf('/') + 1);
                FileUtils.copyURLToFile(url, mappingsFolder.toPath().resolve(filename).toFile());
                remove(version);
                
                return null;
            }));
    }

    private int getCurrentVersion(File zipFile) throws IOException {
        Matcher matcher = Patterns.MAPPINGS_FILENAME.matcher(zipFile.getName());
        Preconditions.checkArgument(matcher.matches(), "Invalid file found in mappings folder: " + zipFile.getName());
        return Integer.parseInt(matcher.group(1));
    }

    public Mono<McpVersionJson> getVersions() {
        return updateVersionsIfRequired().then(Mono.justOrEmpty(versions));
    }
}
