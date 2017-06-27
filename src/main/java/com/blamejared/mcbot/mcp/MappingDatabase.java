package com.blamejared.mcbot.mcp;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;

import com.blamejared.mcbot.mcp.IMapping.Side;
import com.blamejared.mcbot.mcp.ISrgMapping.MappingType;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;

public class MappingDatabase {
        
    private final Multimap<MappingType, IMapping> mappings = MultimapBuilder.enumKeys(MappingType.class).arrayListValues().build();
    
    private final File zip;
    private final String mcver;
    
    public MappingDatabase(String mcver) {
        this.zip = Paths.get(".", "data", mcver, "mappings").toFile().listFiles()[0];
        this.mcver = mcver;
        try {
            reload();
        } catch (IOException e) {
            Throwables.propagate(e);
        }
    }

    public void reload() throws IOException {
        mappings.clear();
        ZipFile zipfile = new ZipFile(zip);

        try {
            for (MappingType type : MappingType.values()) {
                if (type.getCsvName() != null) {
                    List<String> lines;
                    lines = IOUtils.readLines(zipfile.getInputStream(zipfile.getEntry(type.getCsvName() + ".csv")), Charsets.UTF_8);

                    lines.remove(0); // Remove header line

                    for (String line : lines) {
                        String[] info = line.split(",", -1);
                        String comment = info.length > 3 ? Joiner.on(',').join(ArrayUtils.subarray(info, 3, info.length)) : "";
                        IMapping mapping = new IMapping.Impl(type, info[0], info[1], comment, Side.values()[Integer.valueOf(info[2])]);
                        mappings.put(type, mapping);
                    }
                }
            }
        } finally {
            zipfile.close();
        }
    }

    public List<IMapping> lookup(MappingType type, String name) {
        Collection<IMapping> mappingsForType = mappings.get(type);
        String[] hierarchy = null;
        if (name.contains(".")) {
            hierarchy = name.split("\\.");
            name = hierarchy[hierarchy.length - 1];
        }

        final String lookup = name;
        List<IMapping> ret = mappingsForType.stream().filter(m -> m.getSRG().contains(lookup) || m.getMCP().equals(lookup)).collect(Collectors.toList());
        if (hierarchy != null) {
            if (type == MappingType.PARAM) {
                final String parent = hierarchy[0];
                System.out.println(parent);
            } else {
                final String parent = hierarchy[0];
                ret = ret.stream().filter(m -> Strings.nullToEmpty(DataDownloader.INSTANCE.lookupSRG(type, m.getSRG(), mcver).get(0).getOwner()).endsWith(parent)).collect(Collectors.toList());
            }
        }
        
        return ret;
    }
}
