package com.tterrag.k9.util;

import java.awt.Color;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import discord4j.core.spec.EmbedCreateSpec;
import lombok.Builder;
import lombok.Value;

@Builder(builderClassName = "Builder")
public class EmbedCreator implements Consumer<EmbedCreateSpec> {
    
    public static class Builder {
        
        public Builder field(String title, String description, boolean inline) {
            if (this.fields == null) {
                this.fields = new ArrayList<>();
            }
            this.fields.add(new EmbedField(title, description, inline));
            return this;
        }
    }
    
    @Value
    private static class EmbedField {
        
        String title;
        String description;
        boolean inline;
        
    }
    
    private final String title;
    private final String description;
    private final String image;
    private final String url;
    private final String thumbnail;
    
    private final String authorName, authorUrl, authorIcon;
    private final String footerText, footerIcon;
    
    private final Integer color;
    private final Instant timestamp;
    
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
        if (url != null) {
            t.setUrl(url);
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
        if (fields != null) {
            fields.forEach(f -> t.addField(f.title, f.description, f.inline));
        }
    }
}
