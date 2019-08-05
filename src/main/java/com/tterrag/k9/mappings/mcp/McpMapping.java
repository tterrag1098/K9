package com.tterrag.k9.mappings.mcp;

import com.google.common.base.Strings;
import com.tterrag.k9.mappings.Mapping;
import com.tterrag.k9.mappings.MappingType;
import com.tterrag.k9.util.annotation.Nullable;

import lombok.Getter;
import lombok.Value;
import lombok.experimental.NonFinal;

public interface McpMapping extends IntermediateMapping {

    enum Side {
        CLIENT,
        SERVER,
        BOTH
    }

    String getComment();

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
        builder.append("__Side__: `" + side + "`\n");

        if (getType() != MappingType.PARAM) {
            builder.append("__AT__: `public ").append(Strings.nullToEmpty(getOwner()).replace('/', '.'));
            String atName = getIntermediate();
            if (getType() == MappingType.CLASS) {
                atName = atName.replace('/', '.');
            }
            builder.append(" ").append(atName);
            String desc = getDesc();
            if (desc != null) {
                builder.append(getDesc());
            }
            if (getType() != MappingType.CLASS) {
                Mapping parent = getParent();
                String parentMcp = parent.getName();
                builder.append(" # ").append(parentMcp == null ? parent.getIntermediate() : parentMcp);
            }
            builder.append("`\n");
        }
        String type = getMemberClass();
        if (type != null) {
            builder.append("__Type__: `" + type + "`\n");
        }
        return builder.toString();
    }

    @Value
    @NonFinal
    class Impl implements McpMapping {

        @Getter(onMethod = @__(@Override))
        MappingType type;

        @Getter(onMethod = @__(@Override))
        String original, intermediate, name;

        @Getter(onMethod = @__({ @Override, @Nullable }))
        String desc, owner;
        
        @Getter(onMethod = @__(@Override))
        boolean isStatic;

        @Getter(onMethod = @__({ @Override, @Nullable }))
        String comment;

        @Getter(onMethod = @__(@Override))
        Side side;
    }
}
