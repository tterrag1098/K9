package com.tterrag.k9.mappings;

import java.util.Collection;

import com.google.common.base.Strings;
import com.tterrag.k9.util.annotation.NonNull;
import com.tterrag.k9.util.Patterns;

import clojure.asm.Type;

public class SignatureHelper {
    
    public <@NonNull T extends Mapping> String mapSignature(NameType nameType, String sig, T map, MappingDatabase<T> db) {
        Type ret = Type.getReturnType(sig);
        Type[] args = Type.getArgumentTypes(sig);
        for (int i = 0; i < args.length; i++) {
            args[i] = mapType(nameType, args[i], map, db);
        }
        ret = mapType(nameType, ret, map, db);
        return Type.getMethodDescriptor(ret, args);
    }
    
    public <@NonNull T extends Mapping> Type mapType(NameType nameType, String original, T map, MappingDatabase<T> db) {
        return mapType(nameType, Type.getObjectType(original), map, db);
    }
    
    public <@NonNull T extends Mapping> Type mapType(NameType nameType, Type original, T map, MappingDatabase<T> db) {
        Type type = original;
        if (original.getSort() == Type.ARRAY) {
            type = type.getElementType();
        }
        if (type.getSort() == Type.OBJECT) {
            String name = original.getInternalName();
            if (Patterns.NOTCH_PARAM.matcher(name).matches()) {
                Collection<T> matches = db.lookup(NameType.ORIGINAL, MappingType.CLASS, name);
                if (!matches.isEmpty()) {
                    T first = matches.iterator().next();
                    String mappedName = nameType.get(first);
                    if (mappedName == null && nameType == NameType.NAME) {
                        mappedName = NameType.INTERMEDIATE.get(first);
                    }
                    return Type.getType("L" + mappedName + ";");
                }
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
