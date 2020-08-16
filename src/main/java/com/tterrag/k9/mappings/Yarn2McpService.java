package com.tterrag.k9.mappings;

import java.io.IOException;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ListMultimap;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.tterrag.k9.mappings.mcp.IntermediateMapping;
import com.tterrag.k9.mappings.mcp.McpDownloader;
import com.tterrag.k9.mappings.mcp.McpMapping;
import com.tterrag.k9.mappings.mcp.McpMapping.Side;
import com.tterrag.k9.mappings.mcp.SrgDatabase;
import com.tterrag.k9.mappings.mcp.SrgMapping;
import com.tterrag.k9.mappings.yarn.YarnDownloader;
import com.tterrag.k9.util.Monos;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpStatusClass;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClient;
import reactor.util.function.Tuple2;

@Slf4j
public class Yarn2McpService {
    
    private static final MappingType[] SUPPORTED_TYPES = { MappingType.FIELD, MappingType.METHOD };
    
    private static final String YARN = "yarn", MIXED = "mixed";
    private static final String MIXED_VERSION = "1.14.3";
    
    private static final String EOL = "\r\n";
    
    private static final String POM_TEMPLATE = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + EOL + 
            "<project xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\" xmlns=\"http://maven.apache.org/POM/4.0.0\"" + EOL + 
            "    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">" + EOL + 
            "  <modelVersion>4.0.0</modelVersion>" + EOL + 
            "  <groupId>de.oceanlabs.mcp</groupId>" + EOL + 
            "  <artifactId>%1$s</artifactId>" + EOL + 
            "  <version>%2$s</version>" + EOL + 
            "</project>" + EOL;
    
    private static final ImmutableMap<String, String> CORRECTIONS = ImmutableMap.<String, String>builder()
            .put("func_213454_em", "wakeUpInternal") // FoxEntity
            .put("field_77356_a ", "protectionType") // ProtectionEnchantment
            .put("func_225486_c", "getTemperatureCached") // Biome
            .put("func_227833_h_", "resetUnchecked") // BufferBuilder
            .put("func_227480_b_", "findPathNodeType") // WalkNodeProcessor
            .put("func_228676_c_", "create") // RenderType$Type
            .put("field_200256_aj", "lootTableManager") // MinecraftServer (conflict with forge added field)
            .put("func_70825_j", "teleportTo") // EndermanEntity (mapping error, invalid override)
            .put("func_211342_a", "fromReader") // MinMaxBounds$IntBound (mapping error, unintentional overload)
            .put("func_211337_a", "fromReader") // MinMaxBounds (conflict with nested class method name?)
            .put("func_211338_a", "create") // MinMaxBounds$IntBound (possible conflict? at this point I'm throwing everything at the wall)
            .put("func_218985_a", "create") // ServerProperties (mapping error, invalid override)
            .put("field_234921_x_", "dimensionType") // World (conflict with RegistryKey field)
            .put("field_237004_e_", "templateLoc") // RuinedPortalPiece (mapping error, shadows field in super)
            .put("field_236995_d_", "templateLoc") // NetherFossilStructures (")
            .put("func_238483_d_", "unusedGetHeight") // Widget (getHeight nonsense)
            .put("func_237806_b_", "addVisibleButton") // RealmsConfigureWorldScreen (conflict with mcp addButton)
            .put("func_176610_l", "getString") // IStringSerializable (Conflict with MCP names in enums)
            .put("func_234934_e_", "isOutsideWorldLimit") // World (Conflict with MCP name isValid)
            .put("func_233709_b_", "setMemoryInternal") // Brain (Conflict with MCP name setMemory)
            .put("func_230519_c_", "getKeyOptional") // Registry (Conflict with MCP name getKey)
            .build();
    
    public final String output;
    
    private final String user, pass;
    
    private Path tempDir;
        
    public Yarn2McpService(String url, String user, String pass) {
        this.output = url + "/de/oceanlabs/mcp";
        this.user = user;
        this.pass = pass;
    }
    
    public Mono<Void> start() {
        // First, generate mappings for the latest mcp version if they are not done already.
        // These either shouldn't change (if yarn is on a newer version) or will get updated
        // by the daily export (if yarn is on the same version).
        return McpDownloader.INSTANCE.getLatestMinecraftVersion(true)
                .publishOn(Schedulers.elastic())
                .flatMap(version -> publishIfNotExists(version, true, YARN, this::publishMappings))
                // Then run initial output of the latest yarn version if no file exists for today
                .then(YarnDownloader.INSTANCE.getLatestMinecraftVersion(true))
                .flatMap(version -> publishIfNotExists(version, false, YARN, this::publishMappings).thenReturn(version))
                // Also publish mixed mappings
                .flatMap(version -> publishIfNotExists(version, false, MIXED, (v, $) -> publishMixedMappings(MIXED_VERSION, v)))
                // Then begin a periodic publish every day at midnight
                .thenMany(Flux.interval(Duration.between(Instant.now(), Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS)), Duration.ofDays(1)))
                .flatMap(tick -> YarnDownloader.INSTANCE.getLatestMinecraftVersion(true)
                        .publishOn(Schedulers.elastic())
                        .flatMap(version -> publishMappings(version, false).thenReturn(version))
                        .flatMap(version -> publishMixedMappings(MIXED_VERSION, version))
                        .thenReturn(tick)
                        .doOnError(t -> log.error("Error performing periodic mapping push", t))
                        .onErrorReturn(tick))
                .then();
    }
    
    private Mono<Void> publishIfNotExists(String version, boolean stable, String name, BiFunction<String, Boolean, Mono<Void>> func) {
        return Mono.fromSupplier(() -> getOutputURL(version, stable, name))
            .filterWhen(url -> remoteFileMissing(url))
            .flatMap($ -> func.apply(version, stable)
                    .doOnError(t -> log.error("Error publishing " + name + " mappings for version " + version, t))
                    .onErrorResume(t -> Mono.empty()));
    }
    
    private Mono<Boolean> remoteFileMissing(String url) {
        return HttpClient.create()
                .get()
                .uri(url)
                .response()
                .map(resp -> resp.status().code() == 404);
    }
    
    private Mono<SrgDatabase> getSrgs(String version) {
        return McpDownloader.INSTANCE.updateSrgs(version)
                .then(Mono.fromCallable(() -> (SrgDatabase) new SrgDatabase(version).reload()));
    }
    
    private Mono<Void> setupTempDir() {
        return Mono.fromCallable(() -> {
            if (tempDir != null) {
                FileUtils.deleteDirectory(tempDir.toFile());
            }
            tempDir = Files.createTempDirectory("yarn2mcp");
            return null;
        });
    }
    
    private Mono<Void> publishMixedMappings(String mcpVersion, String yarnVersion) {
        return Mono.zip(getSrgs(yarnVersion),
                        YarnDownloader.INSTANCE.getDatabase(yarnVersion),
                        McpDownloader.INSTANCE.getDatabase(mcpVersion))
                .doOnNext($ -> log.info("Publishing mixed mappings for MC " + mcpVersion + "/" + yarnVersion))
                .flatMap(srgs -> setupTempDir().thenReturn(srgs))
                .as(Monos.groupWith(Flux.just(SUPPORTED_TYPES), (type, dbs) -> Mono.zip(
                        findMatching(type, dbs.getT1(), dbs.getT2()),
                        this.<SrgMapping, Mapping>findMatchingByIntermediate(type, dbs.getT1(), dbs.getT3()))))
                .flatMap(gf -> gf
                        .doOnNext(maps -> maps.getT1().forEach(maps.getT2()::putIfAbsent))
                        .map(Tuple2::getT2)
                        .flatMapIterable(Map::entrySet)
                        .filter(e -> e.getKey().getName() != null || e.getValue().getName() != null)
                        .map(e -> toCsv(e.getKey(), e.getValue()))
                        .collectList()
                        .flatMap(csv -> writeToFile(yarnVersion, false, gf.key(), csv, MIXED)))
                .last()
                .flatMap(p -> uploadFile(yarnVersion, false, MIXED, p))
                .then();
    }
    
    private Mono<Void> publishMappings(String version, boolean stable) {
        return Mono.zip(getSrgs(version), YarnDownloader.INSTANCE.getDatabase(version))
                .doOnNext($ -> log.info("Publishing yarn-over-mcp for MC " + version))
                .flatMap(srgs -> setupTempDir().thenReturn(srgs))
                .as(Monos.groupWith(Flux.just(SUPPORTED_TYPES), (type, t) -> findMatching(type, t.getT1(), t.getT2())))
                .flatMap(gf -> gf.flatMapIterable(m -> m.entrySet())
                        .filter(e -> e.getKey().getName() != null || e.getValue().getName() != null)
                        .map(e -> toCsv(e.getKey(), e.getValue()))
                        .collectList()
                        .flatMap(csv -> writeToFile(version, stable, gf.key(), csv, YARN)))
                .last()
                .flatMap(p -> uploadFile(version, stable, YARN, p))
                .then();
    }

    private <M extends IntermediateMapping> Comparator<M> bySrg() {
        return Comparator.<M>comparingInt(m -> FastIntLookupDatabase.getSrgId(m.getIntermediate())
                .orElse(Integer.MAX_VALUE))
                .thenComparing(Mapping::getIntermediate);
    }
    
    private <M1 extends IntermediateMapping, M2 extends Mapping> Mono<SortedMap<M1, M2>> findMatchingByIntermediate(
            MappingType type, AbstractMappingDatabase<? extends M1> a, AbstractMappingDatabase<? extends M2> b) {
        return Mono.fromSupplier(() -> {
            ListMultimap<String, ? extends M1> aBySrg = a.getTable(NameType.INTERMEDIATE, type);
            ListMultimap<String, ? extends M2> bBySrg = b.getTable(NameType.INTERMEDIATE, type);
            SortedMap<M1, M2> ret = new TreeMap<>(bySrg());
            for (String key : aBySrg.keySet()) {
                List<? extends M1> aMappings = aBySrg.get(key);
                List<? extends M2> bMappings = bBySrg.get(key);
                if (!aMappings.isEmpty() && !bMappings.isEmpty()) {
                    ret.put(aMappings.get(0), bMappings.get(0));
                }
            }
            return ret;
        });
    }

    private <M1 extends IntermediateMapping, M2 extends Mapping> Mono<SortedMap<M1, M2>> findMatching(
            MappingType type, AbstractMappingDatabase<M1> mcp, AbstractMappingDatabase<M2> yarn) {
        return Mono.defer(() -> {
            Map<String, M1> mcpBySignature = bySignature(mcp.getTable(NameType.ORIGINAL, type));
            Map<String, M2> yarnBySignature = bySignature(yarn.getTable(NameType.ORIGINAL, type));
            return Flux.fromIterable(mcpBySignature.entrySet())
                    .filter(e -> yarnBySignature.containsKey(e.getKey()))
                    .collectMap(Map.Entry::getValue, e -> yarnBySignature.get(e.getKey()), () -> new TreeMap<M1, M2>(bySrg()))
                    .map(m -> (SortedMap<M1, M2>) m); // wtf reactor?
        });
    }
    
    private <M1 extends Mapping, M2 extends Mapping> String toCsv(M1 m1, M2 m2) {
        String name = m2.getName();
        if (name == null) {
            name = m1.getName();
        } else {
            name = CORRECTIONS.getOrDefault(m1.getIntermediate(), name);
        }
        return Joiner.on(',').join(
                m1.getIntermediate(),
                name,
                m1 instanceof McpMapping ? ((McpMapping)m1).getSide().ordinal() : Side.BOTH.ordinal(),
                Strings.nullToEmpty(m2.getName() != null ? getComment(m2) : getComment(m1)));
    }
    
    private String getComment(Mapping m) {
        if (m instanceof CommentedMapping) {
            return ((CommentedMapping) m).getComment();
        }
        return null;
    }
    
    private String getOutputURL(String version, boolean stable, String name) {
        String channel = stable ? "mcp_stable" : "mcp_snapshot";
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String snapshot = stable ? name + "-" + version : String.format("%04d%02d%02d-%s-%s", now.getYear(), now.getMonthValue(), now.getDayOfMonth(), name, version);
        return output
                + "/" + channel
                + "/" + snapshot
                + "/" + channel + "-" + snapshot + ".zip";
    }
    
    private Mono<Path> writeToFile(String version, boolean stable, MappingType type, List<String> csv, String name) {
        return Mono.fromCallable(() -> {
            log.info("Writing yarn-to-mcp data for " + version + " [" + type + "] to temp file");
            Map<String, String> env = new HashMap<>(); 
            env.put("create", "true");
            Path tmp = tempDir.resolve(version + "-" + name + ".zip");
            URI uri = URI.create("jar:" + tmp.toUri());
            String text = "searge,name,side,desc" + EOL + csv.stream().collect(Collectors.joining(EOL));
            try (FileSystem fs = FileSystems.newFileSystem(uri, env)) {
                writeToFile(fs.getPath(type.getCsvName() + ".csv"), text);
            }
            if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
                Files.setPosixFilePermissions(tmp, PosixFilePermissions.fromString("rw-rw-r--"));
            }
            return tmp;
        });
    }
    
    private Mono<Void> uploadFile(String version, boolean stable, String name, Path file) {
        return Mono.defer(() -> {
            String zipURL = getOutputURL(version, stable, name);

            String pomURL = zipURL.replace(".zip", ".pom");
            String filename = pomURL.substring(pomURL.lastIndexOf('/') + 1).replace(".pom", "");
            int split = filename.indexOf('-');
            Object[] args = { filename.substring(0, split), filename.substring(split + 1) };
            String pomText = String.format(POM_TEMPLATE, args);

            return Mono.fromRunnable(() -> log.info("Uploading yarn-to-mcp data to " + zipURL))
                    .then(writeFile(zipURL, file))
                    .then(Mono.fromCallable(() -> Files.readAllBytes(file))
                            .flatMap(b -> writeHashes(zipURL, b)))
                    .then(writeText(pomURL, pomText))
                    .then(writeHashes(pomURL, pomText.getBytes(StandardCharsets.UTF_8)));
        });
    }
    
    private void writeToFile(Path path, String text) throws IOException {
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write(text);
        }
    }
    
    private Mono<Void> writeFile(String url, Path file) {
        return writeData(url, Mono.fromCallable(() -> Files.readAllBytes(file)).map(Unpooled::wrappedBuffer));
    }
    
    private Mono<Void> writeText(String url, String text) {
        return writeData(url, Mono.just(text).map(s -> s.getBytes(StandardCharsets.UTF_8)).map(Unpooled::wrappedBuffer));
    }
    
    private Mono<Void> writeData(String url, Mono<ByteBuf> data) {
        String md5login = Base64.getEncoder().encodeToString((user + ":" + pass).getBytes());
        
        return HttpClient.create()
            .wiretap(true)
            .headers(h -> h.add("Authorization", "Basic " + md5login))
            .put()
            .uri(url)
            .send(data)
            .responseSingle((resp, buf) -> resp.status().codeClass() != HttpStatusClass.SUCCESS
                    ? buf.asString().flatMap(err -> Mono.error(new IllegalStateException("Unexpected error publishing yarn2mcp artifacts to " + url + ": " + err)))
                    : Mono.empty())
            .then();
    }
    
    @SuppressWarnings("deprecation")
    private Mono<Void> writeHashes(String url, byte[] toHash) {
        return writeHash(url + ".md5", toHash, Hashing.md5())
         .then(writeHash(url + ".sha1", toHash, Hashing.sha1()));
    }
    
    private Mono<Void> writeHash(String url, byte[] text, HashFunction hasher) {
        return writeData(url, Mono.just(hasher)
                .map(m -> m.hashBytes(text))
                .map(HashCode::asBytes)
                .map(Unpooled::wrappedBuffer));
    }
    
    private String getSignature(Mapping mapping) {
        StringBuilder ret = new StringBuilder(mapping.getOwner(NameType.ORIGINAL)).append(".").append(mapping.getOriginal());
        if (mapping.getType() == MappingType.METHOD) {
            ret.append(mapping.getDesc(NameType.ORIGINAL));
        }
        return ret.toString();
    }
    
    private <T extends Mapping> Map<String, T> bySignature(ListMultimap<String, T> table) {
        return table.values().stream()
                .collect(Collectors.toMap(this::getSignature, Function.identity()));
    }
}
