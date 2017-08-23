package com.blamejared.mcbot.util;

import gnu.trove.map.TLongObjectMap;
import gnu.trove.map.hash.TLongObjectHashMap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.guild.channel.message.reaction.ReactionAddEvent;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.handle.obj.IReaction;
import sx.blah.discord.util.RequestBuilder;

import com.blamejared.mcbot.MCBot;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

public enum PaginatedMessageFactory {

	INSTANCE;

	private Queue<PaginatedMessage> sent = new PriorityQueue<PaginatedMessage>();
	
	private TLongObjectMap<PaginatedMessage> byMessageId = new TLongObjectHashMap<PaginatedMessage>();

	@RequiredArgsConstructor
	public class PaginatedMessage implements Comparable<PaginatedMessage> {
		@NonNull
		private final List<@NonNull BakedMessage> messages;
		@NonNull
		private final IChannel channel;
		@Getter
		private int page;
		@Nullable
		private IMessage sentMessage;
		private long lastUpdate;

		@Override
		public int compareTo(PaginatedMessage other) {
			return Long.compare(lastUpdate, other.lastUpdate);
		}
		
		public void send() {
			Preconditions.checkArgument(sentMessage == null, "Paginated message has already been sent!");
			new RequestBuilder(MCBot.instance).shouldBufferRequests(true)
			.doAction(() -> {
				this.sentMessage = messages.get(0).send(channel);
				byMessageId.put(this.sentMessage.getLongID(), PaginatedMessage.this);
				return true;
			}).andThen(() -> {
				this.sentMessage.addReaction(LEFT_ARROW);
				return true;
			}).andThen(() -> {
				this.sentMessage.addReaction(X);
				return true;
			}).andThen(() -> {
				this.sentMessage.addReaction(RIGHT_ARROW);
				return true;
			}).build();
			this.lastUpdate = System.currentTimeMillis();
		}
		
		public void setPage(int page) {
			Preconditions.checkPositionIndex(page, messages.size());
			BakedMessage message = messages.get(page);
			message.update(sentMessage);
			this.page = page;
			this.lastUpdate = System.currentTimeMillis();
		}
		
		public void pageUp() {
			if (page < messages.size() - 1) {
				setPage(page + 1);
			}
		}
		
		public void pageDn() {
			if (page > 0) {
				setPage(page - 1);
			}
		}
		
		public void delete() {
			sentMessage.delete();
		}
	}
	
	@RequiredArgsConstructor
	public class Builder {
		private final List<BakedMessage> messages = new ArrayList<>();
		private final IChannel channel;
		
		public PaginatedMessage build() {
			return new PaginatedMessage(Lists.newArrayList(messages), channel);
		}
		
		public Builder addPage(BakedMessage msg) {
			this.messages.add(msg);
			return this;
		}
		
		public Builder addPages(Collection<? extends BakedMessage> msgs) {
			this.messages.addAll(msgs);
			return this;
		}
	}
	
	public Builder builder(IChannel channel) {
		return new Builder(channel);
	}
	
	/* == Event Handlers == */

	private static final String LEFT_ARROW = "\u2B05";
	private static final String RIGHT_ARROW = "\u27A1";
	private static final String X = "\u274C";

	@EventSubscriber
	public void onReactAdd(ReactionAddEvent event) {
		IReaction reaction = event.getReaction();
		if (!event.getClient().getOurUser().equals(event.getUser()) && !reaction.isCustomEmoji()) {
			String unicode = reaction.getUnicodeEmoji().getUnicode();
			PaginatedMessage message = byMessageId.get(event.getMessage().getLongID());
			if (message != null) {
				switch(unicode) {
				case LEFT_ARROW:
					message.pageDn();
					break;
				case RIGHT_ARROW:
					message.pageUp();
					break;
				case X:
					message.delete();
					byMessageId.remove(event.getMessage().getLongID());
					break;
				}
			}
			event.getMessage().removeReaction(event.getUser(), event.getReaction());
		}
	}
}
