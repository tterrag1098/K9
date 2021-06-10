package com.tterrag.k9.mappings.mcp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.zip.ZipFile;

import org.apache.commons.io.IOUtils;

import com.google.common.base.Charsets;
import com.tterrag.k9.mappings.MappingType;
import com.tterrag.k9.mappings.Parser;
import com.tterrag.k9.util.NullHelper;
import com.tterrag.k9.util.Patterns;
import com.tterrag.k9.util.annotation.NonNull;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SrgParser implements Parser<ZipFile, SrgMapping> {
    
    private final SrgDatabase db;
    
    @Override
    public List<SrgMapping> parse(ZipFile zip) throws IOException {
        List<String> staticMethods;
        List<String> srglines;
        List<String> excLines;
        try {
            staticMethods = IOUtils.readLines(zip.getInputStream(zip.getEntry("static_methods.txt")), Charsets.UTF_8);
            srglines = IOUtils.readLines(zip.getInputStream(zip.getEntry("joined.srg")), Charsets.UTF_8);
            excLines = IOUtils.readLines(zip.getInputStream(zip.getEntry("joined.exc")), Charsets.UTF_8);
        } finally {
            zip.close();
        }
        
        List<SrgMapping> ret = new ArrayList<>();
        Matcher matcher = Patterns.SRG_PATTERN.matcher("");

        for (String srg : srglines) {
            matcher.reset(srg);
            if (matcher.matches()) {
                MappingType type = Arrays.stream(MappingType.values()).filter(t -> Optional.ofNullable(t.getSrgKey()).orElse("").equals(matcher.group(1))).findFirst().get();
                SrgMapping mapping = create(type, matcher.group(2), staticMethods);
                ret.add(mapping);
            }
        }
        for(String exc : excLines) {
            if(exc.contains("V=")) {
                String line = exc.split("V=")[1].substring(1);
                String owner = exc.split("\\(")[0].substring(exc.split("\\(")[0].lastIndexOf("/")+1);
                if(line.split(",").length > 0) {
                    String[] params = line.split(",");
                    for(String param : params) {
                        SrgMapping mapping = new SrgMapping(db, MappingType.PARAM, "", param, null, null, owner);
                        ret.add(mapping);
                    }
                } else {
                    SrgMapping mapping = new SrgMapping(db, MappingType.PARAM, "", line, null, null, owner);
                    ret.add(mapping);
                }
            }
        }
    
        return ret;
    }
    
    private SrgMapping create(MappingType type, String line, List<String> staticMethods) {
        @NonNull String[] data = NullHelper.notnullJ(line.trim().split("\\s+"), "String#split");
        switch(type) {
            case CLASS:
                return new SrgMapping(db, type, data[0], data[1], null, null, null);
            case FIELD:
                String owner = data[1].substring(0, data[1].lastIndexOf('/'));
                return new SrgMapping(db, type,
                        NullHelper.notnullJ(data[0].substring(data[0].lastIndexOf('/') + 1), "String#substring"), 
                        NullHelper.notnullJ(data[1].replace(owner + "/", ""), "String#replace"),
                        null, null,
                        owner);
            case METHOD:
                owner = data[2].substring(0, data[2].lastIndexOf('/'));
                String srg = NullHelper.notnullJ(data[2].replace(owner + "/", ""), "String#replace");
                SrgMapping ret = new SrgMapping(db, type,
                        NullHelper.notnullJ(data[0].substring(data[0].lastIndexOf('/') + 1), "String#substring"), 
                        srg,
                        data[1],
                        data[3],
                        owner);
                if (staticMethods.contains(srg)) {
                    ret.setStatic(true);
                }
            default:
                throw new IllegalArgumentException("Invalid type");
        }
    }

}
