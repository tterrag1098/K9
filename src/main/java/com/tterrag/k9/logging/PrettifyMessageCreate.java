package com.tterrag.k9.logging;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Marker;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import discord4j.core.DiscordClient;
import discord4j.core.object.entity.Channel;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.GuildChannel;
import discord4j.core.object.entity.PrivateChannel;
import discord4j.core.object.util.Snowflake;
import discord4j.gateway.json.GatewayPayload;
import discord4j.gateway.json.Opcode;
import discord4j.gateway.json.dispatch.EventNames;
import discord4j.gateway.json.dispatch.MessageCreate;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j(topic = "com.tterrag.k9.messages")
public class PrettifyMessageCreate extends TurboFilter {
    
    public static DiscordClient client;
    private static Cache<Long, String> channelNames = CacheBuilder.newBuilder().expireAfterAccess(1, TimeUnit.HOURS).build();
    private static Map<Long, String> guildNames = new HashMap<>();
    
    @Override
    public FilterReply decide(Marker marker, Logger logger, Level level, String format, Object[] params, Throwable t) {
        if (logger.getName().startsWith("discord4j.gateway.inbound.0")) {
            for (Object param : params) {
                if (param instanceof GatewayPayload) {
                    GatewayPayload<?> payload = (GatewayPayload<?>) param;
                    if (Opcode.DISPATCH.equals(payload.getOp()) && EventNames.MESSAGE_CREATE.equals(payload.getType())) {
                        MessageCreate msg = (MessageCreate) payload.getData();
                        String channel = channelNames.getIfPresent(msg.getChannelId());
                        Mono<String> channelName;
                        if (channel == null) {
                            if (client != null) {
                                Mono<Channel> chan = client.getChannelById(Snowflake.of(msg.getChannelId())).cache();
                                channelName = chan.ofType(GuildChannel.class)
                                    .map(c -> "#" + c.getName())
                                    .switchIfEmpty(chan.ofType(PrivateChannel.class).map(c -> "[DM]"))
                                    .doOnNext(n -> channelNames.put(msg.getChannelId(), n));
                            } else {
                                channelName = Mono.just(Long.toUnsignedString(msg.getChannelId()));
                            }
                        } else {
                            channelName = Mono.just(channel);
                        }
                        Long guildId = msg.getGuildId();
                        Mono<String> guildName;
                        if (guildId != null) {
                            String name = guildNames.get(guildId);
                            if (name == null) {
                                if (client != null) {
                                    guildName = client.getGuildById(Snowflake.of(guildId))
                                            .map(Guild::getName)
                                            .doOnNext(n -> guildNames.put(guildId, n));
                                } else {
                                    guildName = Mono.just(Long.toUnsignedString(guildId));
                                }
                            } else {
                                guildName = Mono.just(name);
                            }
                        } else {
                            guildName = Mono.just("");
                        }
                        channelName.zipWith(guildName, (c, g) -> (g.isEmpty() ? "" : "[" + g + "] ") + c + " <" + msg.getAuthor().getUsername() + "#" + msg.getAuthor().getDiscriminator() + "> " + msg.getContent())
                                   .doOnNext(log::info)
                                   .subscribe();
                    }
                }
            }
        }
        return FilterReply.NEUTRAL;
    }
}
