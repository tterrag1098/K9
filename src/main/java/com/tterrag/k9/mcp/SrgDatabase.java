package com.tterrag.k9.mcp;

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
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import org.apache.commons.io.IOUtils;

import com.google.common.base.Charsets;
import com.google.common.base.Throwables;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.tterrag.k9.mcp.ISrgMapping.MappingType;
import com.tterrag.k9.mcp.SrgMappingFactory.ClassMapping;
import com.tterrag.k9.mcp.SrgMappingFactory.FieldMapping;
import com.tterrag.k9.mcp.SrgMappingFactory.MethodMapping;
import com.tterrag.k9.util.NonNull;
import com.tterrag.k9.util.NullHelper;

public class SrgDatabase {
    
    private static final Pattern SRG_PATTERN = Pattern.compile("^(CL|FD|MD):\\s(.+)$");
    
    private final Table<MappingType, String, ISrgMapping> srgs = HashBasedTable.create();
    
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
    
    private static final Pattern NOTCH_SIGNATURE_ENTRY = Pattern.compile("([a-z$]+);");
    
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
                        public @NonNull String getSrgDesc() {
                            if (srgDesc != null) {
                                return srgDesc;
                            }
                            Matcher descReplacer = NOTCH_SIGNATURE_ENTRY.matcher(getNotchDesc());
                            StringBuffer srgDesc = new StringBuffer();
                            while (descReplacer.find()) {
                                descReplacer.appendReplacement(srgDesc, lookup(MappingType.CLASS, descReplacer.group(1)).get(0).getSRG());
                            }
                            descReplacer.appendTail(srgDesc);
                            return (this.srgDesc = srgDesc.toString());
                        }
                    };
                }
            }
            srgs.put(mapping.getType(), mapping.getSRG(), mapping);
        }
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
        Matcher matcher = SRG_PATTERN.matcher("");
        SrgMappingFactory factory = new SrgMappingFactory();
        for (String srg : srglines) {
            matcher.reset(srg);
            if (matcher.matches()) {
                ISrgMapping mapping = factory.create(Arrays.stream(MappingType.values()).filter(t -> Optional.ofNullable(t.getSrgKey()).orElse("").equals(matcher.group(1))).findFirst().get(), matcher.group(2));
                ISrgMapping existing = srgs.get(mapping.getType(), mapping.getSRG());
                String owner = existing != null ? existing.getOwner() : null;
                String newOwner = mapping.getOwner();
                if (existing == null || (owner != null && newOwner != null && owner.length() > newOwner.length())) {
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
                        ISrgMapping mapping = new SrgMappingFactory.ParamMapping(NullHelper.notnullJ(param, "String#split"), owner);
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
