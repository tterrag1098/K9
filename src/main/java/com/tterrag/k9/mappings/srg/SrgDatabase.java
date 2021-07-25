package com.tterrag.k9.mappings.srg;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

import com.tterrag.k9.mappings.MappingType;
import com.tterrag.k9.mappings.NameType;
import com.tterrag.k9.mappings.NoSuchVersionException;
import com.tterrag.k9.mappings.Parser;
import com.tterrag.k9.mappings.mcp.OverrideRemovingDatabase;
import com.tterrag.k9.util.NullHelper;
import com.tterrag.k9.util.annotation.NonNull;

public class SrgDatabase extends OverrideRemovingDatabase<SrgMapping> {

    public SrgDatabase(String mcver) throws NoSuchVersionException {
        super(mcver);
    }

    public static boolean srgExists(String mcver) {
        return getSrgFile(mcver, false).exists() || getSrgFile(mcver, true).exists();
    }

    @Override
    public Collection<SrgMapping> parseMappings() throws NoSuchVersionException, IOException {
        String mcver = getMinecraftVersion();
        File zip = getSrgFile(mcver, false);
        Parser<ZipFile, SrgMapping> parser;
        if (!zip.exists()) {
            zip = getSrgFile(mcver, true);
            if (!zip.exists()) {
                throw new NoSuchVersionException("srg", mcver);
            }
            parser = new TsrgParser(this);
        } else {
            parser = new SrgParser(this);
        }
        try (ZipFile zipfile = new ZipFile(zip)) {
            return parser.parse(zipfile);
        }
    }

    private static File getSrgFile(String mcver, boolean tsrg) {
        String filename = tsrg ? "mcp_config-" + mcver + ".zip" : "mcp-" + mcver + "-srg.zip";
        return SrgDownloader.INSTANCE.getDataFolder().resolve(Paths.get(mcver, "srgs", filename)).toFile();
    }

    @NonNull
    @Override
    public Collection<SrgMapping> lookup(NameType by, MappingType type, String name) {
        if (by == NameType.NAME) {
            return Collections.emptyList();
        } else if (by == NameType.ORIGINAL) {
            return super.lookup(by, type, name);
        }
        Collection<SrgMapping> fast = fastLookup(type, name);
        if (!fast.isEmpty()) {
            return fast;
        }
        List<SrgMapping> ret = getTable(NameType.INTERMEDIATE, type).get(name);
        if (ret.isEmpty()) {
            Predicate<Entry<String, SrgMapping>> lookupFunc;
            if (type == MappingType.CLASS) {
                lookupFunc = e -> e.getKey().substring(e.getKey().lastIndexOf('/') + 1).equals(name) || e.getValue().getOriginal().equals(name);
            } else {
                lookupFunc = e -> e.getValue().getIntermediate().equals(name) || e.getValue().getOriginal().equals(name);
            }
            List<SrgMapping> found = getTable(NameType.INTERMEDIATE, type).entries().stream().filter(lookupFunc).map(Entry::getValue).collect(Collectors.toList());
            return NullHelper.notnullJ(found, "Stream#collect");
        }
        return ret;
    }
}
