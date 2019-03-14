package com.tterrag.k9.util;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.function.BiFunction;
import java.util.function.Function;

import com.tterrag.k9.commands.api.CommandContext;
import com.tterrag.k9.util.PaginatedMessageFactory.PaginatedMessage;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import sx.blah.discord.util.EmbedBuilder;

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
    
    private int objectsPerPage = 5;
    
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
    
    public PaginatedMessage build(CommandContext ctx) {
        PaginatedMessageFactory.Builder builder = PaginatedMessageFactory.INSTANCE.builder(ctx.getChannel());
        int i = 0;
        String title = "";
        StringBuilder content = new StringBuilder();
        final int maxPages = (((objects.size() - 1) / objectsPerPage) + 1);
        for (T object : this.objects) {
            if (i % objectsPerPage == 0) {
                if (i != 0) {
                    addPage(builder, title, content.toString(), embed);
                }
                content = new StringBuilder();
                title = "List of " + name + " (Page " + ((i / objectsPerPage) + 1) + "/" + maxPages + "):";
            }
            if (showIndex) {
                content.append(indexFunc.apply(object, i)).append(") ");
            }
            content.append(stringFunc.apply(object)).append("\n");
            i++;
        }
        if (content.length() > 0) {
            addPage(builder, title, content.toString(), embed);
        }
        return builder.setParent(ctx.getMessage()).setProtected(protect).build();
    }
    
    private void addPage(PaginatedMessageFactory.Builder builder, String title, String content, boolean embed) {
        if (embed) {
            if (hasColor) {
                rand.setSeed(content.hashCode());
            }

            final EmbedBuilder embedBuilder = new EmbedBuilder()
                .setLenient(true)
                .withTitle(title)
                .withDesc(content)
                .withColor(hasColor ? color : Color.HSBtoRGB(rand.nextFloat(), 1, 1));
        
            builder.addPage(new BakedMessage().withEmbed(embedBuilder.build()));
        } else {
            builder.addPage(new BakedMessage().withContent(title + "\n" + content));
        }
    }
}
