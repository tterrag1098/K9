package com.tterrag.k9.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import com.tterrag.k9.commands.api.CommandContext;
import com.tterrag.k9.util.PaginatedMessageFactory.PaginatedMessage;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@Accessors(fluent = true, chain = true)
@Setter
@RequiredArgsConstructor
public class ListMessageBuilder<T> {
    
    private final String name;
    
    private final List<T> objects = new ArrayList<>();
    
    private boolean protect = true;
    
    private int objectsPerPage = 5;
    
    private Function<? super T, String> stringFunc = Object::toString;
    
    private BiFunction<? super T, Integer, Integer> indexFunc = (obj, i) -> (i + 1);
    
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
        String content = "";
        final int maxPages = (((objects.size() - 1) / objectsPerPage) + 1);
        for (T object : this.objects) {
            if (i % objectsPerPage == 0) {
                if (i != 0) {
                    builder.addPage(new BakedMessage().withContent(content));
                }
                content = "List of " + name + " (Page " + ((i / objectsPerPage) + 1) + "/" + maxPages + "):\n";
            }
            content += indexFunc.apply(object, i) + ") " + stringFunc.apply(object) + "\n";
            i++;
        }
        if (!content.isEmpty()) {
            builder.addPage(new BakedMessage().withContent(content));
        }
        return builder.setParent(ctx.getMessage()).setProtected(protect).build();
    }
}
