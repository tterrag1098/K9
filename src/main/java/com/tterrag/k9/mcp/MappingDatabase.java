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
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
import com.tterrag.k9.util.Nullable;

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
        private final String type;
        
        public MemberInfoParam(ISrgMapping srg, IMCPMapping mcp, int paramID) {
            this(srg, mcp, paramID, findType(srg, paramID));
        }
        
        public static String findType(ISrgMapping method, int param) {
            int i = 1;
            String desc = method.getDesc();
            Matcher paramMatcher = DESC_PARAM.matcher(desc.substring(1, desc.lastIndexOf(')')));
            while (paramMatcher.find()) {
                if (i == param) {
                    return paramMatcher.group();
                }
                i++;
            }
            throw new IllegalArgumentException("Could not find type name. Method: " + method + "  param: " + param);
        }
        
        public MemberInfoParam(ISrgMapping srg, IMCPMapping mcp, int paramID, String type) {
            super(srg, mcp);
            this.paramID = paramID;
            this.type = getReadableName(type);
        }
        
        private static String getReadableName(String className) {
            String ret = className;

            int arrayDimensions = 0;
            while (ret.startsWith("[")) {
                ret = ret.substring(1);
                arrayDimensions++;
            }
            
            if (className.startsWith("L")) {
                ret = className.replaceAll("^L", "").replaceAll(";$", "").replaceAll("\\/", ".");
            } else {    
                if (ret.length() != 1) {
                    throw new IllegalArgumentException("Invalid descriptor \"" + className + "");
                }
                ret = getPrimitiveName(ret.charAt(0));
            }
            for (int i = 0; i < arrayDimensions; i++) {
                ret += "[]";
            }
            return ret;
        }
        
        private static String getPrimitiveName(char desc) {
            switch(desc) {
                case 'B': return "byte";
                case 'C': return "char";
                case 'D': return "double";
                case 'F': return "float";
                case 'I': return "int";
                case 'J': return "long";
                case 'S': return "short";
                case 'Z': return "boolean";
                default: throw new IllegalArgumentException("Invalid primitive descriptor '" + desc + "'");
            }
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
            return type;
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
    
    private static final Pattern SRG_PARAM = Pattern.compile("(?:p_)?(\\d+)_(\\d)_?");
    private static final Pattern DESC_PARAM = Pattern.compile("L[a-zA-Z$\\/]+;|\\[*[BCDFIJSZ]");

    public Collection<IMemberInfo> lookup(MappingType type, String name) {
        
        Collection<IMCPMapping> mappingsForType = mappings.get(type);
        String[] hierarchy = null;
        if (name.contains(".")) {
            hierarchy = name.split("\\.");
            name = hierarchy[hierarchy.length - 1];
        }

        final String lookup = name;
        
        // Find all matches in srgs and mcp data
        Map<String, ISrgMapping> srgMatches = srgs.lookup(type, lookup).stream().collect(Collectors.toMap(ISrgMapping::getSRG, Function.identity()));
        List<IMCPMapping> mcpMatches = mappingsForType.stream().filter(m -> m.getSRG().contains(lookup) || m.getMCP().equals(lookup)).collect(Collectors.toList());

        Map<String, IMemberInfo> srgToInfo = new LinkedHashMap<>();

        // Special case for handling unmapped params
        if (mcpMatches.isEmpty() && type == MappingType.PARAM) {
            Matcher m = SRG_PARAM.matcher(name);
            if (m.matches()) {
                Collection<IMemberInfo> potentialMethods = lookup(MappingType.METHOD, m.group(1));
                for (IMemberInfo method : potentialMethods) {
                    int param = 1;
                    String desc = method.getDesc();
                    Matcher paramMatcher = DESC_PARAM.matcher(desc.substring(1, desc.lastIndexOf(')')));
                    while (paramMatcher.find()) {
                        if (Integer.toString(param).equals(m.group(2))) {
                            srgToInfo.put(method.getSRG(), new MemberInfoParam(method, null, param));
                        }
                        param++;
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
                Matcher m = SRG_PARAM.matcher(mcp.getSRG());
                if (!m.matches()) {
                    throw new IllegalStateException("SRG is invalid: " + mcp.getSRG());
                }
                IMemberInfo method = lookup(MappingType.METHOD, m.replaceAll("$1")).iterator().next();
                m.reset();
                m.matches();
                srgToInfo.put(mcp.getSRG(), new MemberInfoParam(method, mcp, Integer.parseInt(m.group(2))));
            } else {
                ISrgMapping srg = srgMatches.get(mcp.getSRG());
                if (srg == null) {
                    srg = srgs.lookup(type, mcp.getSRG()).get(0);
                }
                if (parent == null || Strings.nullToEmpty(srg.getOwner()).endsWith(parent)) {
                    srgToInfo.put(mcp.getSRG(), new MemberInfo(srg, mcp));
                }
            }
        }
        
        // Remove srg matches that are mapped
        for (Entry<String, ISrgMapping> e : srgMatches.entrySet()) {
            if (!srgToInfo.containsKey(e.getKey())) {
                if (parent == null || Strings.nullToEmpty(e.getValue().getOwner()).endsWith(parent)) {
                    srgToInfo.put(e.getKey(), new MemberInfo(e.getValue(), null));
                }
            }
        }
        
        return srgToInfo.values();
    }
}
