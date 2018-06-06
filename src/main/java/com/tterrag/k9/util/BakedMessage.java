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
    	return CommandContext.sanitize(channel, content).flatMap(s -> channel.createMessage(m -> m.setContent(s).setEmbed(embed)));
	}
	
	public Mono<Message> update(Message message) {
		return message.getGuild()
			.flatMap(g -> CommandContext.sanitize(g, content))
			.flatMap(s -> message.edit(m -> m.setContent(s).setEmbed(embed)));
	}
}
