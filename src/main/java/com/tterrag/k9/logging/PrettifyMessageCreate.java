package com.tterrag.k9.logging;

import java.util.concurrent.TimeUnit;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import discord4j.core.DiscordClient;
import discord4j.core.object.entity.Channel;
import discord4j.core.object.entity.GuildChannel;
import discord4j.core.object.entity.PrivateChannel;
import discord4j.core.object.util.Snowflake;
import discord4j.gateway.json.GatewayPayload;
import discord4j.gateway.json.Opcode;
import discord4j.gateway.json.dispatch.EventNames;
import discord4j.gateway.json.dispatch.MessageCreate;
import reactor.core.publisher.Mono;

public class PrettifyMessageCreate extends MessageConverter {
    
    public static DiscordClient client;
    private static Cache<Long, String> channelNames = CacheBuilder.newBuilder().expireAfterAccess(1, TimeUnit.HOURS).build();

    public String convert(ILoggingEvent event) {
        if (event.getLoggerName().startsWith("discord4j.gateway.inbound.0")) {
            for (Object param : event.getArgumentArray()) {
                if (param instanceof GatewayPayload) {
                    GatewayPayload<?> payload = (GatewayPayload<?>) param;
                    if (Opcode.DISPATCH.equals(payload.getOp()) && EventNames.MESSAGE_CREATE.equals(payload.getType())) {
                        MessageCreate msg = (MessageCreate) payload.getData();
                        String channelName = channelNames.getIfPresent(msg.getChannelId());
                        if (channelName == null) {
                            channelName = Long.toUnsignedString(msg.getChannelId());
                            if (client != null) {
                                Mono<Channel> chan = client.getChannelById(Snowflake.of(msg.getChannelId())).cache();
                                chan.ofType(GuildChannel.class)
                                    .doOnNext(c -> channelNames.put(msg.getChannelId(), "#" + c.getName()))
                                    .then(chan.ofType(PrivateChannel.class))
                                    .flatMap(c -> c.getRecipients().next())
                                    .doOnNext(u -> channelNames.put(msg.getChannelId(), "[DM]"))
                                    .subscribe();
                            }
                        }
                        return " (" + channelName + " <" + msg.getAuthor().getUsername() + "> " + msg.getContent() + ")";
                    }
                }
            }
        }
        return "";
    }
}
