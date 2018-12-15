package com.tterrag.k9.mappings.yarn;

import java.util.EnumMap;
import java.util.Map;

import com.tterrag.k9.mappings.Mapping;
import com.tterrag.k9.mappings.MappingDatabase;
import com.tterrag.k9.mappings.MappingType;
import com.tterrag.k9.mappings.NameType;
import com.tterrag.k9.mappings.SignatureHelper;
import com.tterrag.k9.util.NonNull;
import com.tterrag.k9.util.Nullable;

import clojure.asm.Type;
import gnu.trove.list.TIntList;
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
public class TinyMapping implements Mapping {
    
    private static final SignatureHelper sigHelper = new SignatureHelper();
    
    MappingDatabase<@NonNull TinyMapping> db;
    
    @Getter(onMethod = @__(@Override))
    MappingType type;
    
    String owner, desc;
    
    @Getter(onMethod = @__(@Override))
    String original, intermediate, name;
    
    @NonFinal
    Map<NameType, String> mappedOwner = new EnumMap<>(NameType.class), mappedDesc = new EnumMap<>(NameType.class);
    
    @Override
    public @Nullable String getOwner() {
        return getOwner(NameType.NAME);
    }
    
    public @Nullable String getOwner(NameType name) {
        return mappedOwner.computeIfAbsent(name, t -> owner == null ? null : sigHelper.mapType(t, owner, this, db).getInternalName());
    }
    
    @Override
    public @Nullable String getDesc() {
        return getDesc(NameType.NAME);
    }
    
    public @Nullable String getDesc(NameType name) {
        return mappedDesc.computeIfAbsent(name, t -> desc == null ? null : desc.contains("(") ? sigHelper.mapSignature(t, desc, this, db) : sigHelper.mapType(t, Type.getType(desc), this, db).getInternalName());
    }
    
    public static TinyMapping fromString(MappingDatabase<@NonNull TinyMapping> db, String line, TIntList order) {
        String[] info = line.split("\t");
        MappingType type = MappingType.valueOf(info[0]);
        switch(type) {
            case CLASS:
                return new TinyMapping(db, type, null, null, info[order.get(0)], info[order.get(1)], info[order.get(2)]);
            case METHOD:
            case FIELD:
                return new TinyMapping(db, type, info[1], info[2], info[order.get(0) + 2], info[order.get(1) + 2], info[order.get(2) + 2]);
            default:
                throw new IllegalArgumentException("Unknown type"); // Params NYI, doesn't exist in the spec
        }
    }

}
