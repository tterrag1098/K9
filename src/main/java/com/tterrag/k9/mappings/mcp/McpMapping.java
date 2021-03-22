package com.tterrag.k9.mappings.mcp;

import java.util.EnumMap;
import java.util.Map;

import com.google.common.base.Strings;
import com.tterrag.k9.mappings.CommentedMapping;
import com.tterrag.k9.mappings.Mapping;
import com.tterrag.k9.mappings.MappingDatabase;
import com.tterrag.k9.mappings.MappingType;
import com.tterrag.k9.mappings.NameType;
import com.tterrag.k9.mappings.SignatureHelper;
import com.tterrag.k9.util.annotation.NonNull;
import com.tterrag.k9.util.annotation.Nullable;

import clojure.asm.Type;
import lombok.Getter;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.NonFinal;

public interface McpMapping extends IntermediateMapping, CommentedMapping {

    enum Side {
        CLIENT,
        SERVER,
        BOTH
    }

    Side getSide();
    
    /**
     * @return The mapping that "owns" this one. Currently only meaningful for parameters.
     */
    default Mapping getParent() { return this; }
    
    @Override
    default String formatMessage(String mcver) {
        StringBuilder builder = new StringBuilder();
        String mcp = getName();
        builder.append("\n");
        builder.append("**MC " + mcver + ": " + (getOwner() == null ? "" : getOwner() + ".") + (mcp == null ? getIntermediate().replace("_", "\\_") : mcp) + "**\n");
        builder.append("__Name__: `" + getOriginal() + "` => `" + getIntermediate() + (mcp == null ? "`\n" : "` => `" + getName() + "`\n"));

        String comment = getComment();
        builder.append("__Comment__: `" + (comment.isEmpty() ? "None" : getComment()) + "`\n");

        Side side = getSide();
        builder.append("__Side__: `" + side + "`");

        if (getType() != MappingType.PARAM) {
            builder.append("\n__AT__: `public ").append(Strings.nullToEmpty(getOwner()).replace('/', '.'));
            String atName = getIntermediate();
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
                builder.append(" # ").append(getName() == null ? getIntermediate() : getName());
            }
            builder.append("`");
        }
        String type = getMemberClass();
        if (type != null) {
            builder.append("\n__Type__: `").append(type).append("`");
        }
        return builder.toString();
    }

    @Value
    @NonFinal
    class Impl implements McpMapping {

        private static final SignatureHelper sigHelper = new SignatureHelper();

        @ToString.Exclude
        transient MappingDatabase<? extends @NonNull McpMapping> db;

        @Getter(onMethod = @__(@Override))
        MappingType type;

        @Getter(onMethod = @__(@Override))
        String original, intermediate, name;

        String desc, owner;
        
        @Getter(onMethod = @__(@Override))
        boolean isStatic;

        @Getter(onMethod = @__({ @Override, @Nullable }))
        String comment;

        @Getter(onMethod = @__(@Override))
        Side side;
        
        @ToString.Exclude
        transient Map<NameType, String> mappedOwner = new EnumMap<>(NameType.class), mappedDesc = new EnumMap<>(NameType.class);
        
        @Override
        public @Nullable String getOwner() {
            return getOwner(NameType.NAME);
        }
        
        @Override
        public @Nullable String getOwner(NameType name) {
            return mappedOwner.computeIfAbsent(name, t -> owner == null ? null : sigHelper.mapType(t, owner, this, db).getInternalName());
        }
        
        private String mapType(NameType t, String type) {
            return sigHelper.mapType(t, Type.getType(type), this, db).getDescriptor();
        }
        
        @Override
        public @Nullable String getDesc() {
            return getDesc(NameType.NAME);
        }
        
        @Override
        public @Nullable String getDesc(NameType name) {
            return mappedDesc.computeIfAbsent(name, t -> desc == null ? null : desc.contains("(") ? sigHelper.mapSignature(t, desc, this, db) : mapType(t, desc));
        }
    }
}
