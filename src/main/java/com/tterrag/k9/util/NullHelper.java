package com.tterrag.k9.util;

/**
 * Adapted from 
 * <a href="https://github.com/SleepyTrousers/EnderCore/blob/93681af2c8a778c72f617ca7506a4cec833288b9/src/main/java/com/enderio/core/common/util/NullHelper.java">EnderCore</a>
 * <br>
 * Changes by tterrag
 * 
 * @author HenryLoenwind
 */
@DefaultNonNull
public final class NullHelper {

    private NullHelper() {}

    public final static <P> P notnull(@Nullable P o, String message) {
        if (o == null) {
            throw new NullPointerException("Houston we have a problem: '" + message + "'. " + "Please report this to the K9 bugtracker unless you are using some old version. Thank you.");
        }
        return o;
    }

    public final static <P> P notnullJ(@Nullable P o, String message) {
        if (o == null) {
            throw new NullPointerException("There was a problem with Java: The call '" + message + "' returned null even though it should not be able to do that. Is your Java broken?");
        }
        return o;
    }

    public final static <P> P notnullD(@Nullable P o, String message) {
        if (o == null) {
            throw new NullPointerException("There was a problem with Discord4J: The call '" + message + "' returned null even though it should not be able to do that!");
        }
        return o;
    }
    
    public final static <P> P notnullL(@Nullable P o, String message) {
        if (o == null) {
            throw new NullPointerException("There was a problem with a library: The call '" + message + "' returned null even though it should not be able to do that!");
        }
        return o;
    }

    /**
     * Returns its {@link Nonnull} argument unchanged as {@link Nullable}. Use this if you want to null-check values
     * that are annotated non-null but are known not to be.
     */
    public final static @Nullable <P> P untrust(P o) {
        return o;
    }

    @SafeVarargs
    public final static <P> P first(@Nullable P... o) {
        for (P on : notnullJ(o, "... param is null")) {
            if (on != null) {
                return on;
            }
        }
        throw new NullPointerException("Houston we have a problem. Please report that to the K9 bugtracker unless you are using some old version. Thank you.");
    }
}
