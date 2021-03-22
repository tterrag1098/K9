package com.tterrag.k9.mappings.official;

import clojure.asm.Type;
import com.google.common.base.Strings;
import com.tterrag.k9.mappings.Mapping;
import com.tterrag.k9.mappings.MappingType;
import com.tterrag.k9.mappings.NameType;
import com.tterrag.k9.mappings.SignatureHelper;
import com.tterrag.k9.mappings.mcp.McpMapping;
import com.tterrag.k9.mappings.mcp.SrgDatabase;
import com.tterrag.k9.util.annotation.Nullable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.NonFinal;

import java.util.EnumMap;
import java.util.Map;

@Value
@RequiredArgsConstructor
@EqualsAndHashCode(of = {"type", "owner", "original", "name"})
@ToString(doNotUseGetters = true)
public class OfficialMapping implements Mapping {
    private static final SignatureHelper sigHelper = new SignatureHelper();

    @ToString.Exclude
    protected final transient SrgDatabase srgs;
    @ToString.Exclude
    protected final transient OfficialDatabase db;

    private final McpMapping.Side side;

    @Getter(onMethod = @__(@Override))
    private final MappingType type;

    private final Mapping owner;

    private final String desc;

    @Getter(onMethod = @__(@Override))
    private final String original, name, memberClass;

    @ToString.Exclude
    private final transient Map<NameType, String> mappedOwner = new EnumMap<>(NameType.class), mappedDesc = new EnumMap<>(NameType.class);

    @NonFinal
    private String intermediate = null;

    @Override
    public String formatMessage(String mcver) {
        StringBuilder builder = new StringBuilder();
        builder.append("\n");
        builder.append("**MC " + mcver + ": " + (owner == null ? "" : owner.getName() + ".") + name + "**\n");
        String intermediate = getIntermediate();
        builder.append("__Name__: `" + original + (intermediate.isEmpty() ? "" : "` => `" + intermediate) + "` => `" + name + "`\n");

        builder.append("__Side__: `" + side + "`");

        if (getType() != MappingType.PARAM && !intermediate.isEmpty()) {
            builder.append("\n__AT__: `public ").append(Strings.nullToEmpty(getOwner(NameType.INTERMEDIATE)).replace('/', '.'));
            String atName = intermediate;
            if (getType() == MappingType.CLASS) {
                atName = atName.replace('/', '.');
            } else {
                // If this is a class, then getOwner() is empty meaning we shouldn't add another space
                builder.append(' ');
            }
            builder.append(atName);
            String desc = getDesc();
            if (desc != null) {
                builder.append(desc);
            }
            if (getType() != MappingType.CLASS) {
                builder.append(" # ").append(getName() == null ? intermediate : getName());
            }
            builder.append("`");
        }

        if (getMemberClass() != null)
            builder.append("\n__Type__: `").append(getMemberClass()).append("`");

        return builder.toString();
    }

    OfficialMapping cloneWithSide(McpMapping.Side side) {
        return new OfficialMapping(srgs, db, side, type, owner, desc, original, name, memberClass);
    }

    @Override
    public @Nullable String getOwner() {
        return getOwner(NameType.NAME);
    }

    @Override
    public @Nullable String getOwner(NameType name) {
        return mappedOwner.computeIfAbsent(name, t -> owner == null ? null : sigHelper.mapType(t, owner.getOriginal(), this, db).getInternalName());
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
        if (intermediate != null)
            return intermediate;
        if (getType() == MappingType.CLASS) {
            intermediate = convert(srgs).map(Mapping::getIntermediate).orElse("");
        } else {
            intermediate = convert(srgs).map(Mapping.class::cast).orElse(owner).getIntermediate();
        }
        return intermediate;
    }
}
