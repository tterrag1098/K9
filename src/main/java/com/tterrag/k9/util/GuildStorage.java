package com.tterrag.k9.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;

import com.tterrag.k9.commands.api.CommandContext;

import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Message;
import discord4j.core.object.util.Snowflake;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class GuildStorage<T> implements Iterable<Entry<Long, T>> {

    private final Map<Long, T> data = new HashMap<>();

    private final Function<Long, T> dataCreator;

    public T get(Snowflake snowflake) {
        long guild = snowflake.asLong();
        if (data.containsKey(guild)) {
            return data.get(guild);
        } else {
            T val = dataCreator.apply(guild);
            data.put(guild, val);
            return val;
        }
    }
    

    public T get(Guild guild) {
        return get(guild.getId());
    }
    
    public Mono<T> get(Message message) {
        return message.getGuild().map(this::get);
    }
    
    public Mono<T> get(CommandContext ctx) {
    	return get(ctx.getMessage());
    }
    
    public T put(Snowflake guild, T val) {
        return data.put(guild.asLong(), val);
    }
    
    public T put(Guild guild, T val) {
        return put(guild.getId(), val);
    }
    
    public Mono<T> put(Message message, T val) {
        return message.getGuild().map(g -> put(g, val));
    }
    
    public Mono<T> put(CommandContext ctx, T val) {
        return put(ctx.getMessage(), val);
    }
    
    @Override
    public Iterator<Entry<Long, T>> iterator() {
        return Collections.unmodifiableMap(data).entrySet().iterator();
    }
}
