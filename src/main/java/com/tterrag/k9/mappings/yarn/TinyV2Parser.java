package com.tterrag.k9.mappings.yarn;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import org.apache.commons.lang3.StringEscapeUtils;

import com.tterrag.k9.mappings.MappingType;
import com.tterrag.k9.mappings.NameType;
import com.tterrag.k9.mappings.Parser;
import com.tterrag.k9.util.annotation.Nullable;

import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

@RequiredArgsConstructor
public class TinyV2Parser implements Parser<File, TinyMapping> {
    
    private final YarnDatabase db;

    @Override
    public Collection<TinyMapping> parse(File input) throws IOException {
        URI uri = URI.create("jar:" + input.toPath().toUri());
        try (FileSystem fs = FileSystems.newFileSystem(uri, Collections.emptyMap())) {
            Path mappings = fs.getPath("mappings", "mappings.tiny");
            return parseV2(Files.readAllLines(mappings));
        }
    }
    
    @Setter
    @Accessors(fluent = true)
    @RequiredArgsConstructor
    @ToString
    private class PartialMapping {
        
        private final MappingType type;
        
        @Getter
        private String original;
        private String intermediate;
        private String name;
        
        private String desc;
        
        private String owner;
        
        private String comment;
        
        private int param = -1;
        
        private PartialMapping paramOwner;
        
        @Setter(AccessLevel.NONE)
        private List<TinyMapping.Param> params = new ArrayList<>();
        
        PartialMapping name(NameType type, String name) {
            switch(type) {
                case ORIGINAL:
                    return original(name);
                case INTERMEDIATE:
                    return intermediate(name);
                case NAME:
                    return name(name);
                default:
                    throw new IllegalArgumentException(type.name()); 
            }
        }
        
        PartialMapping applyNames(String[] values, Object2IntMap<NameType> order, int start) {
            for (Object2IntMap.Entry<NameType> e : order.object2IntEntrySet()) {
                name(e.getKey(), values[e.getIntValue() + start]);
            }
            return this;
        }
        
        PartialMapping addParam(TinyMapping.Param param) {
            this.params.add(param);
            return this;
        }
        
        @Nullable
        TinyMapping bake() {
            TinyMapping ret;
            if (param >= 0) { 
                TinyMapping.Param pMapping = new TinyMapping.Param(db, type, owner, desc, name, comment, param);
                if (paramOwner != null) {
                    paramOwner.addParam(pMapping);
                }
                ret = pMapping;
            } else {
                ret = new TinyMapping(db, type, owner, desc, original, intermediate, name, comment, params.size() > 0 && params.get(0).getIndex() == 0);
                params.forEach(p -> p.setParentMapping(ret));
            }
            return ret;
        }
    }
    
    private class Dummy extends PartialMapping {
                
        public Dummy() {
            super(null);
        }

        TinyMapping bake() {
            return null;
        }
    }
    
    private final PartialMapping HEADER = new Dummy();
    private final PartialMapping UNKNOWN = new Dummy();

    private Collection<TinyMapping> parseV2(List<String> lines) throws IOException {
        List<TinyMapping> ret = new ArrayList<>();
        Deque<PartialMapping> sections = new LinkedList<>();
        Map<String, String> properties = new HashMap<>();
        Object2IntMap<NameType> names = new Object2IntArrayMap<>(4);
        for (String s : lines) {
            int depth = sections.size();
            for (int i = 0; i < depth; i++) {
                if (s.charAt(0) == '\t') {
                    s = s.substring(1);
                } else {
                    TinyMapping mapping = sections.pop().bake();
                    if (mapping != null) {
                        ret.add(mapping);
                    }
                }
            }
            depth = sections.size();
            String[] values = s.split("\t", -1);
            if (properties.containsKey("escaped-names")) {
                for (int i = 0; i < values.length; i++) {
                    values[i] = StringEscapeUtils.unescapeJava(values[i]);
                }
            }
            PartialMapping context = depth == 0 ? UNKNOWN : sections.peek();
            switch (values[0]) {
                case "tiny":
                    sections.push(HEADER);
                    int maj = Integer.parseInt(values[1]);
                    int min = Integer.parseInt(values[2]);
                    if (maj != 2) {
                        throw new IllegalStateException("Unsupported tiny format: " + maj + "." + min);
                    }
                    for (int i = 3; i < values.length; i++) {
                        names.put(TinyV1Parser.BY_NAME.get(values[i]), i - 3);
                    }
                    break;
                case "c":
                    if (depth == 0) {
                        sections.push(new PartialMapping(MappingType.CLASS)
                                .applyNames(values, names, 1));
                    } else {
                        context.comment(values[1]);
                    }
                    break;
                case "m":
                    sections.push(new PartialMapping(MappingType.METHOD)
                            .desc(values[1])
                            .owner(context.original())
                            .applyNames(values, names, 2));
                    break;
                case "p":
                    sections.push(new PartialMapping(MappingType.PARAM)
                            .owner(context.original())
                            .param(Integer.parseInt(values[1]))
                            .applyNames(values, names, 2)
                            .paramOwner(context));
                    break;
                case "f":
                    sections.push(new PartialMapping(MappingType.FIELD)
                            .desc(values[1])
                            .owner(context.original())
                            .applyNames(values, names, 2));
                    break;
                case "v":
                    break; // No variable mappings (yet?)
                default:
                    if (depth == 1 && context == HEADER) { // properties
                        properties.put(values[1], values.length > 2 ? values[2] : null);
                    } else {
                        sections.push(UNKNOWN);
                    }
            }
        }
        // Must add mappings in this order since params can reference methods/fields/classes
        Collections.sort(ret, Comparator.comparing(TinyMapping::getType));
        return ret;
    }
}
