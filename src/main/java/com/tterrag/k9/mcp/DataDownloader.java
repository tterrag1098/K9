package com.tterrag.k9.mcp;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.io.Files;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonParseException;
import com.tterrag.k9.mcp.ISrgMapping.MappingType;
import com.tterrag.k9.mcp.VersionJson.MappingsJson;
import com.tterrag.k9.util.Threads;

import gnu.trove.list.array.TIntArrayList;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public enum DataDownloader {
    
    INSTANCE;
    
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final String VERSION_JSON = "http://export.mcpbot.bspk.rs/versions.json";
    private static final String SRGS_URL = "http://files.minecraftforge.net/maven/de/oceanlabs/mcp/mcp/%1$s/mcp-%1$s-srg.zip";
    private static final String TSRGS_URL = "http://files.minecraftforge.net/maven/de/oceanlabs/mcp/mcp_config/%1$s/mcp_config-%1$s.zip";
    private static final String MAPPINGS_URL_SNAPSHOT = "http://export.mcpbot.bspk.rs/mcp_snapshot/%1$d-%2$s/mcp_snapshot-%1$d-%2$s.zip";
    private static final String MAPPINGS_URL_STABLE = "http://export.mcpbot.bspk.rs/mcp_stable/%1$d-%2$s/mcp_stable-%1$d-%2$s.zip";

    private static final Gson GSON = new GsonBuilder().registerTypeAdapter(TIntArrayList.class, (JsonDeserializer<TIntArrayList>) (json, typeOfT, context) -> {
        if (json.isJsonArray()) {
            TIntArrayList ret = new TIntArrayList();
            JsonArray versions = json.getAsJsonArray();
            versions.forEach(e -> ret.add(e.getAsInt()));
            return ret;
        }
        throw new JsonParseException("Could not parse TIntHashSet, was not array.");
    }).create();
    
    private final Path dataFolder = Paths.get(".", "data");

    @Getter
    private VersionJson versions;
    private final Map<String, SrgDatabase> srgTable = new HashMap<>();
    private final Map<String, MappingDatabase> mappingTable = new HashMap<>();

    private class UpdateCheckTask implements Runnable {

        @SuppressWarnings("serial")
        @Override
        public void run() {
            try {
            	log.info("Running update check...");
            	
                URL url = new URL(VERSION_JSON);
                HttpURLConnection request = (HttpURLConnection) url.openConnection();
                request.connect();

                versions = new VersionJson(GSON.fromJson(new InputStreamReader(request.getInputStream()), new TypeToken<Map<String, MappingsJson>>(){}.getType()));
                
                for (String version : versions.getVersions()) {
                   
                    Path versionFolder = dataFolder.resolve(version);
                    
                    String minversion = version.substring(version.indexOf('.') + 1, version.length());
                    int seconddot = minversion.indexOf('.');
                    if (seconddot != -1) {
                        minversion = minversion.substring(0, seconddot);
                    }
                    
                    String urlpattern = SRGS_URL;
                    if (Integer.parseInt(minversion) >= 13) {
                        urlpattern = TSRGS_URL;
                    }
                    
                    log.info("Updating MCP data for for MC {}", version);
                    
                    // Download new SRGs if necessary
                    Path srgsFolder = versionFolder.resolve("srgs");
                    String srgsUrl = String.format(urlpattern, version);
                    url = new URL(srgsUrl);

                    String filename = srgsUrl.substring(srgsUrl.lastIndexOf('/') + 1);
                    File md5File = srgsFolder.resolve(filename + ".md5").toFile();
                    File zipFile = srgsFolder.resolve(filename).toFile();

                    boolean srgsUpToDate = false;
                    String md5 = IOUtils.toString(new URL(srgsUrl + ".md5").openStream(), Charsets.UTF_8);
                    if (md5File.exists() && zipFile.exists()) {
                        String localMd5 = Files.readFirstLine(md5File, Charsets.UTF_8);
                        if (md5.equals(localMd5)) {
                            log.debug("MC {} SRGs up to date: {} == {}", version, md5, localMd5);
                            srgsUpToDate = true;
                        }
                    }

                    if (!srgsUpToDate) {
                        log.info("Found out of date or missing SRGS for MC {}. new MD5: {}", version, md5);
                        FileUtils.copyURLToFile(url, zipFile);
                        FileUtils.write(md5File, md5, Charsets.UTF_8);
                    }
                
                    // Download new CSVs if necessary
                    File mappingsFolder = versionFolder.resolve("mappings").toFile();

                    MappingsJson mappings = versions.getMappings(version);
                    if (mappings == null) continue;
                    
                    int mappingVersion = mappings.latestStable() < 0 ? mappings.latestSnapshot() : mappings.latestStable();
                    String mappingsUrl = String.format(mappings.latestStable() < 0 ? MAPPINGS_URL_SNAPSHOT : MAPPINGS_URL_STABLE, mappingVersion, version);
                    url = new URL(mappingsUrl);
        
                    if (!mappingsFolder.exists()) {
                        mappingsFolder.mkdir();
                    }
                    
                    File[] folderContents = mappingsFolder.listFiles();
                    if (folderContents.length > 0) {
                        int currentVersion = getCurrentVersion(folderContents[0]);
                        if (currentVersion == mappingVersion) {
                            log.debug("MC {} mappings up to date: {} == {}", version, mappingVersion, currentVersion);
                            continue;
                        } else {
                            folderContents[0].delete();
                        }
                    }
                    
                    log.info("Found out of date or missing mappings for MC {}. New version: {}", version, mappingVersion);
                    filename = mappingsUrl.substring(mappingsUrl.lastIndexOf('/') + 1);
                    FileUtils.copyURLToFile(url, mappingsFolder.toPath().resolve(filename).toFile());
                }
            } catch (IOException e) {
                log.error("Error loading MCP data: ", e);
            }
        }

        private final Pattern MAPPINGS_FILENAME_PATTERN = Pattern.compile("mcp_(?:stable|snapshot)-([0-9]+)-\\S+\\.zip");
        private int getCurrentVersion(File zipFile) throws IOException {
            Matcher matcher = MAPPINGS_FILENAME_PATTERN.matcher(zipFile.getName());
            Preconditions.checkArgument(matcher.matches(), "Invalid file found in mappings folder: " + zipFile.getName());
            return Integer.parseInt(matcher.group(1));
        }
    }
    
    @SneakyThrows
    public void start() {
        Runnable updateTask = new UpdateCheckTask();
        Threads.sleep(2000);
        
        updateTask.run();
        executor.submit(() -> {
            while (!executor.isShutdown()) {
                Threads.sleep(10 * 60 * 1000); // 10 minutes
                updateTask.run();
            }
        });
    }
    
    public SrgDatabase getSrgDatabase(String mcver) throws NoSuchVersionException {
        SrgDatabase db = srgTable.get(mcver);
        if (db == null) {
            db = new SrgDatabase(mcver);
            srgTable.put(mcver, db);
        }
        return db;
    }
    
    public List<ISrgMapping> lookupSRG(MappingType type, String name, String mcver) throws NoSuchVersionException {
        return getSrgDatabase(mcver).lookup(type, name);
    }
    
    public MappingDatabase getMappingDatabase(String mcver) throws NoSuchVersionException {
        MappingDatabase db = mappingTable.get(mcver);
        if (db == null) {
            db = new MappingDatabase(mcver);
            mappingTable.put(mcver, db);
        }
        return db;
    }
    
    public Collection<IMemberInfo> lookup(MappingType type, String name, String mcver) throws NoSuchVersionException {
        return getMappingDatabase(mcver).lookup(type, name);
    }
}
