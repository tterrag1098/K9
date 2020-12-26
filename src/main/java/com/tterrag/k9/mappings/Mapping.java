package com.tterrag.k9.mappings;

import java.util.Objects;
import java.util.Optional;

import com.tterrag.k9.util.annotation.Nullable;

public interface Mapping {

    MappingType getType();

    String getOriginal();

    String getIntermediate();

    default @Nullable String getName() {
        return null;
    }

    /**
     * @return For classes, null.
     *         <p>
     *         For everything else, the class owner of the member.
     */
    default @Nullable String getOwner() {
        return null;
    }
    
    default @Nullable String getOwner(NameType name) {
        return null;
    }

    /**
     * @return For method mappings, the method descriptor (in intermediate names). Otherwise, null.
     */
    default @Nullable String getDesc() {
        return null;
    }
    
    default @Nullable String getDesc(NameType name) {
        return null;
    }

    /**
     * @return The type of this mapping, in internal class form.
     */
    default @Nullable String getMemberClass() {
        return null;
    }

    /**
     * Print all the information for this mapping in a pretty way for user-facing representations
     */
    String formatMessage(String mcver);

    default <T extends Mapping> Optional<? extends T> convert(MappingDatabase<? extends T> db) {
        String owner = getOwner(NameType.ORIGINAL);
        return db.lookup(NameType.ORIGINAL, getType(), owner == null ? getOriginal() : owner + "." + getOriginal())
                .stream()
                .filter(m -> Objects.equals(getDesc(NameType.ORIGINAL), m.getDesc(NameType.ORIGINAL)))
                .findFirst();
    }
}
