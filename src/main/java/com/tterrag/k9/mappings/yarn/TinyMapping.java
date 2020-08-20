package com.tterrag.k9.mappings.yarn;

import java.util.EnumMap;
import java.util.Map;

import com.tterrag.k9.mappings.CommentedMapping;
import com.tterrag.k9.mappings.MappingDatabase;
import com.tterrag.k9.mappings.MappingType;
import com.tterrag.k9.mappings.NameType;
import com.tterrag.k9.mappings.ParamMapping;
import com.tterrag.k9.mappings.SignatureHelper;
import com.tterrag.k9.mappings.mcp.IntermediateMapping;
import com.tterrag.k9.mappings.mcp.McpDatabase.McpParamMapping;
import com.tterrag.k9.util.annotation.NonNull;
import com.tterrag.k9.util.annotation.Nullable;

import clojure.asm.Type;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.NonFinal;

@Value
@RequiredArgsConstructor
@EqualsAndHashCode(of = {"type", "intermediate"})
@ToString(doNotUseGetters = true)
@NonFinal
public class TinyMapping implements CommentedMapping, IntermediateMapping {
    
    @EqualsAndHashCode(callSuper = true)
    @ToString(callSuper = true)
    @Value
    public static final class Param extends TinyMapping implements ParamMapping {
        
        @Getter(onMethod = @__(@Override))
        int index;
        
        @NonFinal
        private TinyMapping parent;
        
        @NonFinal
        private Type paramType;

        public Param(MappingDatabase<@NonNull TinyMapping> db, MappingType type, String owner, String desc, String name, String comment, int index) {
            super(db, type, owner, desc, "", "\u2603", name, comment, false);
            this.index = index;
        }

        public void setParentMapping(TinyMapping parent) {
            this.parent = parent;
            this.paramType = McpParamMapping.findType(parent, index);
        }

        @Override
        public @Nullable String getOwner(NameType type) {
            String parentName = parent.getOwner(type);
            if (parentName == null) {
                parentName = parent.getOwner(NameType.INTERMEDIATE);
            }
            String name = type.get(parent);
            return parentName + "." + (name == null ? parent.getIntermediate() : name);
        }
        
        @Override
        public @Nullable String getMemberClass() {
            return sigHelper.mapType(NameType.NAME, paramType, this, db).getClassName();
        }
        
        @Override
        public String formatMessage(String mcver) {
            StringBuilder ret = new StringBuilder(super.formatMessage(mcver));
            ret.append("\n__Index__: `").append(index).append('`');
            return ret.toString();
        }
    }
    
    private static final SignatureHelper sigHelper = new SignatureHelper();
    
    @ToString.Exclude
    protected transient MappingDatabase<@NonNull TinyMapping> db;
    
    @Getter(onMethod = @__(@Override))
    MappingType type;
    
    String owner, desc;
    
    @Getter(onMethod = @__(@Override))
    String original, intermediate, name;
    
    @Getter(onMethod = @__(@Override))
    String comment;
    
    @Getter(onMethod = @__(@Override))
    boolean isStatic;
    
    @ToString.Exclude
    transient Map<NameType, String> mappedOwner = new EnumMap<>(NameType.class), mappedDesc = new EnumMap<>(NameType.class);
    
    @Override
    public @Nullable final String getOwner() {
        return getOwner(NameType.NAME);
    }
    
    @Override
    public @Nullable String getOwner(NameType name) {
        return mappedOwner.computeIfAbsent(name, t -> owner == null ? null : sigHelper.mapType(t, owner, this, db).getInternalName());
    }
    
    @Override
    public @Nullable final String getDesc() {
        return getDesc(NameType.NAME);
    }
    
    @Override
    public @Nullable String getMemberClass() {
        return getType() == MappingType.FIELD ? Type.getType(getDesc(NameType.NAME)).getClassName() : CommentedMapping.super.getMemberClass();
    }
    
    private String mapType(NameType t, String type) {
        return sigHelper.mapType(t, Type.getType(type), this, db).getDescriptor();
    }
    
    @Override
    public @Nullable String getDesc(NameType name) {
        return mappedDesc.computeIfAbsent(name, t -> desc == null ? null : desc.contains("(") ? sigHelper.mapSignature(t, desc, this, db) : mapType(t, desc));
    }
    
    @Override
    public String formatMessage(String mcver) {
        StringBuilder builder = new StringBuilder();
        String name = getName();
        String displayName = name;
        if (displayName == null) {
            displayName = getIntermediate().replaceAll("_", "\\_");
        }
        builder.append("\n");
        String owner = getOwner(NameType.NAME);
        if (owner == null) {
            owner = getOwner(NameType.INTERMEDIATE);
        }
        builder.append("**MC " + mcver + ": " + (owner != null ? owner + "." : "") + displayName + "**\n");
        builder.append("__Name__: `");
        if (getType() != MappingType.PARAM) {
            builder.append(getOriginal()).append("` => `").append(getIntermediate());
            if (name != null) {
                builder.append("` => `");
            }
        }
        builder.append(getName()).append("`");
        String desc = getDesc();
        if (desc != null && getType() == MappingType.METHOD) {
            builder.append("\n__Descriptor__: `" + displayName + desc + "`");
        }
        String type = getMemberClass();
        if (type != null) {
            builder.append("\n__Type__: `" + type  + "`");
        }
        String mixinTarget = null;
        if (owner != null) {
            if (getType() == MappingType.METHOD && desc != null) {
                mixinTarget = owner + "." + displayName + desc;
            } else if (getType() == MappingType.FIELD && type != null) {
                mixinTarget = "L" + owner + ";" + displayName + ":" + getDesc(NameType.NAME);
            }
        }
        if (mixinTarget != null) {
            builder.append("\n__Mixin Target__: `").append(mixinTarget).append("`");
        }
        if (getType() != MappingType.PARAM) {
            builder.append("\n__Access Widener__: `accessible\t");
            if (getType() == MappingType.CLASS) {
                builder.append("class\t").append(displayName).append('`');
            } else {
                builder.append(getType() == MappingType.METHOD ? "method" : "field").append('\t').append(owner).append('\t').append(displayName).append('\t').append(desc).append('`');
            }
        }
        return builder.toString();
    }
}
