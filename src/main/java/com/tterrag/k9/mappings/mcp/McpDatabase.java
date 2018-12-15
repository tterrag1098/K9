package com.tterrag.k9.mappings.mcp;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.tterrag.k9.mappings.AbstractMappingDatabase;
import com.tterrag.k9.mappings.FastIntLookupDatabase;
import com.tterrag.k9.mappings.Mapping;
import com.tterrag.k9.mappings.MappingDatabase;
import com.tterrag.k9.mappings.MappingType;
import com.tterrag.k9.mappings.NameType;
import com.tterrag.k9.mappings.NoSuchVersionException;
import com.tterrag.k9.mappings.mcp.McpMapping.Side;
import com.tterrag.k9.util.NonNull;
import com.tterrag.k9.util.NullHelper;
import com.tterrag.k9.util.Nullable;
import com.tterrag.k9.util.Patterns;

import clojure.asm.Type;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.experimental.Delegate;

public class McpDatabase extends FastIntLookupDatabase<McpMapping> {
    
    @RequiredArgsConstructor
    private static class ParamMapping implements McpMapping {
        
        interface Excludes {
            MappingType getType();
            String getOwner();
            String getMemberClass();
        }
        
        private final Mapping method;
        
        @Delegate(excludes = Excludes.class)
        private final Mapping parent;
        
        private final int paramID;
        private final Type type;
        
        @Getter(onMethod = @__({@Override}))
        private final String comment;
        
        @Getter(onMethod = @__({@Override}))
        private final Side side;
        
        public ParamMapping(IntermediateMapping method, Mapping parent, int paramID, String comment, Side side) {
            this(method, parent, paramID, findType(method, paramID), comment, side);
        }
        
        public static Type findType(IntermediateMapping method, int param) {
            if (!method.isStatic()) {
                if (param == 0) {
                    throw new IllegalArgumentException("Cannot use param 0 for non-static method: " + method + "  param: " + param);
                }
                param--; // "this" counts as param 0 
            }
            String desc = method.getDesc();
            List<Type> args = Lists.newArrayList(Type.getArgumentTypes(desc));
            // double and long count twice....because java
            for (int i = 0; i < args.size(); i++) {
                Type type = args.get(i);
                if (type.getSort() == Type.DOUBLE || type.getSort() == Type.LONG) {
                    args.add(i++, type); // duplicate this parameter for easy lookup
                }
            }
            if (param >= args.size()) {
                throw new IllegalArgumentException("Could not find type name. Method: " + method + "  param: " + param);
            }
            return args.get(param);
        }
        
        @Override
        public MappingType getType() {
            return MappingType.PARAM;
        }
    
        @Override
        public @Nullable String getOwner() {
            String name = method.getName();
            return method.getOwner() + "." + (name == null ? method.getIntermediate() : name);
        }
        
        @Override
        public @Nullable String getMemberClass() {
            return type.getClassName();
        }
        
        @Override
        public Mapping getParent() {
            return method;
        }
    }
    
    @Value
    @Getter(onMethod = @__({@Override}))
    private static class CsvMapping implements McpMapping {
        MappingType type;
        
        String original = ""; // Unused
        
        String intermediate, name;
        
        String comment;
        
        Side side;
    }
    
    public McpDatabase(String mcver) throws NoSuchVersionException {
        super(mcver);
    }
    
    
    private final SrgDatabase srgs = new SrgDatabase(getMinecraftVersion());

    @Override
    protected List<McpMapping> parseMappings() throws NoSuchVersionException, IOException {
        srgs.reload();
        
        File folder = McpDownloader.INSTANCE.getDataFolder().resolve(Paths.get(getMinecraftVersion(), "mappings")).toFile();
        if (!folder.exists()) {
            throw new NoSuchVersionException(getMinecraftVersion());
        }
        File zip = folder.listFiles()[0];
        ZipFile zipfile = new ZipFile(zip);

        try {
            MappingDatabase<CsvMapping> tempDb = new AbstractMappingDatabase<CsvMapping>(getMinecraftVersion()) {
                
                @Override
                protected List<CsvMapping> parseMappings() throws NoSuchVersionException, IOException {
                    return NullHelper.notnullJ(Arrays.stream(MappingType.values())
                                 .filter(type -> type.getCsvName() != null)
                                 .flatMap(type -> {
                                    List<String> lines;
                                    try {
                                        lines = IOUtils.readLines(zipfile.getInputStream(zipfile.getEntry(type.getCsvName() + ".csv")), Charsets.UTF_8);
                                    } catch (IOException e) {
                                        throw new RuntimeException(e);
                                    }
                
                                    lines.remove(0); // Remove header line
                
                                    return lines.stream()
                                                .map(line -> {
                                                    String[] info = line.split(",", -1);
                                                    String comment = info.length > 3 ? Joiner.on(',').join(ArrayUtils.subarray(info, 3, info.length)) : "";
                                                    return new CsvMapping(type, info[0], info[1], comment, Side.values()[Integer.valueOf(info[2])]);
                                                });
                                })
                                .collect(Collectors.toList()), "Stream#collect");
                }
            };
            tempDb.reload();

            // Add all srg mappings to this, if unmapped just use null/defaults
            for (MappingType type : MappingType.values()) {
                Collection<com.tterrag.k9.mappings.mcp.SrgMapping> byType = srgs.lookup(type);
                for (SrgMapping srg : byType) {
                    McpMapping mapping;
                    Optional<@NonNull CsvMapping> csv = tempDb.lookup(type, srg.getIntermediate()).stream().sorted(Comparator.comparingInt(m -> m.getIntermediate().length())).findFirst();
                    mapping = new McpMapping.Impl(type, srg.getOriginal(), srg.getIntermediate(), csv.map(CsvMapping::getName).orElse(null), srg.getDesc(), srg.getOwner(), srg.isStatic(), csv.map(CsvMapping::getComment).orElse(null), csv.map(CsvMapping::getSide).orElse(Side.BOTH));
                    addMapping(mapping);
                }
            }
            
            // Params are not part of srgs, so add them after the fact using the merged data as a source
            for (CsvMapping csv : tempDb.lookup(MappingType.PARAM)) {
                Matcher m = Patterns.SRG_PARAM.matcher(csv.getIntermediate());
                if (m.matches()) {
                    McpMapping method = lookup(MappingType.METHOD, m.replaceAll("$1")).stream().findFirst().orElseThrow(() -> new IllegalArgumentException("No method found for param: " + csv));
                    m.reset();
                    m.matches();
                    addMapping(new ParamMapping(method, csv, Integer.parseInt(m.group(2)), csv.getComment(), csv.getSide()));
                } else {
                    addMapping(new McpMapping.Impl(MappingType.PARAM, "", csv.getIntermediate(), csv.getName(), null, null, csv.isStatic(), csv.getComment(), csv.getSide()));
                }
            }
            
        } finally {
            zipfile.close();
        }
        
        return Collections.emptyList(); // We must add the mappings as we go so that params can find methods, so this return is not needed
    }
    
    private boolean matches(McpMapping m, String lookup) {
        String[] byUnderscores = m.getIntermediate().split("_");
        if (byUnderscores.length > 1 && byUnderscores[1].equals(lookup)) {
            return true;
        } else {
            return m.getIntermediate().equals(lookup) || (m.getName() != null && m.getName().equals(lookup));
        }
    }
    
    public Collection<McpMapping> lookup(MappingType type, String name) {
        Collection<McpMapping> fast = lookupFastSrg(type, name);
        if (!fast.isEmpty()) {
            return fast;
        }
        Collection<McpMapping> mappingsForType = getTable(NameType.INTERMEDIATE, type).values();
        String[] hierarchy = null;
        if (name.contains(".")) {
            hierarchy = name.split("\\.");
            name = hierarchy[hierarchy.length - 1];
        }

        final String lookup = name;
        
        // Find all matches in srgs and mcp data
        List<McpMapping> mcpMatches = mappingsForType.stream().filter(m -> matches(m, lookup)).collect(Collectors.toList());
        
        List<McpMapping> ret = new ArrayList<>();

        // Special case for handling unmapped params
        if (mcpMatches.isEmpty() && type == MappingType.PARAM) {
            Matcher m = Patterns.SRG_PARAM.matcher(name);
            if (m.matches()) {
                Collection<SrgMapping> potentialMethods = srgs.lookup(MappingType.METHOD, m.group(1));
                for (SrgMapping method : potentialMethods) {
                    int param = 1;
                    String desc = method.getDesc();
                    Type[] params = Type.getArgumentTypes(desc);
                    if (param < params.length) {
                        ret.add(new ParamMapping(method, method, param, "", Side.BOTH));
                    }
                }
            } else {
                return Collections.emptyList();
            }
        } else {
            ret.addAll(mcpMatches);
        }

        // Ignore those which do not match the supplied hierarchy
//        final String parent = hierarchy == null ? null : hierarchy[0];
//        for (McpMapping mcp : mcpMatches) {
//            if (mcp.getType() == MappingType.PARAM) {
//
//            } else {
//                ret.add(mcp);
//            }
//        }
        
        return ret;
    }
}
