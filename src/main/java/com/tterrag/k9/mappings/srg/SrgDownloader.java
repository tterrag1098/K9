package com.tterrag.k9.mappings.srg;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.google.gson.GsonBuilder;
import com.tterrag.k9.mappings.DeserializeIntArrayList;
import com.tterrag.k9.mappings.MappingDownloader;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import reactor.core.publisher.Mono;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;

@Slf4j
public class SrgDownloader extends MappingDownloader<SrgMapping, SrgDatabase> {
    public static final SrgDownloader INSTANCE = new SrgDownloader();

    private static final String SRGS_URL = "https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp/%1$s/mcp-%1$s-srg.zip";
    private static final String TSRGS_URL = "https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp_config/%1$s/mcp_config-%1$s.zip";

    private SrgDownloader() {
        super("srg", SrgDatabase::new, 1);
    }

    @Override
    protected Set<String> getMinecraftVersionsInternal() {
        return Collections.emptySet();
    }

    @Override
    protected String getLatestMinecraftVersionInternal(boolean stable) {
        return "Unknown";
    }

    @Override
    protected void collectParsers(GsonBuilder builder) {
        super.collectParsers(builder);
        DeserializeIntArrayList.register(builder);
    }

    @Override
    protected Mono<Void> updateVersions() {
        return Mono.empty();
    }

    private int getMinVersion(String version) {
        String minversion = version.substring(version.indexOf('.') + 1);
        int seconddot = minversion.indexOf('.');
        if (seconddot != -1) {
            minversion = minversion.substring(0, seconddot);
        }
        return Integer.parseInt(minversion);
    }

    public Mono<Void> updateSrgs(String version) {
        return Mono.fromCallable(() -> {
            Path versionFolder = getDataFolder().resolve(version);

            String urlpattern = SRGS_URL;
            if (getMinVersion(version) >= 13) {
                urlpattern = TSRGS_URL;
            }

            log.info("Updating SRG data for for MC {}", version);

            // Download new SRGs if necessary
            Path srgsFolder = versionFolder.resolve("srgs");
            String srgsUrl = String.format(urlpattern, version);
            URL url = new URL(srgsUrl);

            String filename = srgsUrl.substring(srgsUrl.lastIndexOf('/') + 1);
            File md5File = srgsFolder.resolve(filename + ".md5").toFile();
            File zipFile = srgsFolder.resolve(filename).toFile();

            boolean srgsUpToDate = false;
            String md5 = IOUtils.toString(new URL(srgsUrl + ".md5").openStream(), Charsets.UTF_8);
            if (md5File.exists() && zipFile.exists()) {
                String localMd5 = Files.asCharSource(md5File, Charsets.UTF_8).readFirstLine();
                if (md5.equals(localMd5)) {
                    log.debug("MC {} SRGs up to date: {} == {}", version, md5, localMd5);
                    srgsUpToDate = true;
                }
            }

            if (!srgsUpToDate) {
                log.info("Found out of date or missing SRGs for MC {}. new MD5: {}", version, md5);
                FileUtils.copyURLToFile(url, zipFile);
                FileUtils.write(md5File, md5, Charsets.UTF_8);
                remove(version);
            }
            return null;
        });
    }

    @Override
    protected Mono<Void> checkUpdates(String version) {
        return updateSrgs(version);
    }
}
