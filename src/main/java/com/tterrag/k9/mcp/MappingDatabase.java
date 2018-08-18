package com.tterrag.k9.mcp;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.tterrag.k9.mcp.IMCPMapping.Side;
import com.tterrag.k9.mcp.ISrgMapping.MappingType;
import com.tterrag.k9.util.NonNull;
import com.tterrag.k9.util.Nullable;

import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;

public class MappingDatabase {
    
    @RequiredArgsConstructor
    private static class MemberInfo implements IMemberInfo {
        
        private interface Excludes { String getMCP(); }
        
        @Delegate(excludes = Excludes.class)
        private final ISrgMapping srg;

        @Nullable
        private final IMCPMapping mcp;
        
        @Override
        public String getMCP() {
            final IMCPMapping mcp = this.mcp;
            return mcp == null ? srg.getMCP() : mcp.getMCP();
        }

        @Override
        public @Nullable String getComment() {
            final IMCPMapping mcp = this.mcp;
            return mcp == null ? null : mcp.getComment();
        }

        @Override
        public @Nullable Side getSide() {
            final IMCPMapping mcp = this.mcp;
            return mcp == null ? null : mcp.getSide();
        }
    }
        
    private final Multimap<MappingType, IMCPMapping> mappings = MultimapBuilder.enumKeys(MappingType.class).arrayListValues().build();
    
    private final File zip;
    
    private final SrgDatabase srgs;
    
    public MappingDatabase(String mcver) throws NoSuchVersionException {
        File folder = Paths.get(".", "data", mcver, "mappings").toFile();
        if (!folder.exists()) {
            throw new NoSuchVersionException(mcver);
        }
        this.zip = folder.listFiles()[0];
        try {
            reload();
        } catch (IOException e) {
            Throwables.propagate(e);
        }
        
        this.srgs = DataDownloader.INSTANCE.getSrgDatabase(mcver);
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
                        IMCPMapping mapping = new IMCPMapping.Impl(type, info[0], info[1], comment, Side.values()[Integer.valueOf(info[2])]);
                        mappings.put(type, mapping);
                    }
                }
            }
        } finally {
            zipfile.close();
        }
    }

    public Collection<IMemberInfo> lookup(MappingType type, String name) {
        Collection<IMCPMapping> mappingsForType = mappings.get(type);
        String[] hierarchy = null;
        if (name.contains(".")) {
            hierarchy = name.split("\\.");
            name = hierarchy[hierarchy.length - 1];
        }

        final String lookup = name;
        
        // Find all matches in srgs and mcp data
        Map<String, ISrgMapping> srgMatches = srgs.lookup(type, name).stream().collect(Collectors.toMap(ISrgMapping::getSRG, Function.identity()));
        List<IMCPMapping> mcpMatches = mappingsForType.stream().filter(m -> m.getSRG().contains(lookup) || m.getMCP().equals(lookup)).collect(Collectors.toList());

        Map<String, IMemberInfo> srgToInfo = new LinkedHashMap<>();

        // Ignore those which do not match the supplied hierarchy
        final String parent = hierarchy == null ? null : hierarchy[0];
        Iterator<@NonNull IMCPMapping> iter = mcpMatches.stream()
                // TODO params
                .filter(m -> m.getType() != MappingType.PARAM && (parent == null || Strings.nullToEmpty(srgs.lookup(type, m.getSRG()).get(0).getOwner()).endsWith(parent)))
                .iterator();
        while (iter.hasNext()) {
            IMCPMapping mcp = iter.next();
            ISrgMapping srg = srgMatches.get(mcp.getSRG());
            if (srg == null) {
                srg = srgs.lookup(type, mcp.getSRG()).get(0);
            }
            srgToInfo.put(mcp.getSRG(), new MemberInfo(srg, mcp));
        }
        
        // Remove srg matches that are mapped
        for (Entry<String, ISrgMapping> e : srgMatches.entrySet()) {
            if (!srgToInfo.containsKey(e.getKey())) {
                srgToInfo.put(e.getKey(), new MemberInfo(e.getValue(), null));
            }
        }
        
        return srgToInfo.values();
    }
}
