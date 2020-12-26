package com.tterrag.k9.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import com.beust.jcommander.internal.Maps;
import com.google.common.collect.ImmutableMap;
import com.tterrag.k9.commands.api.CommandContext;

import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Message;
import discord4j.common.util.Snowflake;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class GuildStorage<T> {

    private final Map<Long, T> data = new HashMap<>();

    private final Function<Long, T> dataCreator;

    public T get(Snowflake snowflake) {
        long guild = snowflake.asLong();
        synchronized (data) {
            if (data.containsKey(guild)) {
                return data.get(guild);
            } else {
                T val = dataCreator.apply(guild);
                data.put(guild, val);
                return val;
            }
        }
    }

    public T get(Guild guild) {
        return get(guild.getId());
    }
    
    public Mono<T> get(Message message) {
        return message.getGuild().map(this::get);
    }
    
    public Optional<T> get(CommandContext ctx) {
    	return ctx.getGuildId().map(this::get);
    }
    
    public Optional<T> put(Snowflake guild, T val) {
        synchronized (data) {
            return Optional.ofNullable(data.put(guild.asLong(), val));
        }
    }
    
    public Optional<T> put(Guild guild, T val) {
        return put(guild.getId(), val);
    }
    
    public Mono<T> put(Message message, T val) {
        return message.getGuild().transform(Monos.mapOptional(g -> put(g, val)));
    }
    
    public Mono<T> put(CommandContext ctx, T val) {
        return put(ctx.getMessage(), val);
    }
    
    public Map<Long, T> snapshot() {
        synchronized (data) {
            return new HashMap<>(data);
        }
    }
}
