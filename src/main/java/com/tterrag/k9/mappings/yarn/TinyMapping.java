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
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.NonFinal;

@Value
@Getter(onMethod = @__({@Override}))
@RequiredArgsConstructor
@EqualsAndHashCode(of = {"type", "intermediate"})
@ToString(doNotUseGetters = true)
public class TinyMapping implements Mapping {
    
    private static final SignatureHelper sigHelper = new SignatureHelper();
    
    @Getter(AccessLevel.NONE)
    MappingDatabase<@NonNull TinyMapping> db;
    
    MappingType type;
    
    @Nullable
    String owner, desc;
    
    String original, intermediate, name;
    
    @Getter(AccessLevel.NONE)
    @NonFinal
    Map<NameType, String> mappedOwner = new EnumMap<>(NameType.class), mappedDesc = new EnumMap<>(NameType.class);
    
    public @Nullable String getMappedOwner(NameType name) {
        return mappedOwner.computeIfAbsent(name, t -> owner == null ? null : sigHelper.mapType(t, owner, this, db).getInternalName());
    }
    
    public @Nullable String getMappedDesc(NameType name) {
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
