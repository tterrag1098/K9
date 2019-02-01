package com.tterrag.k9.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import discord4j.core.event.domain.message.ReactionAddEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.MessageChannel;
import discord4j.core.object.entity.PrivateChannel;
import discord4j.core.object.reaction.ReactionEmoji;
import gnu.trove.map.TLongObjectMap;
import gnu.trove.map.hash.TLongObjectHashMap;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import reactor.core.publisher.Mono;

public enum PaginatedMessageFactory {

	INSTANCE;
	
	private final TLongObjectMap<PaginatedMessage> byMessageId = new TLongObjectHashMap<>();

	@RequiredArgsConstructor
	public class PaginatedMessage implements Comparable<PaginatedMessage> {
		@NonNull
		private final List<@NonNull BakedMessage> messages;
		@NonNull
		private final MessageChannel channel;
		@Getter
		private final Message parent;
		private final boolean isProtected;

		@Getter
		private int page;
		@Nullable
		private Message sentMessage;
		private long lastUpdate;

		@Override
		public int compareTo(PaginatedMessage other) {
			return Long.compare(lastUpdate, other.lastUpdate);
		}
		
        public Mono<Message> send() {
            final Message sent = sentMessage;
			Preconditions.checkArgument(sent == null, "Paginated message has already been sent!");
			
			this.sentMessage = messages.get(page).send(channel).block();
            byMessageId.put(NullHelper.notnull(sentMessage, "PaginatedMessage").getId().asLong(), PaginatedMessage.this);
            NullHelper.notnull(sentMessage, "PaginatedMessage").addReaction(ReactionEmoji.unicode(LEFT_ARROW)).subscribe();
            if (getParent() != null) {
                NullHelper.notnull(sentMessage, "PaginatedMessage").addReaction(ReactionEmoji.unicode(X)).subscribe();
            }
            NullHelper.notnull(sentMessage, "PaginatedMessage").addReaction(ReactionEmoji.unicode(RIGHT_ARROW)).subscribe();
            this.lastUpdate = System.currentTimeMillis();
            
            return Mono.just(this.sentMessage); // TODO
        }
        
        public int size() {
            return messages.size();
        }
		
        public boolean setPage(int page) {
			Preconditions.checkPositionIndex(page, messages.size());
			BakedMessage message = messages.get(page);
			final Message sent = sentMessage;
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
            final Message sent = sentMessage;
            if (sent != null) {
                sent.delete();
                this.sentMessage = null;
            }
            if (parent != null) {
                parent.delete();
            }
            return true;
        }
        
        public BakedMessage getMessage(int page) {
            if (page >= 0 && page < messages.size()) {
                return messages.get(page);
            }
            throw new IndexOutOfBoundsException();
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
		private final @NonNull MessageChannel channel;

		private Message parent;
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
	
	public Builder builder(@NonNull MessageChannel channel) {
		return new Builder(channel);
	}
	
	/* == Event Handlers == */

	private static final String LEFT_ARROW = "\u2B05";
	private static final String RIGHT_ARROW = "\u27A1";
	private static final String X = "\u274C";

	public void onReactAdd(ReactionAddEvent event) {
	    final Message msg = event.getMessage().block();
	    if (msg == null) {
	        return;
	    }
		ReactionEmoji reaction = event.getEmoji();
		if (reaction != null && !event.getClient().getSelfId().get().equals(event.getUserId())) {
			String unicode = reaction.asUnicodeEmoji().isPresent() ? reaction.asUnicodeEmoji().get().getRaw() : null;
			PaginatedMessage message = byMessageId.get(msg.getId().asLong());
            if (message != null) {
                if (unicode == null) {
                    event.getMessage().block().removeReaction(reaction, event.getUserId()).subscribe();
                    return;
                }
                if (!message.isProtected() || message.getParent().getAuthor().equals(event.getUser())) {
                    switch (unicode) {
                        case LEFT_ARROW:
                            message.pageDn();
                            break;
                        case RIGHT_ARROW:
                            message.pageUp();
                            break;
                        case X:
                            if (message.getParent().getAuthor().equals(event.getUser())) {
                                message.delete();
                                byMessageId.remove(msg.getId().asLong());
                            }
                            break;
                    }
                }
                if (!(msg.getChannel().block() instanceof PrivateChannel)) {
                    msg.removeReaction(reaction, event.getUserId());
                }
			}
		}
	}
}
