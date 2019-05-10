package com.tterrag.k9.mappings.mcp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipFile;

import org.apache.commons.io.IOUtils;

import com.google.common.base.Charsets;
import com.google.common.collect.Sets;
import com.tterrag.k9.mappings.MappingType;
import com.tterrag.k9.mappings.NameType;
import com.tterrag.k9.mappings.Parser;
import com.tterrag.k9.mappings.SignatureHelper;
import com.tterrag.k9.util.annotation.NonNull;
import com.tterrag.k9.util.annotation.Nullable;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class TsrgParser implements Parser<ZipFile, SrgMapping> {
    
    private static final SignatureHelper sigHelper = new SignatureHelper();
    
    private final SrgDatabase db;

    @Override
    public List<SrgMapping> parse(ZipFile zip) throws IOException {
        Set<String> staticMethods;
        List<String> lines;
        try {
            staticMethods = Sets.newHashSet(IOUtils.readLines(zip.getInputStream(zip.getEntry("config/static_methods.txt")), Charsets.UTF_8));
            lines = IOUtils.readLines(zip.getInputStream(zip.getEntry("config/joined.tsrg")), Charsets.UTF_8);
        } finally {
            zip.close();
        }
        List<SrgMapping> ret = new ArrayList<>();
        SrgMapping currentClass = null;
        for (String line : lines) {
            SrgMapping mapping;
            if (!line.startsWith("\t")) {
                String[] names = line.split(" ");
                mapping = currentClass = new SrgMapping(MappingType.CLASS, names[0], names[1], null, null, null, false);
            } else {
                String[] data = line.substring(1).split(" ");
                if (data.length == 2) {
                    mapping = new SrgMapping(MappingType.FIELD, data[0], data[1], null, null, currentClass.getIntermediate(), false);
                } else {
                    mapping = new SrgMapping(MappingType.METHOD, data[0], data[2], data[1], null, currentClass.getIntermediate(), staticMethods.contains(data[2])) {
    
                        private @Nullable String srgDesc;
    
                        @Override
                        public @NonNull String getIntermediateDesc() {
                            if (srgDesc == null) {
                                srgDesc = sigHelper.mapSignature(NameType.INTERMEDIATE, getOriginalDesc(), this, db);
                            }
                            return srgDesc;
                        }
                    };
                }
            }
            ret.add(mapping);
        }
        return ret;
    }

}
