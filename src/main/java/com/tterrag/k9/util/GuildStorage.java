package com.tterrag.k9.util;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;

import com.google.common.collect.ImmutableMap;
import com.tterrag.k9.commands.api.CommandContext;

import lombok.RequiredArgsConstructor;
import sx.blah.discord.handle.obj.IGuild;
import sx.blah.discord.handle.obj.IMessage;

@RequiredArgsConstructor
public class GuildStorage<T> implements Iterable<Entry<Long, T>> {

    private final Map<Long, T> data = new HashMap<>();

    private final Function<Long, T> dataCreator;

    public T get(long guild) {
        return data.computeIfAbsent(guild, dataCreator);
    }

    public T get(IGuild guild) {
        return get(guild.getLongID());
    }
    
    public T get(IMessage message) {
        return get(message.getGuild());
    }
    
    public T get(CommandContext ctx) {
    	return get(ctx.getMessage());
    }
    
    public T put(long guild, T val) {
        return data.put(guild, val);
    }
    
    public T put(IGuild guild, T val) {
        return put(guild.getLongID(), val);
    }
    
    public T put(IMessage message, T val) {
        return put(message.getGuild(), val);
    }
    
    public T put(CommandContext ctx, T val) {
        return put(ctx.getMessage(), val);
    }

    @Override
    public Iterator<Entry<Long, T>> iterator() {
        return ImmutableMap.copyOf(data).entrySet().iterator();
    }
}
