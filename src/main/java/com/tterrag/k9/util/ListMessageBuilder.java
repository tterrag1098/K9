package com.tterrag.k9.util;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.function.BiFunction;
import java.util.function.Function;

import com.tterrag.k9.util.PaginatedMessageFactory.PaginatedMessage;

import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.MessageChannel;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@Accessors(fluent = true, chain = true)
@Setter
@RequiredArgsConstructor
public class ListMessageBuilder<T> {
    
    private static final Random rand = new Random();
    
    private final String name;
    
    private final List<T> objects = new ArrayList<>();
    
    private boolean protect = true;
    private boolean embed = true;
    private boolean showIndex = true;
    @Setter(AccessLevel.NONE)
    private boolean hasColor;
    private int color;
    
    private int objectsPerPage = 0;
    
    private Function<? super T, String> stringFunc = Object::toString;
    
    private BiFunction<? super T, Integer, Integer> indexFunc = (obj, i) -> (i + 1);
    
    public ListMessageBuilder<T> color(int color) {
        this.color = color;
        this.hasColor = true;
        return this;
    }
    
    public ListMessageBuilder<T> addObject(T object) {
        this.objects.add(object);
        return this;
    }
    
    public ListMessageBuilder<T> addObjects(Collection<? extends T> objects) {
        this.objects.addAll(objects);
        return this;
    }
    
    private String getTitle(int page, int max) {
        return "List of " + name + " (Page " + page + "/" + max + "):";
    }
    
    public PaginatedMessage build(MessageChannel channel, Message parent) {
        PaginatedMessageFactory.Builder builder = PaginatedMessageFactory.INSTANCE.builder(channel);
        List<String> contentPerPage = new ArrayList<>();
        StringBuilder content = new StringBuilder();
        // If this is not going to be an embed, the title must be included in the max size check
        // Since the title length varies, we use the most pessimistic case, and add a buffer for newline characters
        int maxLength = embed ? 2000 : 2000 - getTitle(objects.size(), objects.size()).length() - 2;
        int i = 0;
        for (T object : this.objects) {
            StringBuilder newContent = new StringBuilder();
            if (showIndex) {
                newContent.append(indexFunc.apply(object, i)).append(") ");
            }
            newContent.append(stringFunc.apply(object)).append("\n");
            if ((objectsPerPage > 0 && i == objectsPerPage) || content.length() + newContent.length() > maxLength) {
                contentPerPage.add(content.toString());
                content = newContent;
                i = 0;
            } else {
                content.append(newContent);
            }
            i++;
        }
        if (content.length() > 0) {
            contentPerPage.add(content.toString());
        }
        for (i = 0; i < contentPerPage.size(); i++) {
            String title = getTitle(i + 1, contentPerPage.size());
            addPage(builder, title, contentPerPage.get(i), embed);
        }

        return builder.setParent(parent).setProtected(protect).build();
    }
    
    private void addPage(PaginatedMessageFactory.Builder builder, String title, String content, boolean embed) {
        if (embed) {
            if (hasColor) {
                rand.setSeed(content.hashCode());
            }

            EmbedCreator.Builder embedBuilder = EmbedCreator.builder()
                .title(title)
                .description(content)
                .color(hasColor ? color : Color.HSBtoRGB(rand.nextFloat(), 1, 1));
        
            builder.addPage(new BakedMessage().withEmbed(embedBuilder));
        } else {
            builder.addPage(new BakedMessage().withContent(title + "\n" + content));
        }
    }
}
