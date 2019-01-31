package com.tterrag.k9.util;

import com.tterrag.k9.commands.api.CommandContext;

import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.MessageChannel;
import discord4j.core.spec.EmbedCreateSpec;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Wither;
import reactor.core.publisher.Mono;

@AllArgsConstructor
@Wither
@DefaultNonNull
@Getter
public class BakedMessage {

    @Nullable
	private final String content;
    @Nullable
	private final EmbedCreateSpec embed;
	private final boolean tts;

	public BakedMessage() {
		this(null, null, false);
	}

    public Mono<Message> send(MessageChannel channel) {
    	return channel.createMessage(m -> m.setContent(content).setEmbed(embed));
	}
	
	public Mono<Message> update(Message message) {
		return message.edit(m -> m.setContent(content).setEmbed(embed));
	}
}
