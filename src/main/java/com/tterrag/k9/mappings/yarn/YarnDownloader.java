package com.tterrag.k9.mappings.yarn;

import java.io.File;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;

import com.google.common.base.Preconditions;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.tterrag.k9.mappings.MappingDownloader;
import com.tterrag.k9.mappings.NoSuchVersionException;
import com.tterrag.k9.util.Patterns;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
public class YarnDownloader extends MappingDownloader<TinyMapping, YarnDatabase> {

    public static final YarnDownloader INSTANCE = new YarnDownloader();

    private static final int DATA_VERSION = 1;
    
    private static final String META_URL_BASE = "https://meta.fabricmc.net/v1/";
    private static final String ENDPOINT_GAME_VERSIONS = "versions/game/";
    private static final String ENDPOINT_YARN_VERSIONS = "versions/mappings/";
    
    private static final String MAVEN_URL_BASE = "https://maven.fabricmc.net/";

    private List<MinecraftVersion> mcVersions = new ArrayList<>();
    private Map<String, List<MappingsVersion>> versions = new LinkedHashMap<>();

    private YarnDownloader() {
        super("yarn", YarnDatabase::new, DATA_VERSION);
    }
    
    @Override
    protected void collectParsers(GsonBuilder builder) {
        super.collectParsers(builder);
        builder.registerTypeAdapter(MinecraftVersion.class, new JsonDeserializer<MinecraftVersion>() {
            @Override
            public MinecraftVersion deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
                if (json.isJsonPrimitive()) {
                    return new MinecraftVersion(json.getAsString(), false);
                } else {
                    JsonObject obj = json.getAsJsonObject();
                    return new MinecraftVersion(obj.get("version").getAsString(), obj.get("stable").getAsBoolean());
                }
            }
        });
    }
    
    @Override
    protected Mono<Void> updateVersions() {
        return Mono.fromCallable(() -> {
            log.info("Running Yarn update check...");
            
            URL url = new URL(META_URL_BASE + ENDPOINT_GAME_VERSIONS);
            HttpURLConnection request = (HttpURLConnection) url.openConnection();
            request.connect();
    
            mcVersions = getGson().fromJson(new InputStreamReader(request.getInputStream()), new TypeToken<List<MinecraftVersion>>(){}.getType());
            return null;
        });
    }

    @Override
    protected Mono<Void> checkUpdates(String mcver) {
        return updateVersions().then(Mono.fromSupplier(() -> this.mcVersions)).flatMap(mcVersions -> Mono.fromCallable(() -> {
            MinecraftVersion mc = mcVersions.stream().filter(m -> m.getVersion().equals(mcver)).findFirst().orElseThrow(() -> new NoSuchVersionException("yarn", mcver));

                URL url = new URL(META_URL_BASE + ENDPOINT_YARN_VERSIONS + mc.getVersionEncoded() + "/");
                HttpURLConnection request = (HttpURLConnection) url.openConnection();
                request.connect();
                List<MappingsVersion> versions = getGson().fromJson(new InputStreamReader(request.getInputStream()), new TypeToken<List<MappingsVersion>>(){}.getType());
                versions.sort(Comparator.comparingInt(MappingsVersion::getBuild).reversed());
                this.versions.put(mcver, versions);
                
                File versionFolder = getDataFolder().resolve(mcver).toFile();
                if (!versionFolder.exists()) {
                    versionFolder.mkdir();
                }
                
                log.info("Updating Yarn data for for MC {}", mcver);
              
                if (versions.isEmpty()) throw new IllegalStateException("Found no yarn versions for MC " + mcver + "!");
                
                MappingsVersion mappingVersion = versions.get(0);
                String mappingsUrl = mappingVersion.getMavenUrl(MAVEN_URL_BASE, "mergedv2", "jar");
                url = new URL(mappingsUrl);
                if (((HttpURLConnection)url.openConnection()).getResponseCode() / 100 == 4) { // This version doesn't support v2
                    mappingsUrl = mappingVersion.getMavenUrl(MAVEN_URL_BASE, "tiny", "gz"); 
                    url = new URL(mappingsUrl);
                }
                
                File[] folderContents = versionFolder.listFiles();
                if (folderContents.length > 0) {
                    int currentVersion = getCurrentVersion(folderContents[0]);
                    if (currentVersion == mappingVersion.getBuild()) {
                        log.debug("Yarn {} mappings up to date: {} == {}", mcver, mappingVersion, currentVersion);
                        return null;
                    } else {
                        folderContents[0].delete();
                    }
                }
                
                log.info("Found out of date or missing yarn mappings for MC {}. New version: {}", mcver, mappingVersion);
                String filename = mappingsUrl.substring(mappingsUrl.lastIndexOf('/') + 1);
                FileUtils.copyURLToFile(url, versionFolder.toPath().resolve(filename).toFile());
                remove(mcver);
            return null;
        }));
    }
    
    private int getCurrentVersion(File file) {
        Matcher matcher = Patterns.YARN_TINY_FILENAME.matcher(file.getName());
        Preconditions.checkArgument(matcher.matches(), "Invalid file found in mappings folder: " + file.getName());
        String match = matcher.group(2);
        if (match == null) {
            match = matcher.group(1);
            match = match.substring(match.lastIndexOf('.') + 1);
        }
        return Integer.parseInt(match);
    }
    
    @Override
    protected Set<String> getMinecraftVersionsInternal() {
        return mcVersions.stream().map(mv -> mv.getVersion()).collect(Collectors.toSet());
    }
    
    @Override
    protected String getLatestMinecraftVersionInternal(boolean stable) {
        return mcVersions.stream()
                .filter(v -> !stable || v.isStable())
                .map(MinecraftVersion::getVersion)
                .findFirst()
                .orElse("Unknown");
    }
    
    public Map<String, List<MappingsVersion>> getIndexedVersions() {
        return versions;
    }
}
