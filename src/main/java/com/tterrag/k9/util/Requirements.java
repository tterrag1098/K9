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
import com.tterrag.k9.commands.api.CommandContext;
import com.tterrag.k9.util.annotation.NonNull;
import com.tterrag.k9.util.annotation.NonNullFields;

import discord4j.core.object.entity.GuildChannel;
import discord4j.core.object.entity.Member;
import discord4j.core.object.util.Permission;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

public class Requirements {
    
    @RequiredArgsConstructor
    @NonNullFields
    public enum RequiredType {
        /**
         * All Permission mapped to this type must be on the user.
         */
        ALL_OF("All of"),
        /**
         * One of the Permission mapped to this value must be on the user.
         */
        ONE_OF("One of"),
        /**
         * None of the Permission mapped to this value can be on the user.
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
    
    private final Multimap<RequiredType, Permission> requirements = MultimapBuilder.enumKeys(RequiredType.class).enumSetValues(Permission.class).build();
    
    public static Requirements none() { return NONE; }
    
    public static Builder builder() {
        return new Requirements().new Builder();
    }
    
    public class Builder {
        private Builder() {}
        
        public Builder with(Permission perm, RequiredType type) {
            Requirements.this.requirements.put(type, perm);
            return this;
        }
        
        public Requirements build() { return Requirements.this; }
    }
    
    public Mono<Boolean> matches(CommandContext ctx) {
        return ctx.getMember().transform(Monos.flatZipWith(ctx.getChannel().ofType(GuildChannel.class), this::matches))
                .switchIfEmpty(Mono.just(true));
    }
    
    public Mono<Boolean> matches(Member member, GuildChannel channel) {
    	return channel.getEffectivePermissions(member.getId()).map(this::matches);
    }
    
    public Mono<Boolean> matches(Member member) {
    	return member.getBasePermissions().map(this::matches);
    }
    
    public boolean matches(Set<Permission> perms) {
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
