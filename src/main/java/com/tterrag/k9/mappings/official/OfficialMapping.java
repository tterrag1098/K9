package com.tterrag.k9.mappings.official;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.tterrag.k9.mappings.Mapping;
import com.tterrag.k9.mappings.MappingType;
import com.tterrag.k9.mappings.NameType;
import com.tterrag.k9.mappings.SignatureHelper;
import com.tterrag.k9.mappings.mcp.McpMapping;
import com.tterrag.k9.util.annotation.Nullable;

import clojure.asm.Type;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.NonFinal;

@Value
@RequiredArgsConstructor
@EqualsAndHashCode(of = {"type", "owner", "original", "name", "desc"})
@ToString(doNotUseGetters = true)
public class OfficialMapping implements Mapping {
    private static final SignatureHelper sigHelper = new SignatureHelper();

    @ToString.Exclude
    private final FastSrgDatabase srgs;
    @ToString.Exclude
    private final OfficialDatabase db;

    @Setter(AccessLevel.PACKAGE)
    @NonFinal
    @NonNull
    private McpMapping.Side side;

    @Getter(onMethod = @__(@Override))
    private final MappingType type;

    private final Mapping owner;

    @Getter(onMethod = @__(@Override))
    private final String desc;

    private final String parameters, returnType;

    @Getter(onMethod = @__(@Override))
    private final String original, name, memberClass;

    @ToString.Exclude
    @Getter(AccessLevel.NONE)
    private final Map<NameType, String> mappedDesc = new EnumMap<>(NameType.class);

    @NonFinal
    private String intermediate = null;

    @Override
    public String formatMessage(String mcver) {
        boolean isClassMapping = type == MappingType.CLASS;
        StringBuilder builder = new StringBuilder("\n");
        builder.append("**MC ").append(mcver).append(": ")
                .append(owner == null ? "" : owner.getName() + ".").append(name).append("**\n");

        String intermediate = getIntermediate();
        builder.append("__Name__: `");
        if (!isSpecial()) {
            builder.append(original)
                    .append(intermediate.isEmpty() || isClassMapping ? "" : "` => `" + intermediate).append("` => `");
        }
        builder.append(name).append("`\n");

        builder.append("__Side__: `").append(side).append("`\n");

        if (type == MappingType.METHOD) {
            builder.append("__Descriptor__: `").append(returnType).append(' ')
                    .append(name).append('(').append(parameters).append(")`\n");
        }

        boolean is121Plus = mcver != null && mcver.compareTo("1.21") >= 0;

        // For 1.21+, use the remapped name. For older versions, use intermediate.
        String atName = is121Plus ? name : intermediate;

        if (!intermediate.isEmpty() || is121Plus) {
            builder.append("__AT__: `public ").append(Strings.nullToEmpty(getOwner(NameType.INTERMEDIATE)).replace('/', '.'));
            if (isClassMapping) {
                // getOwner() is empty here, so no space is needed
                atName = atName.replace('/', '.');
            } else {
                // getOwner() isn't empty here, so we need an extra space
                builder.append(' ');
            }
            builder.append(atName);
            String desc = getDesc(NameType.INTERMEDIATE);
            if (desc != null) {
                builder.append(desc);
            }
            if (!isClassMapping) {
                builder.append(" # ").append(getName() == null ? intermediate : getName());
            }
            builder.append("`\n");
        }

        if (getMemberClass() != null)
            builder.append("__Type__: `").append(getMemberClass()).append("`");

        return builder.toString();
    }

    /**
     * Special methods like &lt;init&gt;, &lt;init&gt;, and other stuff required to have the same name by external libs
     * @return True if the method is special (its original name equals its mapped name).
     */
    private boolean isSpecial() {
        return original.equals(name);
    }

    @Override
    public @Nullable String getOwner() {
        return getOwner(NameType.NAME);
    }

    @Override
    public @Nullable String getOwner(NameType name) {
        return owner == null ? null : name.get(owner);
    }

    private String mapType(NameType t, String type) {
        return sigHelper.mapType(t, Type.getType(type), this, db).getDescriptor();
    }

    @Override
    public String getDesc(NameType name) {
        return mappedDesc.computeIfAbsent(name, t -> desc == null ? null : desc.contains("(") ? sigHelper.mapSignature(t, desc, this, db) : mapType(t, desc));
    }

    @Override
    public String getIntermediate() {
        if (intermediate == null) {
            // Should help with random issues
            mappedDesc.clear();

            if (srgs == null) {
                intermediate = "";
            } else if (type == MappingType.CLASS) {
                intermediate = Optional.ofNullable(srgs.getClassMapping(original)).map(Mapping::getIntermediate).orElse("");
                if (intermediate != null && intermediate.startsWith("net/minecraft/src/C_")) {
                    // SRG classnames are purely used to make exports not contain mojmaps.
                    // Remap to mojmap classnames here.
                    intermediate = name;
                }
            } else {
                String desc = getDesc(NameType.ORIGINAL);
                intermediate = srgs.getChildren(owner.getOriginal()).stream()
                        .filter(m -> original.equals(m.getOriginal()))
                        .filter(m -> Objects.equal(desc, m.getDesc(NameType.ORIGINAL)))
                        .findFirst()
                        .map(Mapping::getIntermediate)
                        .orElse("");
            }

            if (srgs != null && intermediate.isEmpty() && isSpecial()) {
                intermediate = original;
            }
        }

        return intermediate;
    }
}
