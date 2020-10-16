package com.tterrag.k9.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import com.google.common.collect.ImmutableMap;
import com.tterrag.k9.commands.api.CommandContext;

import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Message;
import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.channel.Channel;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class GuildStorage<T> {

    private final Map<Pair<Long, Long>, T> data = new HashMap<>();

    private final Function<Pair<Long, Long>, T> dataCreator;

    public T get(Snowflake guildId) {
        return get(guildId, null);
    }

    public T get(Snowflake guildId, Snowflake channelId) {
        long guild = guildId.asLong();
        long channel = channelId != null ? channelId.asLong() : -1l;
        Pair<Long, Long> key = Pair.of(guild, channel);
        synchronized (data) {
            if (data.containsKey(key)) {
                return data.get(key);
            } else {
                T val = dataCreator.apply(key);
                data.put(key, val);
                return val;
            }
        }
    }

    public T get(Guild guild) {
        return get(guild.getId());
    }

    public T get(Guild guild, Channel channel) {
        return get(guild.getId(), channel.getId());
    }
    
    public Mono<T> get(Message message) {
        return get(message, false);
    }

    public Mono<T> get(Message message, boolean useChannel) {
        if (useChannel) {
            return message.getGuild().flatMap(guild -> message.getChannel().map(channel -> get(guild, channel)));
        } else {
            return message.getGuild().map(this::get);
        }
    }
    
    public Optional<T> get(CommandContext ctx) {
    	return get(ctx, false);
    }

    public Optional<T> get(CommandContext ctx, boolean useChannel) {
        if (useChannel) {
            return ctx.getGuildId().map(guild -> get(guild, ctx.getChannelId()));
        } else {
            return ctx.getGuildId().map(this::get);
        }
    }
    
    public Optional<T> put(Snowflake guild, T val) {
        return put(guild, null, val);
    }

    public Optional<T> put(Snowflake guild, Snowflake channel, T val) {
        Pair<Long, Long> key = Pair.of(guild.asLong(), channel!=null?channel.asLong():-1l);
        synchronized (data) {
            return Optional.ofNullable(data.put(key, val));
        }
    }
    
    public Optional<T> put(Guild guild, T val) {
        return put(guild.getId(), val);
    }

    public Optional<T> put(Guild guild, Channel channel, T val) {
        return put(guild.getId(), channel.getId(), val);
    }
    
    public Mono<T> put(Message message, T val) {
        return put(message, val, false);
    }

    public Mono<T> put(Message message, T val, boolean useChannel) {
        if (useChannel) {
            return message.getGuild().flatMap(guild -> message.getChannel().transform(Monos.mapOptional(channel -> put(guild, channel, val))));
        } else {
            return message.getGuild().transform(Monos.mapOptional(g -> put(g, val)));
        }
    }
    
    public Mono<T> put(CommandContext ctx, T val) {
        return put(ctx.getMessage(), val);
    }

    public Mono<T> put(CommandContext ctx, T val, boolean useChannel) {
        return put(ctx.getMessage(), val, useChannel);
    }
    
    public Map<Pair<Long, Long>, T> snapshot() {
        synchronized (data) {
            return ImmutableMap.copyOf(data);
        }
    }
}
