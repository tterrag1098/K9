package com.tterrag.k9.util;

import java.io.InputStream;

import com.tterrag.k9.util.annotation.Nullable;

import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.rest.util.AllowedMentions;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Wither;
import reactor.core.publisher.Mono;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Wither
@Getter
public class BakedMessage {

    @Nullable
	private final String content;
    @Nullable
	private final EmbedCreator.Builder embed;
    @Nullable
    private final InputStream file;
    private final String fileName;
	private final boolean tts;

	public BakedMessage() {
		this(null, null, null, "", false);
	}

    public Mono<Message> send(MessageChannel channel) {
        return channel.createMessage(m -> {
            m.setAllowedMentions(AllowedMentions.builder().build());
            if (content != null) {
                m.setContent(content);
            }
            if (embed != null) {
                m.setEmbed(embed.build());
            }
            if (file != null) {
                m.addFile(fileName == null ? "unknown.png" : fileName, file);
            }
            m.setTts(tts);
        });
	}
	
	public Mono<Message> update(Message message) {
        return message.edit(m -> {
            if (content != null) {
                m.setContent(content);
            }
            if (embed != null) {
                m.setEmbed(embed.build());
            }
        });
    }
}
