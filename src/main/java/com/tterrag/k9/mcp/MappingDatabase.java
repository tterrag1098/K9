package com.tterrag.k9.mcp;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.tterrag.k9.mcp.IMCPMapping.Side;
import com.tterrag.k9.mcp.ISrgMapping.MappingType;
import com.tterrag.k9.util.Nullable;
import com.tterrag.k9.util.Patterns;

import clojure.asm.Type;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;

public class MappingDatabase {
    
    @RequiredArgsConstructor
    private static class MemberInfo implements IMemberInfo {
        
        private interface Excludes { String getMCP(); }
        
        @Delegate(excludes = Excludes.class)
        protected final ISrgMapping srg;

        @Nullable
        protected final IMCPMapping mcp;
        
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
        
        @Override
        public String getParamType() {
            return null;
        }
    }
    
    private static class MemberInfoParam extends MemberInfo {
        
        private final int paramID;
        private final Type type;
        
        public MemberInfoParam(ISrgMapping srg, IMCPMapping mcp, int paramID) {
            this(srg, mcp, paramID, findType(srg, paramID));
        }
        
        public static Type findType(ISrgMapping method, int param) {
            String desc = method.getDesc();
            Type[] args = Type.getArgumentTypes(desc);
            if (param >= args.length) {
                throw new IllegalArgumentException("Could not find type name. Method: " + method + "  param: " + param);
            }
            return args[param];
        }
        
        public MemberInfoParam(ISrgMapping srg, IMCPMapping mcp, int paramID, Type type) {
            super(srg, mcp);
            this.paramID = paramID;
            this.type = type;
        }
        
        @Override
        public MappingType getType() {
            return MappingType.PARAM;
        }

        @Override
        public String getSRG() {
            return srg.getSRG().replaceAll("func_(\\d+).*", "p_$1_" + paramID + "_");
        }
    
        @Override
        public String getOwner() {
            return super.getOwner() + "." + srg.getSRG();
        }
        
        @Override
        public String getParamType() {
            return type.getClassName();
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
        Map<String, List<ISrgMapping>> srgMatches = srgs.lookup(type, lookup).stream().collect(Collectors.toMap(ISrgMapping::getSRG, Lists::newArrayList, (l1, l2) -> { l1.addAll(l2); return l1; }));
        List<IMCPMapping> mcpMatches = mappingsForType.stream().filter(m -> m.getSRG().contains(lookup) || m.getMCP().equals(lookup)).collect(Collectors.toList());

        Map<String, IMemberInfo> srgToInfo = new LinkedHashMap<>();

        // Special case for handling unmapped params
        if (mcpMatches.isEmpty() && type == MappingType.PARAM) {
            Matcher m = Patterns.SRG_PARAM.matcher(name);
            if (m.matches()) {
                Collection<IMemberInfo> potentialMethods = lookup(MappingType.METHOD, m.group(1));
                for (IMemberInfo method : potentialMethods) {
                    int param = 1;
                    String desc = method.getDesc();
                    Type[] params = Type.getArgumentTypes(desc);
                    if (param < params.length) {
                        srgToInfo.put(method.getSRG(), new MemberInfoParam(method, null, param));
                    }
                }
            } else {
                return Collections.emptyList();
            }
        }

        // Ignore those which do not match the supplied hierarchy
        final String parent = hierarchy == null ? null : hierarchy[0];
        for (IMCPMapping mcp : mcpMatches) {
            if (mcp.getType() == MappingType.PARAM) {
                Matcher m = Patterns.SRG_PARAM.matcher(mcp.getSRG());
                if (!m.matches()) {
                    throw new IllegalStateException("SRG is invalid: " + mcp.getSRG());
                }
                IMemberInfo method = lookup(MappingType.METHOD, m.replaceAll("$1")).iterator().next();
                m.reset();
                m.matches();
                srgToInfo.put(mcp.getSRG(), new MemberInfoParam(method, mcp, Integer.parseInt(m.group(2))));
            } else {
                List<ISrgMapping> matches = srgMatches.get(mcp.getSRG());
                if (matches == null) {
                    matches = srgs.lookup(type, mcp.getSRG());
                }
                for (ISrgMapping srg : matches) {
                    if (parent == null || Strings.nullToEmpty(srg.getOwner()).endsWith(parent)) {
                        srgToInfo.put(mcp.getSRG(), new MemberInfo(srg, mcp));
                    }
                }
            }
        }
        
        // Remove srg matches that are mapped
        for (Entry<String, List<ISrgMapping>> e : srgMatches.entrySet()) {
            if (!srgToInfo.containsKey(e.getKey())) {
                for (ISrgMapping srg : e.getValue()) {
                    if (parent == null || Strings.nullToEmpty(srg.getOwner()).endsWith(parent)) {
                        srgToInfo.put(e.getKey(), new MemberInfo(srg, null));
                    }
                }
            }
        }
        
        return srgToInfo.values();
    }
}
