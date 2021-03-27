package com.tterrag.k9.mappings.official;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.beust.jcommander.internal.Lists;
import com.tterrag.k9.mappings.AbstractMappingDatabase;
import com.tterrag.k9.mappings.Mapping;
import com.tterrag.k9.mappings.MappingType;
import com.tterrag.k9.mappings.NoSuchVersionException;
import com.tterrag.k9.mappings.mcp.McpMapping;

public class OfficialDatabase extends AbstractMappingDatabase<OfficialMapping> {
    private final FastSrgDatabase srgs = new FastSrgDatabase(getMinecraftVersion());

    public OfficialDatabase(String minecraftVersion) {
        super(minecraftVersion);
    }

    @Override
    protected Collection<OfficialMapping> parseMappings() throws NoSuchVersionException, IOException {
        srgs.reload();

        String mcver = getMinecraftVersion();
        Path mappingsFolder = OfficialDownloader.INSTANCE.getDataFolder().resolve(Paths.get(mcver, "mappings"));

        List<OfficialMapping> parsed = new ArrayList<>(this.parse(mappingsFolder.resolve("client.txt"), mappingsFolder.resolve("server.txt")));
        // Sort by MappingType enum (aka the order of the declared constants)
        // This sorts classes to be FIRST so that when getIntermediate() is called, method descriptors can be converted correctly since the DB will have the classes populated
        parsed.sort(Comparator.comparing(Mapping::getType));
        return parsed;
    }

    public Collection<OfficialMapping> parse(Path client, Path server) throws IOException {
        Set<OfficialMapping> mappings = new HashSet<>();
        for (Path path : Lists.newArrayList(client, server)) {
            List<String> lines = Files.readAllLines(path).stream()
                    .filter(s -> !s.startsWith("#"))
                    .map(s -> s.replace('.', '/'))
                    .collect(Collectors.toList());
            populateMappings(mappings, lines, "client.txt".equals(path.getFileName().toString()));
        }
        return mappings;
    }

    // Derived from SrgUtils in InternalUtils#loadProguard
    private void populateMappings(Set<OfficialMapping> mappings, List<String> lines, boolean isClient) throws IOException {
        McpMapping.Side side = isClient ? McpMapping.Side.CLIENT : McpMapping.Side.SERVER;
        OfficialMapping clazz = null;
        for (String line : lines) {
            if (!line.startsWith("    ") && line.endsWith(":")) {
                String[] mapped = line.substring(0, line.length() - 1).split(" -> ");
                clazz = addMapping(mappings, new OfficialMapping(srgs, this, side, MappingType.CLASS, null, null, mapped[1], mapped[0], null));
            } else if (line.contains("(") && line.contains(")")) {
                if (clazz == null)
                    throw new IOException("Class was null when parsing method");

                line = line.trim();
                if (line.indexOf(':') != -1) {
                    int i = line.indexOf(':');
                    int j = line.indexOf(':', i + 1);
                    line = line.substring(j + 1);
                }

                String original = line.split(" -> ")[1];
                int spaceIndex = line.indexOf(' ');
                String returnType = toDesc(line.substring(0, spaceIndex));
                String name = line.substring(spaceIndex + 1, line.indexOf('('));
                String[] args = line.substring(line.indexOf('(') + 1, line.indexOf(')')).split(",");

                StringBuilder desc = new StringBuilder("(");
                for (String arg : args) {
                    if (arg.isEmpty())
                        break;
                    desc.append(toDesc(arg));
                }
                desc.append(')').append(returnType);
                addMapping(mappings, new OfficialMapping(srgs, this, side, MappingType.METHOD, clazz, desc.toString(), original, name, null));
            } else {
                if (clazz == null)
                    throw new IOException("Class was null when parsing field");

                String[] pts = line.trim().split(" ");
                addMapping(mappings, new OfficialMapping(srgs, this, side, MappingType.FIELD, clazz, null, pts[3], pts[1], pts[0]));
            }
        }
    }

    private OfficialMapping addMapping(Set<OfficialMapping> mappings, OfficialMapping mapping) {
        if (!mappings.add(mapping)) { // Already exists
            mappings.remove(mapping);
            mapping.setSide(McpMapping.Side.BOTH);
            mappings.add(mapping);
        }
        return mapping;
    }

    // Copied from SrgUtils in InternalUtils#toDesc
    private static String toDesc(String type) {
        if (type.endsWith("[]")) return "[" + toDesc(type.substring(0, type.length() - 2));
        if (type.equals("int")) return "I";
        if (type.equals("void")) return "V";
        if (type.equals("boolean")) return "Z";
        if (type.equals("byte")) return "B";
        if (type.equals("char")) return "C";
        if (type.equals("short")) return "S";
        if (type.equals("double")) return "D";
        if (type.equals("float")) return "F";
        if (type.equals("long")) return "J";
        if (type.contains("/")) return "L" + type + ";";
        throw new RuntimeException("Invalid toDesc input: " + type);
    }
}
