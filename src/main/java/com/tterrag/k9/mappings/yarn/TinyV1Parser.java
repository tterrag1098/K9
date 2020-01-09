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
import com.tterrag.k9.mappings.MappingType;
import com.tterrag.k9.mappings.NameType;
import com.tterrag.k9.mappings.Parser;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class TinyV1Parser implements Parser<File, TinyMapping> {
    
    static final Map<String, NameType> BY_NAME = ImmutableMap.of(
            "official", NameType.ORIGINAL,
            "intermediary", NameType.INTERMEDIATE,
            "named", NameType.NAME);
    
    private final YarnDatabase db;

    @Override
    public List<TinyMapping> parse(File input) throws IOException {
        List<String> lines = IOUtils.readLines(new GZIPInputStream(new FileInputStream(input)), Charsets.UTF_8);

        String header = lines.remove(0);
        String[] headerinfo = header.split("\t");
        int tinyver = Integer.parseInt(headerinfo[0].substring(1));
        if (tinyver != 1) {
            throw new IllegalArgumentException("Unsupported mappings version");
        }
        
        IntList order = new IntArrayList(Arrays.stream(headerinfo).skip(1).map(BY_NAME::get).mapToInt(t -> t.ordinal() + 1).toArray());
        
        return lines.stream().map(s -> fromString(s, order)).collect(Collectors.toList());
    }

    private TinyMapping fromString(String line, IntList order) {
        String[] info = line.split("\t");
        MappingType type = MappingType.valueOf(info[0]);
        switch(type) {
            case CLASS:
                String intermediate = info[order.getInt(1)];
                String name = info[order.getInt(2)];
                return new TinyMapping(db, type, null, null, info[order.getInt(0)], intermediate, intermediate.equals(name) ? null : name, null);
            case METHOD:
            case FIELD:
                intermediate = info[order.getInt(1) + 2];
                name = info[order.getInt(2) + 2];
                return new TinyMapping(db, type, info[1], info[2], info[order.getInt(0) + 2], intermediate, intermediate.equals(name) ? null : name, null);
            default:
                throw new IllegalArgumentException("Unknown type"); // Params NYI, doesn't exist in the spec
        }
    }
}
