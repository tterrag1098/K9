package com.tterrag.k9.mappings;

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

    /**
     * @return For method mappings, the method descriptor (in intermediate names). Otherwise, null.
     */
    default @Nullable String getDesc() {
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
}
