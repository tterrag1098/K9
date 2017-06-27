package com.blamejared.mcbot.util;

import java.util.Collections;
import java.util.Set;

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;

import sx.blah.discord.handle.obj.IGuild;
import sx.blah.discord.handle.obj.IUser;
import sx.blah.discord.handle.obj.Permissions;

public class Requirements {
    
    public enum RequiredType {
        /**
         * All permissions mapped to this type must be on the user.
         */
        ALL_OF,
        /**
         * One of the permissions mapped to this value must be on the user.
         */
        ONE_OF,
        /**
         * None of the permissions mapped to this value can be on the user.
         */
        NONE_OF,

        ;
    }
    
    private static final @NonNull Requirements NONE = new Requirements();
    
    private Multimap<RequiredType, Permissions> requirements = MultimapBuilder.enumKeys(RequiredType.class).enumSetValues(Permissions.class).build();
    
    public static @NonNull Requirements none() { return NONE; }
    
    public static @NonNull Builder builder() {
        return new Requirements().new Builder();
    }
    
    public class Builder {
        private Builder() {}
        
        public @NonNull Builder with(Permissions perm, RequiredType type) {
            Requirements.this.requirements.put(type, perm);
            return this;
        }
        
        public @NonNull Requirements build() { return Requirements.this; }
    }
    
    @SuppressWarnings("null")
    public boolean matches(@Nullable IUser user, @Nullable IGuild guild) {
        return matches(user == null || guild == null ? Collections.emptySet() : user.getPermissionsForGuild(guild));
    }
    
    public boolean matches(@NonNull Set<Permissions> perms) {
        if (this == NONE) return true;
        if (this.requirements.isEmpty()) return true;
        boolean hasAll = perms.containsAll(requirements.get(RequiredType.ALL_OF));
        boolean hasOne = !requirements.containsKey(RequiredType.ONE_OF) || !Collections.disjoint(requirements.get(RequiredType.ONE_OF), perms);
        boolean hasNone = !requirements.containsKey(RequiredType.NONE_OF) || Collections.disjoint(requirements.get(RequiredType.NONE_OF), perms);
        return hasAll && hasOne && hasNone;
    }
}
