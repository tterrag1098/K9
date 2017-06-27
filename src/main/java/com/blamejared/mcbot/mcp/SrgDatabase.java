package com.blamejared.mcbot.mcp;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

import org.apache.commons.io.IOUtils;

import com.blamejared.mcbot.mcp.ISrgMapping.MappingType;
import com.google.common.base.Charsets;
import com.google.common.base.Throwables;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

public class SrgDatabase {
    
    private static final Pattern SRG_PATTERN = Pattern.compile("^(CL|FD|MD):\\s(.+)$");
    
    private final Table<MappingType, String, ISrgMapping> srgs = HashBasedTable.create();
    
    private final File zip;
    
    public SrgDatabase(String mcver) {
        this.zip = Paths.get(".", "data", mcver, "srgs", "mcp-" + mcver + "-srg.zip").toFile();
        try {
            reload();
        } catch (IOException e) {
            Throwables.propagate(e);
        }
    }

    public void reload() throws IOException {
        srgs.clear();
        ZipFile zipfile = new ZipFile(zip);
        List<String> srglines;
        List<String> excLines;
        try {
            srglines = IOUtils.readLines(zipfile.getInputStream(zipfile.getEntry("joined.srg")), Charsets.UTF_8);
            excLines = IOUtils.readLines(zipfile.getInputStream(zipfile.getEntry("joined.exc")), Charsets.UTF_8);
        } finally {
            zipfile.close();
        }
        Matcher matcher = SRG_PATTERN.matcher("");
        SrgMappingFactory factory = new SrgMappingFactory();
        for (String srg : srglines) {
            matcher.reset(srg);
            if (matcher.matches()) {
                ISrgMapping mapping = factory.create(Arrays.stream(MappingType.values()).filter(t -> Optional.ofNullable(t.getSrgKey()).orElse("").equals(matcher.group(1))).findFirst().get(), matcher.group(2));
                if (!srgs.contains(mapping.getType(), mapping.getSRG())) {
                    srgs.put(mapping.getType(), mapping.getSRG(), mapping);
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
                        @SuppressWarnings("null") 
                        ISrgMapping mapping = new SrgMappingFactory.ParamMapping(param, owner);
                        if(!srgs.contains(mapping.getType(), mapping.getSRG())) {
                            srgs.put(mapping.getType(), mapping.getSRG(), mapping);
                        }
                    }
                } else {
                    ISrgMapping mapping = new SrgMappingFactory.ParamMapping(line, owner);
                    if(!srgs.contains(mapping.getType(), mapping.getSRG())) {
                        srgs.put(mapping.getType(), mapping.getSRG(), mapping);
                    }
                }
            }
        }
    }

    public List<ISrgMapping> lookup(MappingType type, String name) {
        ISrgMapping ret = srgs.get(type, name);
        if (ret == null) {
            Predicate<Entry<String, ISrgMapping>> lookupFunc;
            if (type == MappingType.CLASS) {
                lookupFunc = e -> e.getKey().substring(e.getKey().lastIndexOf('/') + 1).equals(name) || e.getValue().getNotch().equals(name);
            } else {
                lookupFunc = e -> e.getKey().contains(name);
            }
            List<ISrgMapping> found = srgs.row(type).entrySet().stream().filter(lookupFunc).map(Entry::getValue).collect(Collectors.toList());
            return found;
        }
        return Collections.singletonList(ret);
    }
}
