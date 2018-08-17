package com.tterrag.k9.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.tterrag.k9.K9;
import com.vdurmont.emoji.EmojiManager;

import gnu.trove.map.TLongObjectMap;
import gnu.trove.map.hash.TLongObjectHashMap;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.guild.channel.message.reaction.ReactionAddEvent;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.handle.obj.IReaction;
import sx.blah.discord.util.RequestBuffer;
import sx.blah.discord.util.RequestBuilder;

public enum PaginatedMessageFactory {

	INSTANCE;
	
	private final TLongObjectMap<PaginatedMessage> byMessageId = new TLongObjectHashMap<>();

	@RequiredArgsConstructor
	public class PaginatedMessage implements Comparable<PaginatedMessage> {
		@NonNull
		private final List<@NonNull BakedMessage> messages;
		@NonNull
		private final IChannel channel;
		@Getter
		private final IMessage parent;
		private final boolean isProtected;

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
            final IMessage sent = sentMessage;
			Preconditions.checkArgument(sent == null, "Paginated message has already been sent!");
			new RequestBuilder(K9.instance).shouldBufferRequests(true)
			.doAction(() -> {
				this.sentMessage = messages.get(page).send(channel);
				byMessageId.put(NullHelper.notnull(sentMessage, "PaginatedMessage").getLongID(), PaginatedMessage.this);
				return true;
			}).andThen(() -> {
			    NullHelper.notnull(sentMessage, "PaginatedMessage").addReaction(EmojiManager.getByUnicode(LEFT_ARROW));
				return true;
			}).andThen(() -> {
			    if (getParent() != null) {
			        NullHelper.notnull(sentMessage, "PaginatedMessage").addReaction(EmojiManager.getByUnicode(X));
			    }
				return true;
			}).andThen(() -> {
			    NullHelper.notnull(sentMessage, "PaginatedMessage").addReaction(EmojiManager.getByUnicode(RIGHT_ARROW));
				return true;
			}).build();
			this.lastUpdate = System.currentTimeMillis();
		}
        
        public int size() {
            return messages.size();
        }
		
        public boolean setPage(int page) {
			Preconditions.checkPositionIndex(page, messages.size());
			BakedMessage message = messages.get(page);
			final IMessage sent = sentMessage;
			if (sent != null) {
		         message.update(sent);
			}
			this.page = page;
			this.lastUpdate = System.currentTimeMillis();
			return true;
		}
		
		public boolean pageUp() {
			if (page < messages.size() - 1) {
				return setPage(page + 1);
			}
			return true;
		}
		
		public boolean pageDn() {
			if (page > 0) {
				return setPage(page - 1);
			}
			return true;
		}
		
        public boolean delete() {
            final IMessage sent = sentMessage;
            if (sent != null) {
                sent.delete();
                this.sentMessage = null;
            }
            if (parent != null) {
                parent.delete();
            }
            return true;
        }
        
        public boolean isProtected() {
            return getParent() != null && isProtected;
        }
	}
	
	@RequiredArgsConstructor
    @Accessors(chain = true)
	@Setter
	public class Builder {
		private final @NonNull List<BakedMessage> messages = new ArrayList<>();
		private final @NonNull IChannel channel;

		private IMessage parent;
		private boolean isProtected = true;
		private int page;
		
        public PaginatedMessage build() {
			PaginatedMessage ret = new PaginatedMessage(NullHelper.notnullL(Lists.newArrayList(messages), "Lists#newArrayList"), channel, parent, isProtected);
			ret.setPage(page);
			return ret;
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
	
	public Builder builder(@NonNull IChannel channel) {
		return new Builder(channel);
	}
	
	/* == Event Handlers == */

	private static final String LEFT_ARROW = "\u2B05";
	private static final String RIGHT_ARROW = "\u27A1";
	private static final String X = "\u274C";

	@EventSubscriber
	public void onReactAdd(ReactionAddEvent event) {
	    final IMessage msg = event.getMessage();
	    if (msg == null) {
	        return;
	    }
		IReaction reaction = event.getReaction();
		if (reaction != null && !event.getClient().getOurUser().equals(event.getUser())) {
			String unicode = reaction.getEmoji().isUnicode() ? reaction.getEmoji().getName() : null;
			PaginatedMessage message = byMessageId.get(msg.getLongID());
			RequestBuilder builder = new RequestBuilder(event.getClient()).shouldBufferRequests(true);
            if (message != null) {
                if (unicode == null) {
                    RequestBuffer.request(() -> reaction.getMessage().removeReaction(event.getUser(), reaction));
                    return;
                }
                if (!message.isProtected() || message.getParent().getAuthor().equals(event.getUser())) {
                    switch (unicode) {
                        case LEFT_ARROW:
                            builder.doAction(message::pageDn);
                            break;
                        case RIGHT_ARROW:
                            builder.doAction(message::pageUp);
                            break;
                        case X:
                            // TODO make this not terrible
                            if (message.getParent().getAuthor().equals(event.getUser())) {
                                builder.doAction(message::delete);
                                byMessageId.remove(msg.getLongID());
                            } else {
                                builder.doAction(() -> true);
                            }
                            break;
                    }
                } else {
                    // Because it has to have an initial action
                    builder.doAction(() -> true);
                }
                builder.andThen(() -> {
                    if (msg.isDeleted() || msg.getChannel().isPrivate()) {
                        return false;
                    }
                    msg.removeReaction(event.getUser(), event.getReaction());
                    return true;
	            });
	            builder.execute();
			}
		}
	}
}
