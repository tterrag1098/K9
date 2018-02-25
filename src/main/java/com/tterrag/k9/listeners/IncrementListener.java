package com.tterrag.k9.listeners;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tterrag.k9.commands.api.CommandContext;
import com.tterrag.k9.util.GuildStorage;
import com.tterrag.k9.util.SaveHelper;

import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.util.RequestBuffer;

public enum IncrementListener {
    
    INSTANCE;
    
    private static final Pattern PATTERN = Pattern.compile("^(\\S+)(\\+\\+|--)$");
    
    private static final SaveHelper<Map<String, Long>> saveHelper = new SaveHelper<Map<String, Long>>(new File("counts"), new Gson(), new HashMap<>());
    private static final GuildStorage<Map<String, Long>> counts = new GuildStorage<>(
            id -> saveHelper.fromJson(id + ".json", new TypeToken<Map<String, Long>>(){})
    );

    @EventSubscriber
    public void onMessage(MessageReceivedEvent event) {
        String message = event.getMessage().getFormattedContent();
        
        Matcher matcher = PATTERN.matcher(message);
        if (matcher.matches()) {
            String key = matcher.group(1);
            long incr = matcher.group(2).equals("++") ? 1 : -1;
            long current = counts.get(event.getMessage()).merge(key, incr, (a, b) -> a + b);
            RequestBuffer.request(() -> event.getChannel().sendMessage(CommandContext.sanitize(event.getGuild(), key + " == " + current)));
            saveHelper.writeJson(event.getGuild().getLongID() + ".json", counts.get(event.getMessage()));
        }
    }
}
