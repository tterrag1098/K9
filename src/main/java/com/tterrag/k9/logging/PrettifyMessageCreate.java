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
import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClient;
import discord4j.core.object.entity.channel.Channel;
import discord4j.core.object.entity.channel.PrivateChannel;
import discord4j.discordjson.json.ChannelData;
import discord4j.discordjson.json.GuildFields;
import discord4j.discordjson.json.MessageData;
import discord4j.discordjson.json.gateway.MessageCreate;
import discord4j.discordjson.json.gateway.Opcode;
import discord4j.gateway.json.GatewayPayload;
import discord4j.gateway.json.dispatch.EventNames;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j(topic = "com.tterrag.k9.messages")
public class PrettifyMessageCreate extends TurboFilter {
    
    public static DiscordClient client;
    private static Cache<String, String> channelNames = CacheBuilder.newBuilder().expireAfterAccess(1, TimeUnit.HOURS).build();
    private static Map<String, String> guildNames = new HashMap<>();
    
    @Override
    public FilterReply decide(Marker marker, Logger logger, Level level, String format, Object[] params, Throwable t) {
        if (logger.getName().startsWith("discord4j.gateway.inbound.0")) {
            for (Object param : params) {
                if (param instanceof GatewayPayload) {
                    GatewayPayload<?> payload = (GatewayPayload<?>) param;
                    if (Opcode.DISPATCH.equals(payload.getOp()) && EventNames.MESSAGE_CREATE.equals(payload.getType())) {
                        MessageData msg = ((MessageCreate) payload.getData()).message();
                        String channel = channelNames.getIfPresent(msg.channelId());
                        Mono<String> channelName;
                        if (channel == null) {
                            if (client != null) {
                                Mono<ChannelData> chan = client.getChannelById(Snowflake.of(msg.channelId())).getData();
                                channelName = chan.filter(c -> c.type() == Channel.Type.GUILD_TEXT.getValue())
                                    .map(c -> "#" + c.name().get())
                                    .switchIfEmpty(chan.ofType(PrivateChannel.class).map(c -> "[DM]"))
                                    .doOnNext(n -> channelNames.put(msg.channelId(), n));
                            } else {
                                channelName = Mono.just(msg.channelId());
                            }
                        } else {
                            channelName = Mono.just(channel);
                        }
                        String guildId = msg.guildId().get();
                        Mono<String> guildName;
                        if (guildId != null) {
                            String name = guildNames.get(guildId);
                            if (name == null) {
                                if (client != null) {
                                    guildName = client.getGuildById(Snowflake.of(guildId)).getData()
                                            .map(GuildFields::name)
                                            .doOnNext(n -> guildNames.put(guildId, n));
                                } else {
                                    guildName = Mono.just(guildId);
                                }
                            } else {
                                guildName = Mono.just(name);
                            }
                        } else {
                            guildName = Mono.just("");
                        }
                        channelName.zipWith(guildName, (c, g) -> (g.isEmpty() ? "" : "[" + g + "] ") + c + " <" + msg.author().username() + "#" + msg.author().discriminator() + "> " + msg.content())
                                   .doOnNext(log::info)
                                   .subscribe();
                    }
                }
            }
        }
        return FilterReply.NEUTRAL;
    }
}
