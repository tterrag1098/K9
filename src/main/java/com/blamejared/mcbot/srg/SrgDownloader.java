package com.blamejared.mcbot.srg;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import com.blamejared.mcbot.srg.ISrgMapping.MappingType;
import com.blamejared.mcbot.util.Threads;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import lombok.SneakyThrows;

public enum SrgDownloader {
    
    INSTANCE;
    
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final String VERSION_JSON = "http://export.mcpbot.bspk.rs/versions.json";
    private static final String SRGS_URL = "http://files.minecraftforge.net/maven/de/oceanlabs/mcp/mcp/%1$s/mcp-%1$s-srg.zip";
    private final Path srgsFolder = Paths.get(".", "srgs");
    
    private Map<String, SrgDatabase> srgTable = new HashMap<>();
    
    @SneakyThrows
    public void start() {
        Runnable updateTask = () -> {
            try {
                URL url = new URL(VERSION_JSON);
                HttpURLConnection request = (HttpURLConnection) url.openConnection();
                request.connect();

                Map<String, JsonObject> versions = new Gson().fromJson(new InputStreamReader(request.getInputStream()), new TypeToken<HashMap<String, JsonObject>>() {}.getType());
                for (String version : versions.keySet()) {
                    System.out.println("Updating SRGS for MC " + version);
                    String srgsUrl = String.format(SRGS_URL, version);
                    url = new URL(srgsUrl);

                    String filename = srgsUrl.substring(srgsUrl.lastIndexOf('/') + 1);
                    File md5File = srgsFolder.resolve(version + "/" + filename + ".md5").toFile();
                    File zipFile = srgsFolder.resolve(version + "/" + filename).toFile();

                    String md5 = IOUtils.toString(new URL(srgsUrl + ".md5").openStream(), Charsets.UTF_8);
                    if (md5File.exists() && zipFile.exists()) {
                        String localMd5 = Files.readFirstLine(md5File, Charsets.UTF_8);
                        if (md5.equals(localMd5)) {
                            continue;
                        }
                    }

                    System.out.println("Found out of date or missing SRGS for MC " + version + ". new MD5: " + md5);
                    FileUtils.copyURLToFile(url, zipFile);
                    FileUtils.write(md5File, md5, Charsets.UTF_8);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        };
        
        updateTask.run();
        executor.submit(() -> {
            while (!executor.isShutdown()) {
                Threads.sleep(10 * 60 * 1000); // 10 minutes
                updateTask.run();
            }
        });
    }
    
    public ISrgMapping lookup(MappingType type, String name, String mcver) {
        return srgTable.computeIfAbsent(mcver, SrgDatabase::new).lookup(type, name);
    }
}
