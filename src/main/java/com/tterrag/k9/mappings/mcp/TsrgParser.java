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
            if (line.startsWith("tsrg2 ")) {
                // TSRGv2 support, skip header line and check that this is standard name set
                if (!line.startsWith("tsrg2 obf srg")) {
                    throw new UnsupportedOperationException("Custom names in tsrgv2 is not supported yet");
                }
                continue;
            }
            if (!line.startsWith("\t")) {
                String[] names = line.split(" ");
                mapping = currentClass = new SrgMapping(db, MappingType.CLASS, names[0], names[1], null, null, null, false);
            } else if (!line.startsWith("\t\t")) {
                String[] data = line.substring(1).split(" ");
                if (data.length == 2) {
                    mapping = new SrgMapping(db, MappingType.FIELD, data[0], data[1], null, null, currentClass.getIntermediate(), false);
                } else {
                    // TSRGv2 Support
                    MappingType type = data[1].startsWith("(") ? MappingType.METHOD : MappingType.FIELD;
                    mapping = new SrgMapping(db, type, data[0], data[2], data[1], null, currentClass.getIntermediate(), staticMethods.contains(data[2])) {
    
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
            } else {
                // NO-OP (params)
                continue;
            }
            ret.add(mapping);
        }
        return ret;
    }

}
