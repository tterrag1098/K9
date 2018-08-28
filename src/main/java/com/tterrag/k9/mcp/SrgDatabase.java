package com.tterrag.k9.mcp;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import org.apache.commons.io.IOUtils;

import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.tterrag.k9.mcp.ISrgMapping.MappingType;
import com.tterrag.k9.mcp.SrgMappingFactory.ClassMapping;
import com.tterrag.k9.mcp.SrgMappingFactory.FieldMapping;
import com.tterrag.k9.mcp.SrgMappingFactory.MethodMapping;
import com.tterrag.k9.util.NonNull;
import com.tterrag.k9.util.NullHelper;
import com.tterrag.k9.util.Patterns;

import clojure.asm.Type;

public class SrgDatabase {
    
    
    private final Map<MappingType, ListMultimap<String, ISrgMapping>> srgs = new EnumMap<>(MappingType.class);
    
    private final File zip;
    
    public SrgDatabase(String mcver) throws NoSuchVersionException {
        File zip = Paths.get(".", "data", mcver, "srgs", "mcp-" + mcver + "-srg.zip").toFile();
        if (!zip.exists()) {
            zip = Paths.get(".", "data", mcver, "srgs", "mcp_config-" + mcver + ".zip").toFile();
            if (!zip.exists()) {
                throw new NoSuchVersionException(mcver);
            }
        }
        this.zip = zip;
        try {
            reload();
        } catch (IOException e) {
            Throwables.propagate(e);
        }
    }

    public void reload() throws IOException {
        srgs.clear();
        if (zip.getName().contains("mcp_config")) {
            parseTSRG();
        } else {
            parseSRG();
        }
    }
        
    private void parseTSRG() throws ZipException, IOException {
        List<String> tsrglines;
        try (ZipFile zipfile = new ZipFile(zip)){
            tsrglines = IOUtils.readLines(zipfile.getInputStream(zipfile.getEntry("config/joined.tsrg")), Charsets.UTF_8);
        }
                
        ClassMapping currentClass = null;
        for (String line : tsrglines) {
            ISrgMapping mapping;
            if (!line.startsWith("\t")) {
                String[] names = line.split(" ");
                mapping = currentClass = new ClassMapping(names[0], names[1]);
            } else {
                String[] data = line.substring(1).split(" ");
                if (data.length == 2) {
                    mapping = new FieldMapping(data[0], data[1], currentClass.getSRG());
                } else {
                    mapping = new MethodMapping(data[0], data[1], data[2], null, currentClass.getSRG()) {
                        
                        private String srgDesc;
                      
                        @Override
                        public @NonNull String getDesc() {
                            if (srgDesc != null) {
                                return srgDesc;
                            }
                            Type ret = Type.getReturnType(notchDesc);
                            Type[] args = Type.getArgumentTypes(notchDesc);
                            for (int i = 0; i < args.length; i++) {
                                args[i] = mapType(args[i]);
                            }
                            ret = mapType(ret);
                            return (this.srgDesc = Type.getMethodDescriptor(ret, args));
                        }
                        
                        private Type mapType(Type notch) {
                            Type type = notch;
                            if (notch.getSort() == Type.ARRAY) {
                                type = type.getElementType();
                            }
                            if (type.getSort() == Type.OBJECT) {
                                String name = notch.getInternalName();
                                if (Patterns.NOTCH_PARAM.matcher(name).matches()) {
                                    List<ISrgMapping> matches = lookup(MappingType.CLASS, name);
                                    if (!matches.isEmpty()) {
                                        return Type.getType("L" + matches.get(0).getSRG() + ";");
                                    }
                                }
                                if (notch.getSort() == Type.ARRAY) {
                                    type = Type.getType(Strings.repeat("[", notch.getDimensions()) + type.getDescriptor());
                                }
                            }

                            return type;
                        }
                    };
                }
            }
            srgs.computeIfAbsent(mapping.getType(), t -> ArrayListMultimap.create()).put(mapping.getSRG(), mapping);
        }
    }
    
    private ListMultimap<String, ISrgMapping> getTable(MappingType type) {
        return srgs.computeIfAbsent(type, t -> ArrayListMultimap.create());
    }

    private void parseSRG() throws ZipException, IOException {
        ZipFile zipfile = new ZipFile(zip);
        List<String> srglines;
        List<String> excLines;
        try {
            srglines = IOUtils.readLines(zipfile.getInputStream(zipfile.getEntry("joined.srg")), Charsets.UTF_8);
            excLines = IOUtils.readLines(zipfile.getInputStream(zipfile.getEntry("joined.exc")), Charsets.UTF_8);
        } finally {
            zipfile.close();
        }
        Matcher matcher = Patterns.SRG_PATTERN.matcher("");
        SrgMappingFactory factory = new SrgMappingFactory();
        for (String srg : srglines) {
            matcher.reset(srg);
            if (matcher.matches()) {
                ISrgMapping mapping = factory.create(Arrays.stream(MappingType.values()).filter(t -> Optional.ofNullable(t.getSrgKey()).orElse("").equals(matcher.group(1))).findFirst().get(), matcher.group(2));
                List<ISrgMapping> existing = getTable(mapping.getType()).get(mapping.getSRG());
                String owner = existing.isEmpty() ? null : existing.get(0).getOwner();
                String newOwner = mapping.getOwner();
                if (existing.isEmpty() || (owner != null && newOwner != null && owner.length() > newOwner.length())) {
                    getTable(mapping.getType()).put(mapping.getSRG(), mapping);
                }
            }
        }
        for(String exc : excLines) {
            if(exc.contains("V=")) {
                String line = exc.split("V=")[1].substring(1);
                String owner = exc.split("\\(")[0].substring(exc.split("\\(")[0].lastIndexOf("/")+1);
                if(line.split(",").length > 0) {
                    String[] params = line.split(",");
                    for(String param : params) {
                        ISrgMapping mapping = new SrgMappingFactory.ParamMapping(NullHelper.notnullJ(param, "String#split"), owner);
                        if(getTable(mapping.getType()).get(mapping.getSRG()).isEmpty()) {
                            getTable(mapping.getType()).put(mapping.getSRG(), mapping);
                        }
                    }
                } else {
                    ISrgMapping mapping = new SrgMappingFactory.ParamMapping(line, owner);
                    if(getTable(mapping.getType()).get(mapping.getSRG()).isEmpty()) {
                        getTable(mapping.getType()).put(mapping.getSRG(), mapping);
                    }
                }
            }
        }
    }

    @NonNull
    public List<ISrgMapping> lookup(MappingType type, String name) {
        List<ISrgMapping> ret = getTable(type).get(name);
        if (ret.isEmpty()) {
            Predicate<Entry<String, ISrgMapping>> lookupFunc;
            if (type == MappingType.CLASS) {
                lookupFunc = e -> e.getKey().substring(e.getKey().lastIndexOf('/') + 1).equals(name) || e.getValue().getNotch().equals(name);
            } else {
                lookupFunc = e -> e.getKey().contains(name) || e.getValue().getNotch().equals(name);
            }
            List<ISrgMapping> found = getTable(type).entries().stream().filter(lookupFunc).map(Entry::getValue).collect(Collectors.toList());
            return found;
        }
        return ret;
    }
}
