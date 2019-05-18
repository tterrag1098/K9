package com.tterrag.k9.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.tterrag.k9.util.annotation.NonNull;
import com.tterrag.k9.util.annotation.NonNullFields;
import com.tterrag.k9.util.annotation.NonNullMethods;
import com.tterrag.k9.util.annotation.Nullable;

import discord4j.core.event.domain.message.ReactionAddEvent;
import discord4j.core.object.entity.GuildChannel;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.MessageChannel;
import discord4j.core.object.reaction.ReactionEmoji;
import discord4j.core.object.util.Snowflake;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import reactor.core.publisher.Mono;

public enum PaginatedMessageFactory {

	INSTANCE;
	
	private final Long2ObjectMap<PaginatedMessage> byMessageId = new Long2ObjectOpenHashMap<>();

	@RequiredArgsConstructor
	@NonNullFields
	@NonNullMethods
	public class PaginatedMessage {
	    
		private final List<@NonNull BakedMessage> messages;
		private final MessageChannel channel;
		@Getter
		@Nullable
		private final Message parent;
		private final boolean isProtected;

		@Getter
		private int page;
		@Nullable
		private Mono<Message> sentMessage;
		
        public Mono<Message> send() {
            Preconditions.checkArgument(sentMessage == null, "Paginated message has already been sent!");
			
			return sentMessage = messages.get(page).send(channel)
			        .doOnNext(msg -> byMessageId.put(msg.getId().asLong(), PaginatedMessage.this))
			        .flatMap(msg -> msg.addReaction(ReactionEmoji.unicode(LEFT_ARROW)).thenReturn(msg))
			        .flatMap(msg -> getParent() != null ? msg.addReaction(ReactionEmoji.unicode(X)).thenReturn(msg) : Mono.just(msg))
			        .flatMap(msg -> msg.addReaction(ReactionEmoji.unicode(RIGHT_ARROW)).thenReturn(msg))
			        .cache();
        }
        
        public int size() {
            return messages.size();
        }
        
        public void setPageNumber(int page) {
            Preconditions.checkPositionIndex(page, messages.size());
            this.page = page;
        }
		
        public Mono<Message> setPage(int page) {
            setPageNumber(page);
			if (sentMessage != null) {
		         return sentMessage.flatMap(messages.get(page)::update);
			}
			return Mono.empty();
		}
		
		public Mono<Message> pageUp() {
			if (page < messages.size() - 1) {
				return setPage(page + 1);
			}
			return sentMessage;
		}
		
		public Mono<Message> pageDn() {
			if (page > 0) {
				return setPage(page - 1);
			}
			return sentMessage;
		}
		
        public Mono<Void> delete() {
            Mono<Void> ret = Mono.empty();
            if (sentMessage != null) {
                ret = ret.then(sentMessage.flatMap(Message::delete));
                sentMessage = null;
            }
            if (parent != null) {
                ret = ret.then(parent.delete());
            }
            return ret;
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
			ret.setPageNumber(page);
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
	
	public Builder builder(MessageChannel channel) {
		return new Builder(channel);
	}
	
	/* == Event Handlers == */

	private static final String LEFT_ARROW = "\u2B05";
	private static final String RIGHT_ARROW = "\u27A1";
	private static final String X = "\u274C";

	public Mono<?> onReactAdd(ReactionAddEvent event) {
	    Snowflake msgId = event.getMessageId();
		ReactionEmoji reaction = event.getEmoji();
		if (!event.getClient().getSelfId().get().equals(event.getUserId())) {
			String unicode = reaction.asUnicodeEmoji().isPresent() ? reaction.asUnicodeEmoji().get().getRaw() : null;
			PaginatedMessage message = byMessageId.get(msgId.asLong());
            if (message != null) {
                if (unicode == null) {
                    return event.getMessage().flatMap(msg -> msg.removeReaction(reaction, event.getUserId()));
                }
                Mono<?> pageChange = Mono.empty();
                if (!message.isProtected() || message.getParent().getAuthor().get().getId().equals(event.getUserId())) {
                    switch (unicode) {
                        case LEFT_ARROW:
                            pageChange = pageChange.then(message.pageDn());
                            break;
                        case RIGHT_ARROW:
                            pageChange = pageChange.then(message.pageUp());
                            break;
                        case X:
                            if (message.getParent().getAuthor().filter(u -> u.getId().equals(event.getUserId())).isPresent()) {
                                pageChange = message.delete();
                                byMessageId.remove(msgId.asLong());
                            }
                            break;
                    }
                }
                return pageChange.then(event.getChannel())
                        .ofType(GuildChannel.class)
                        .flatMap($ -> event.getMessage().flatMap(msg -> msg.removeReaction(reaction, event.getUserId())));
            }
		}
		return Mono.empty();
	}
}
