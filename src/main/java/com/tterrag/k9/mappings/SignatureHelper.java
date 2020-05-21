package com.tterrag.k9.mappings;

import com.google.common.base.Strings;
import com.tterrag.k9.util.Patterns;
import com.tterrag.k9.util.annotation.NonNull;

import clojure.asm.Type;

public class SignatureHelper {
    
    public <@NonNull T extends Mapping> String mapSignature(NameType nameType, String sig, T map, MappingDatabase<? extends T> db) {
        Type ret = Type.getReturnType(sig);
        Type[] args = Type.getArgumentTypes(sig);
        for (int i = 0; i < args.length; i++) {
            args[i] = mapType(nameType, args[i], map, db);
        }
        ret = mapType(nameType, ret, map, db);
        return Type.getMethodDescriptor(ret, args);
    }
    
    public <@NonNull T extends Mapping> Type mapType(NameType nameType, String original, T map, MappingDatabase<? extends T> db) {
        return mapType(nameType, Type.getObjectType(original), map, db);
    }
    
    public <@NonNull T extends Mapping> Type mapType(NameType nameType, Type original, T map, MappingDatabase<? extends T> db) {
        Type type = original;
        if (original.getSort() == Type.ARRAY) {
            type = type.getElementType();
        }
        if (type.getSort() == Type.OBJECT) {
            String name = type.getInternalName();
            T match = null;
            if (Patterns.NOTCH_PARAM.matcher(name).matches()) {
                match = db.lookupExact(NameType.ORIGINAL, MappingType.CLASS, name).stream().filter(m -> m.getOriginal().equals(name)).findFirst().orElse(null);
            } else {
                match = db.lookupExact(MappingType.CLASS, name).stream().findFirst().orElse(null);
            }
            if (match != null) {
                String mappedName = nameType.get(match);
                if (mappedName == null && nameType == NameType.NAME) {
                    mappedName = NameType.INTERMEDIATE.get(match);
                }
                type = Type.getType("L" + mappedName + ";");
            }
            if (original.getSort() == Type.ARRAY) {
                type = Type.getType(Strings.repeat("[", original.getDimensions()) + type.getDescriptor());
            }
            return type;
        } else {
            return original;
        }
    }

}
