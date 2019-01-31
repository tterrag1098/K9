package com.tterrag.k9.mappings.yarn;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;

import org.apache.commons.io.FileUtils;

import com.google.common.base.Preconditions;
import com.google.common.reflect.TypeToken;
import com.google.gson.GsonBuilder;
import com.tterrag.k9.mappings.DeserializeTIntArrayList;
import com.tterrag.k9.mappings.MappingDownloader;
import com.tterrag.k9.util.Patterns;

import gnu.trove.list.array.TIntArrayList;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class YarnDownloader extends MappingDownloader<TinyMapping, YarnDatabase> {

    public static final YarnDownloader INSTANCE = new YarnDownloader();

    private static final int DATA_VERSION = 1;
    
    private static final String MANIFEST_URL = "https://maven.fabricmc.net/net/fabricmc/yarn/versions.json";
    private static final String MAVEN_PATTERN = "https://maven.fabricmc.net/net/fabricmc/yarn/%1$s.%2$s/yarn-%1$s.%2$s-tiny.gz";
    
    private LinkedHashMap<String, TIntArrayList> versions = new LinkedHashMap<>();

    private YarnDownloader() {
        super("yarn", YarnDatabase::new, DATA_VERSION);
    }
    
    @Override
    protected void collectParsers(GsonBuilder builder) {
        super.collectParsers(builder);
        DeserializeTIntArrayList.register(builder);
    }

    @Override
    protected void checkUpdates() {
        try {
            log.info("Running Yarn update check...");
            
            URL url = new URL(MANIFEST_URL);
            HttpURLConnection request = (HttpURLConnection) url.openConnection();
            request.connect();
    
            versions = getGson().fromJson(new InputStreamReader(request.getInputStream()), new TypeToken<LinkedHashMap<String, TIntArrayList>>(){}.getType());
            
            for (Entry<String, TIntArrayList> e : versions.entrySet()) {
                String mcver = e.getKey();
                TIntArrayList versions = e.getValue();
                
                File versionFolder = getDataFolder().resolve(mcver).toFile();
                if (!versionFolder.exists()) {
                    versionFolder.mkdir();
                }
                
                log.info("Updating Yarn data for for MC {}", mcver);
              
                if (versions == null) continue;
                
                int mappingVersion = versions.max();
                String mappingsUrl = String.format(MAVEN_PATTERN, mcver, mappingVersion);
                url = new URL(mappingsUrl);
                
                File[] folderContents = versionFolder.listFiles();
                if (folderContents.length > 0) {
                    int currentVersion = getCurrentVersion(folderContents[0]);
                    if (currentVersion == mappingVersion) {
                        log.debug("Yarn {} mappings up to date: {} == {}", mcver, mappingVersion, currentVersion);
                        continue;
                    } else {
                        folderContents[0].delete();
                    }
                }
                
                log.info("Found out of date or missing yarn mappings for MC {}. New version: {}", mcver, mappingVersion);
                String filename = mappingsUrl.substring(mappingsUrl.lastIndexOf('/') + 1);
                FileUtils.copyURLToFile(url, versionFolder.toPath().resolve(filename).toFile());
                remove(mcver);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
    public Set<String> getMinecraftVersions() {
        return versions.keySet();
    }
    
    @Override
    public String getLatestMinecraftVersion() {
        List<String> versionNames = new ArrayList<>(versions.keySet());
        return versionNames.get(versionNames.size() - 1);
    }
    
    public Map<String, TIntArrayList> getIndexedVersions() {
        return versions;
    }
}
