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
import com.tterrag.k9.util.annotation.NonNull;
import com.tterrag.k9.util.NullHelper;
import com.tterrag.k9.util.annotation.Nullable;
import com.tterrag.k9.util.Patterns;

import clojure.asm.Type;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;

@Slf4j
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
        
        private final Type type;
        
        @Getter(onMethod = @__(@Override))
        private final String comment;
        
        @Getter(onMethod = @__(@Override))
        private final Side side;
        
        public ParamMapping(IntermediateMapping method, Mapping parent, int paramID, String comment, Side side) {
            this(method, parent, findType(method, paramID), comment, side);
        }
        
        public static Type findType(IntermediateMapping method, int param) {
            if (!method.isStatic()) {
                if (param == 0) {
                    log.error("Cannot use param 0 for non-static method: " + method + "  param: " + param);
                    return Type.LONG_TYPE;
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
                log.error("Could not find type name. Method: " + method + "  param: " + param);
                return Type.BOOLEAN_TYPE;
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
    private static class CsvMapping implements McpMapping {
        
        @Getter(onMethod = @__(@Override))
        MappingType type;
        
        @Getter(onMethod = @__(@Override))
        String original = "\u2603";
        
        @Getter(onMethod = @__(@Override))
        String intermediate, name;
        
        @Getter(onMethod = @__(@Override))
        String comment;
        
        @Getter(onMethod = @__(@Override))
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
                Collection<com.tterrag.k9.mappings.mcp.SrgMapping> byType = srgs.lookup(NameType.INTERMEDIATE, type);
                for (SrgMapping srg : byType) {
                    McpMapping mapping;
                    Optional<@NonNull CsvMapping> csv = tempDb.lookup(NameType.INTERMEDIATE, type, srg.getIntermediate()).stream().sorted(Comparator.comparingInt(m -> m.getIntermediate().length())).findFirst();
                    mapping = new McpMapping.Impl(this, type, srg.getOriginal(), srg.getIntermediate(), csv.map(CsvMapping::getName).orElse(null), srg.getDesc(), srg.getOwner(), srg.isStatic(), csv.map(CsvMapping::getComment).orElse(""), csv.map(CsvMapping::getSide).orElse(Side.BOTH));
                    addMapping(mapping);
                }
            }
            
            // Params are not part of srgs, so add them after the fact using the merged data as a source
            for (CsvMapping csv : tempDb.lookup(NameType.INTERMEDIATE, MappingType.PARAM)) {
                Matcher m = Patterns.SRG_PARAM.matcher(csv.getIntermediate());
                if (m.matches()) {
                    lookup(NameType.INTERMEDIATE, MappingType.METHOD, m.replaceAll("$1")).stream().findFirst().ifPresent(method -> {
                        m.reset();
                        m.matches();
                        addMapping(new ParamMapping(method, csv, Integer.parseInt(m.group(2)), csv.getComment(), csv.getSide()));
                    });
                } else {
                    addMapping(new McpMapping.Impl(this, MappingType.PARAM, csv.getOriginal(), csv.getIntermediate(), csv.getName(), null, null, csv.isStatic(), csv.getComment(), csv.getSide()));
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
    
    private boolean checkOwner(String ownerMatch, McpMapping mapping) {
        String owner = mapping.getOwner();
        if (owner == null) {
            return false;
        }
        for (NameType type : NameType.values()) {
            String name = type.get(mapping);
            if (name != null) {
                String fullOwner = owner + "." + name;
                if (fullOwner.endsWith(ownerMatch)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    private int[] getParamIds(boolean isStatic, Type[] params) {
        int[] ret = new int[params.length];
        int id = isStatic ? 0 : 1;
        for (int i = 0; i < params.length; i++) {
            ret[i] = id;
            Type arg = params[i];
            if (arg.getSort() == Type.DOUBLE || arg.getSort() == Type.LONG) {
                id++;
            }
            id++;
        }
        return ret;
    }
    
    private McpMapping getSyntheticParam(McpMapping method, String id, int param) {
        return new ParamMapping(method, new CsvMapping(MappingType.PARAM, "p_" + id + "_" + param + "_", null, "", method.getSide()), param, "", method.getSide());
    }
    
    @Override
    public Collection<McpMapping> lookup(NameType by, MappingType type, String name) {
        if (by != NameType.INTERMEDIATE || type != MappingType.PARAM) {
            return super.lookup(by, type, name);
        }
        
        Collection<McpMapping> matchingParams = super.lookup(by, type, name);
        
        final String ownerMatch;
        int lastDot = name.lastIndexOf('.');
        if (lastDot != -1) {
            ownerMatch = name.substring(0, lastDot);
            name = name.substring(lastDot + 1);
        } else {
            ownerMatch = null;
        }
        
        // Special case for unmapped params
        Matcher matcher = Patterns.SRG_PARAM_FUZZY.matcher(name);
        if (matchingParams.isEmpty() && matcher.matches()) {
            matchingParams = new ArrayList<>(matchingParams);

            Collection<McpMapping> matchingMethods = super.lookup(NameType.INTERMEDIATE, MappingType.METHOD, matcher.group(1)).stream()
                    .filter(m -> ownerMatch == null || checkOwner(ownerMatch, m))
                    .collect(Collectors.toList());
            
            for (McpMapping method : matchingMethods) {
                String paramIdMatch = matcher.group(2);
                Integer paramId = paramIdMatch == null ? null : Integer.parseInt(paramIdMatch);
                String desc = method.getDesc();
                Type[] params = Type.getArgumentTypes(desc);
                int[] ids = getParamIds(method.isStatic(), params);
                if (paramId == null) {
                    for (int id : ids) {
                        matchingParams.add(getSyntheticParam(method, matcher.group(1), id));
                    }
                } else if (ArrayUtils.contains(ids, paramId)) {
                    matchingParams.add(getSyntheticParam(method, matcher.group(1), paramId));
                }
            }
        }
        
        return matchingParams;
    }
}
