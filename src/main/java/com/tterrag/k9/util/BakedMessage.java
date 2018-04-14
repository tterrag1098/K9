package com.tterrag.k9.util;

import java.util.concurrent.Future;

import com.tterrag.k9.commands.api.CommandContext;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Wither;
import sx.blah.discord.api.internal.json.objects.EmbedObject;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.util.RequestBuffer;
import sx.blah.discord.util.RequestBuffer.IRequest;

@AllArgsConstructor
@Wither
@DefaultNonNull
@Getter
public class BakedMessage {

    @Nullable
	private final String content;
    @Nullable
	private final EmbedObject embed;
	private final boolean tts;

	public BakedMessage() {
		this(null, null, false);
	}

    public IMessage send(IChannel channel) {
		return NullHelper.notnullD(channel.sendMessage(CommandContext.sanitize(channel, content), CommandContext.sanitize(channel, embed), tts), "IChannel#sendMessage");
	}
    
    public Future<IMessage> sendBuffered(IChannel channel) {
        return RequestBuffer.request((IRequest<IMessage>) () -> send(channel));
    }
	
	public void update(IMessage message) {
		message.edit(CommandContext.sanitize(message.getGuild(), content), CommandContext.sanitize(message.getGuild(), embed));
	}
	
    public Future<Void> updateBuffered(IMessage message) {
        return RequestBuffer.request(() -> update(message));
    }
}
