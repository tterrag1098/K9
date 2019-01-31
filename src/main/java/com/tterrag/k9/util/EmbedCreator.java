package com.tterrag.k9.util;

import java.awt.Color;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

import discord4j.core.spec.EmbedCreateSpec;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

@Builder(builderClassName = "Builder")
public class EmbedCreator implements Consumer<EmbedCreateSpec> {
    
    @Value
    public static class EmbedField {
        
        String title;
        String description;
        boolean inline;
        
    }
    
    private final String title;
    private final String description;
    private final String image;
    private final String thumbnail;
    
    private final String authorName, authorUrl, authorIcon;
    private final String footerText, footerIcon;
    
    private final Integer color;
    private final Instant timestamp;
    
    @Singular
    private final List<EmbedField> fields;

    @Override
    public void accept(EmbedCreateSpec t) {
        if (title != null) {
            t.setTitle(title);
        }
        if (description != null) {
            t.setDescription(description);
        }
        if (image != null) {
            t.setImage(image);
        }
        if (thumbnail != null) {
            t.setThumbnail(thumbnail);
        }
        if (authorName != null) {
            t.setAuthor(authorName, authorUrl, authorIcon);
        }
        if (footerText != null) {
            t.setFooter(footerText, footerIcon);
        }
        if (color != null) {
            t.setColor(new Color(color));
        }
        if (timestamp != null) {
            t.setTimestamp(timestamp);
        }
        fields.forEach(f -> t.addField(f.title, f.description, f.inline));
    }
}
