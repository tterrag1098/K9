package com.tterrag.k9.listeners;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tterrag.k9.commands.api.CommandContext;
import com.tterrag.k9.util.GuildStorage;
import com.tterrag.k9.util.Patterns;
import com.tterrag.k9.util.SaveHelper;

import discord4j.core.event.domain.message.MessageCreateEvent;

public enum IncrementListener {
    
    INSTANCE;
    
    
    private static final SaveHelper<Map<String, Long>> saveHelper = new SaveHelper<>(new File("counts"), new Gson(), new HashMap<>());
    private static final GuildStorage<Map<String, Long>> counts = new GuildStorage<>(
            id -> saveHelper.fromJson(id + ".json", new TypeToken<Map<String, Long>>(){})
    );

    public void onMessage(MessageCreateEvent event) {
        String message = event.getMessage().getContent().get();
        
        Matcher matcher = Patterns.INCREMENT_DECREMENT.matcher(message);
        if (matcher.matches()) {
            String key = matcher.group(1);
            long incr = matcher.group(2).equals("++") ? 1 : -1;
            long current = counts.get(event.getMessage()).block().merge(key, incr, (a, b) -> a + b);
            event.getMessage().getChannel().zipWhen(c -> CommandContext.sanitize(c, key + " == " + current), (chan, content) -> chan.createMessage(spec -> spec.setContent(content))).subscribe();
            saveHelper.writeJson(event.getGuildId().get().asLong() + ".json", counts.get(event.getMessage()).block());
        }
    }
}
