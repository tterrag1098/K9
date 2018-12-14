package com.tterrag.k9.mappings.mcp;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

import com.tterrag.k9.mappings.MappingType;
import com.tterrag.k9.mappings.NoSuchVersionException;
import com.tterrag.k9.mappings.Parser;
import com.tterrag.k9.util.NonNull;
import com.tterrag.k9.util.NullHelper;

public class SrgDatabase extends FastSrgLookupDatabase<SrgMapping> {

    public SrgDatabase(String mcver) throws NoSuchVersionException {
        super(mcver);
    }

    @Override
    public List<SrgMapping> parseMappings() throws NoSuchVersionException, IOException {
        String mcver = getMinecraftVersion();
        File zip = McpDownloader.INSTANCE.getDataFolder().resolve(Paths.get(mcver, "srgs", "mcp-" + mcver + "-srg.zip")).toFile();
        Parser<ZipFile, SrgMapping> parser;
        if (!zip.exists()) {
            zip = McpDownloader.INSTANCE.getDataFolder().resolve(Paths.get(mcver, "srgs", "mcp_config-" + mcver + ".zip")).toFile();
            if (!zip.exists()) {
                throw new NoSuchVersionException(mcver);
            }
            parser = new TsrgParser(this);
        } else {
            parser = new SrgParser();
        }
        try (ZipFile zipfile = new ZipFile(zip)) {
            return parser.parse(zipfile);
        }
    }

    @NonNull
    @Override
    public Collection<SrgMapping> lookup(MappingType type, String name) {
        Collection<SrgMapping> fast = lookupFastSrg(type, name);
        if (!fast.isEmpty()) {
            return fast;
        }
        List<SrgMapping> ret = getTable(type).get(name);
        if (ret.isEmpty()) {
            Predicate<Entry<String, SrgMapping>> lookupFunc;
            if (type == MappingType.CLASS) {
                lookupFunc = e -> e.getKey().substring(e.getKey().lastIndexOf('/') + 1).equals(name) || e.getValue().getOriginal().equals(name);
            } else {
                lookupFunc = e -> e.getValue().getIntermediate().equals(name) || e.getValue().getOriginal().equals(name);
            }
            List<SrgMapping> found = getTable(type).entries().stream().filter(lookupFunc).map(Entry::getValue).collect(Collectors.toList());
            return NullHelper.notnullJ(found, "Stream#collect");
        }
        return ret;
    }
}
