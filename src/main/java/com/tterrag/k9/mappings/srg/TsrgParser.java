package com.tterrag.k9.mappings.srg;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
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
            ZipEntry staticMethodsEntry = zip.getEntry("config/static_methods.txt");
            // This file no longer exists in tsrgv2 versions (starting at 1.17)
            if (staticMethodsEntry != null) {
                staticMethods = Sets.newHashSet(IOUtils.readLines(zip.getInputStream(staticMethodsEntry), Charsets.UTF_8));
            } else {
                staticMethods = Collections.emptySet();
            }
            lines = IOUtils.readLines(zip.getInputStream(zip.getEntry("config/joined.tsrg")), Charsets.UTF_8);
        } finally {
            zip.close();
        }
        List<SrgMapping> ret = new ArrayList<>();
        SrgMapping currentClass = null;
        int fieldNumber = 2;
        for (String line : lines) {
            SrgMapping mapping;
            if (line.startsWith("tsrg2 ")) {
                // TSRGv2 support, skip header line and check that this is standard name set
                if (!line.startsWith("tsrg2 obf srg")) {
                    throw new UnsupportedOperationException("Custom names in tsrgv2 is not supported yet");
                }
                // So we can support extra names on the end, e.g. "obf srg id" which is present in newer MCPConfig exports
                fieldNumber = line.split(" ").length - 1;
                continue;
            }
            if (!line.startsWith("\t")) {
                String[] names = line.split(" ");
                mapping = currentClass = new SrgMapping(db, MappingType.CLASS, names[0], names[1], null, null, null);
            } else if (!line.startsWith("\t\t")) {
                String[] data = line.substring(1).split(" ");
                if (data.length == fieldNumber) {
                    mapping = new SrgMapping(db, MappingType.FIELD, data[0], data[1], null, null, currentClass.getIntermediate());
                } else {
                    // TSRGv2 Support
                    MappingType type = data[1].startsWith("(") ? MappingType.METHOD : MappingType.FIELD;
                    mapping = new SrgMapping(db, type, data[0], data[2], data[1], null, currentClass.getIntermediate()) {
    
                        private @Nullable String srgDesc;
    
                        @Override
                        public @NonNull String getIntermediateDesc() {
                            if (srgDesc == null) {
                                srgDesc = sigHelper.mapSignature(NameType.INTERMEDIATE, getOriginalDesc(), this, db);
                            }
                            return srgDesc;
                        }
                    };
                    if (staticMethods.contains(data[2])) {
                        mapping.setStatic(true);
                    }
                }
            } else {
                if (line.trim().equals("static")) {
                    // Mark the previous method mapping as static
                    ret.get(ret.size() - 1).setStatic(true);
                }
                // NO-OP (params)
                continue;
            }
            ret.add(mapping);
        }
        return ret;
    }

}
