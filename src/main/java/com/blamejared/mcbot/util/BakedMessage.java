package com.blamejared.mcbot.util;

import com.blamejared.mcbot.commands.api.CommandContext;

import lombok.AllArgsConstructor;
import lombok.experimental.Wither;
import sx.blah.discord.api.internal.json.objects.EmbedObject;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IMessage;

@AllArgsConstructor
@Wither
@DefaultNonNull
public class BakedMessage {

    @Nullable
	private final String content;
    @Nullable
	private final EmbedObject embed;
	private final boolean tts;

	public BakedMessage() {
		this(null, null, false);
	}

	@SuppressWarnings("null")
    public IMessage send(IChannel channel) {
		return channel.sendMessage(CommandContext.sanitize(channel.getGuild(), content), CommandContext.sanitize(channel.getGuild(), embed), tts);
	}
	
	public void update(IMessage message) {
		message.edit(CommandContext.sanitize(message.getGuild(), content), CommandContext.sanitize(message.getGuild(), embed));
	}
}
