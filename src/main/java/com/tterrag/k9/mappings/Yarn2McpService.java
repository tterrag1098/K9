package com.tterrag.k9.mappings;

import java.io.IOException;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ObjectUtils;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ListMultimap;
import com.tterrag.k9.mappings.mcp.McpDatabase;
import com.tterrag.k9.mappings.mcp.McpDownloader;
import com.tterrag.k9.mappings.mcp.McpMapping;
import com.tterrag.k9.mappings.yarn.TinyMapping;
import com.tterrag.k9.mappings.yarn.YarnDatabase;
import com.tterrag.k9.mappings.yarn.YarnDownloader;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Slf4j
public class Yarn2McpService {
    
    public final Path output;
    
    public Yarn2McpService(String output) {
        this.output = Paths.get(output, "de", "oceanlabs", "mcp");
    }
    
    public Mono<Void> start() {
        return McpDownloader.INSTANCE.getLatestMinecraftVersion()
                .map(this::getOutputFile)
                .filter(p -> !p.toFile().exists()) // Run initial output if no file exists for today
                .flatMap($ -> publishMappings())
                // Then begin a periodic publish every day at midnight
                .thenMany(Flux.interval(Duration.between(Instant.now(), LocalDate.now().plusDays(1)), Duration.ofDays(1)))
                .flatMap($ -> publishMappings())
                .then();
    }
    
    private Mono<Void> publishMappings() {
        Mono<String> latest = McpDownloader.INSTANCE.getLatestMinecraftVersion().cache();
        return latest.flatMap(McpDownloader.INSTANCE::getDatabase)
                .zipWith(latest.flatMap(YarnDownloader.INSTANCE::getDatabase))
                .flatMapMany(t -> findMatching(t.getT1(), t.getT2()))
                .groupBy(t -> t.getT1().getType())
                .flatMap(gf -> Flux.just("searge,name,side,desc")
                        .concatWith(gf
                                .sort(Comparator.<Tuple2<McpMapping, TinyMapping>>
                                         comparingInt(t -> FastIntLookupDatabase.getSrgId(t.getT1().getIntermediate())
                                                 .orElse(Integer.MAX_VALUE))
                                        .thenComparing(t -> t.getT1().getIntermediate()))
                                .filter(t -> t.getT1().getName() != null || t.getT2().getName() != null)
                                .filter(t -> !t.getT1().getIntermediate().equals("func_213454_em") && !t.getT1().getIntermediate().equals("field_77356_a")) // Some broken mappings
                                .map(this::toCsv))
                        .collectList()
                        .flatMap(csv -> latest.flatMap(version -> writeToFile(version, gf.key(), csv))))
                .then();
    }

    private Flux<Tuple2<McpMapping, TinyMapping>> findMatching(McpDatabase mcp, YarnDatabase yarn) {
        return Flux.just(MappingType.FIELD, MappingType.METHOD)
                .flatMap(type -> {
                    Map<String, McpMapping> mcpBySignature = bySignature(mcp.getTable(NameType.ORIGINAL, type));
                    Map<String, TinyMapping> yarnBySignature = bySignature(yarn.getTable(NameType.ORIGINAL, type));
                    return Flux.fromIterable(mcpBySignature.entrySet())
                        .flatMap(e -> Mono.justOrEmpty(yarnBySignature.get(e.getKey()))
                                .map(y -> Tuples.of(e.getValue(), y)))
                        .doOnNext(t -> log.info(t.toString()));
                });
    }
    
    private String toCsv(Tuple2<McpMapping, TinyMapping> match) {
        return Joiner.on(',').join(
                match.getT1().getIntermediate(),
                ObjectUtils.firstNonNull(match.getT2().getName(), match.getT1().getName()),
                match.getT1().getSide().ordinal(),
                Strings.nullToEmpty(match.getT1().getDesc()));
    }
    
    private Path getOutputFile(String version) {
        String channel = "mcp_snapshot";
        LocalDate today = LocalDate.now();
        String snapshot = String.format("%04d%02d%02d-yarn-%s", today.getYear(), today.getMonthValue(), today.getDayOfMonth(), version);
        return output
                .resolve(channel)
                .resolve(snapshot)
                .resolve(channel + "-" + snapshot + ".zip");
    }
    
    private Mono<Void> writeToFile(String version, MappingType type, List<String> csv) {
        return Mono.fromRunnable(() -> {
            try {
                Path zip = getOutputFile(version);
                zip.getParent().toFile().mkdirs();
                Map<String, String> env = new HashMap<>(); 
                env.put("create", "true");
                URI uri = URI.create("jar:" + zip.toUri());
                try (FileSystem fs = FileSystems.newFileSystem(uri, env)) {
                    Path nf = fs.getPath(type.getCsvName() + ".csv");
                    try (Writer writer = Files.newBufferedWriter(nf, StandardCharsets.UTF_8, StandardOpenOption.CREATE)) {
                        for (String line : csv) {
                            writer.write(line + "\n");
                        }
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
    
    private String getSignature(Mapping mapping) {
        StringBuilder ret = new StringBuilder(mapping.getOwner()).append(".").append(mapping.getOriginal());
        if (mapping.getType() == MappingType.METHOD) {
            ret.append(mapping.getDesc(NameType.ORIGINAL));
        }
        return ret.toString();
    }
    
    private <T extends Mapping> Map<String, T> bySignature(ListMultimap<String, T> table) {
        return table.values().stream()
                .filter(m -> m.getOwner() != null)
                .collect(Collectors.toMap(this::getSignature, Function.identity()));
    }
}
