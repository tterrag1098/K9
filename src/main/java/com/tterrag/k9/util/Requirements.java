package com.tterrag.k9.util;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.google.common.base.Joiner;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.tterrag.k9.K9;

import lombok.RequiredArgsConstructor;
import sx.blah.discord.handle.obj.IGuild;
import sx.blah.discord.handle.obj.IUser;
import sx.blah.discord.handle.obj.Permissions;

public class Requirements {
    
    @RequiredArgsConstructor
    public enum RequiredType {
        /**
         * All permissions mapped to this type must be on the user.
         */
        ALL_OF("All of"),
        /**
         * One of the permissions mapped to this value must be on the user.
         */
        ONE_OF("One of"),
        /**
         * None of the permissions mapped to this value can be on the user.
         */
        NONE_OF("None of"),

        ;
        
        private final String name;
        
        @Override
        public String toString() {
            return name;
        }
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
    
    @Override
    public String toString() {
        if (this != NONE) {
            StringBuilder sb = new StringBuilder();
            for (RequiredType type : RequiredType.values()) {
                Collection<String> perms = requirements.get(type).stream()
                        .map(Object::toString)
                        .map(s -> s.split("_"))
                        .map(arr -> Arrays.stream(arr).map(s -> s.toLowerCase(Locale.ROOT)).map(StringUtils::capitalize).toArray(String[]::new))
                        .map(Joiner.on(' ')::join)
                        .collect(Collectors.toList());

                if (!perms.isEmpty()) {
                    sb.append(type).append(": ").append(Joiner.on(", ").join(perms)).append("\n");
                }
            }
            if (sb.length() > 0) {
                return sb.toString();
            }
        }
        return "None";
    }
}
