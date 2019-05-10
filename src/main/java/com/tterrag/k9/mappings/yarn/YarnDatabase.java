package com.tterrag.k9.mappings.yarn;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import org.apache.commons.io.IOUtils;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.tterrag.k9.mappings.FastIntLookupDatabase;
import com.tterrag.k9.mappings.NameType;
import com.tterrag.k9.mappings.NoSuchVersionException;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;

public class YarnDatabase extends FastIntLookupDatabase<TinyMapping> {

    public YarnDatabase(String minecraftVersion) {
        super(minecraftVersion);
    }
    
    Map<String, NameType> byName = ImmutableMap.of(
            "official", NameType.ORIGINAL,
            "intermediary", NameType.INTERMEDIATE,
            "named", NameType.NAME);

    @Override
    protected List<TinyMapping> parseMappings() throws NoSuchVersionException, IOException {        
        File folder = YarnDownloader.INSTANCE.getDataFolder().resolve(getMinecraftVersion()).toFile();
        if (!folder.exists()) {
            throw new NoSuchVersionException(getMinecraftVersion());
        }
        File gz = folder.listFiles()[0];
        List<String> lines = IOUtils.readLines(new GZIPInputStream(new FileInputStream(gz)), Charsets.UTF_8);

        String header = lines.remove(0);
        String[] headerinfo = header.split("\t");
        int tinyver = Integer.parseInt(headerinfo[0].substring(1));
        if (tinyver != 1) {
            throw new IllegalArgumentException("Unsupported mappings version");
        }
        
        IntList order = new IntArrayList(Arrays.stream(headerinfo).skip(1).map(byName::get).mapToInt(t -> t.ordinal() + 1).toArray());
        
        return lines.stream().map(s -> TinyMapping.fromString(this, s, order)).collect(Collectors.toList());
    }

}
