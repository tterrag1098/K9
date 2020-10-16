package com.tterrag.k9.commands.api;

import java.io.File;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Supplier;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tterrag.k9.util.GuildStorage;
import com.tterrag.k9.util.SaveHelper;

import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.channel.Channel;
import org.apache.commons.lang3.tuple.Pair;
import reactor.core.publisher.Mono;
import reactor.util.annotation.NonNull;
import reactor.util.annotation.Nullable;


public abstract class CommandPersisted<T> extends CommandBase {

    protected GuildStorage<T> storage;
    protected Supplier<T> defaultCreator;
    
    protected CommandPersisted(@NonNull String name, boolean admin, @Nullable Supplier<T> defaultCreator) {
        super(name, admin);
        this.defaultCreator = defaultCreator == null ? () -> null : defaultCreator;
    }
    
    @Override
    public Mono<?> onReady(ReadyContext ctx) {
        return super.onReady(ctx)
                .then(Mono.fromRunnable(() -> 
                    storage = new GuildStorage<>(id -> {
                        T ret = newHelper(ctx.getDataFolder(), id.getKey(), ctx.getGson()).fromJson(getFileName(), getDataType());
                        onLoad(id, ret);
                        return ret;
                    })));
    }
    
    @Override
    public void save(File dataFolder, Gson gson) {
        if (storage != null) {
            for (Entry<Pair<Long, Long>, T> e : storage.snapshot().entrySet()) {
                SaveHelper<T> helper = newHelper(dataFolder, e.getKey().getKey(), gson);
                helper.writeJson(getFileName(), e.getValue(), getDataType());
            }
        }
    }
    
    protected void onLoad(Pair<Long, Long> key, T data) {
    }
    
    private SaveHelper<T> newHelper(File root, long guild, Gson gson) {
        return new SaveHelper<>(getGuildFolder(root, guild), gson, defaultCreator.get());
    }
    
    private File getGuildFolder(File root, long guild) {
        return new File(root, Long.toString(guild));
    }
    
    private String getFileName() {
        return getName() + ".json";
    }
    
    protected abstract TypeToken<T> getDataType();
    
    public final Optional<T> getData(CommandContext ctx) {
        return storage.get(ctx);
    }

    public final Optional<T> getData(CommandContext ctx, boolean useChannel) {
        return storage.get(ctx, useChannel);
    }
    
    public final T getData(Guild guild) {
        return storage.get(guild);
    }

    public final T getData(Guild guild, Channel channel) {
        return storage.get(guild, channel);
    }
    
    public final T getData(Snowflake guild) {
        return storage.get(guild);
    }

    public final T getData(Snowflake guild, Snowflake channel) {
        return storage.get(guild, channel);
    }
}
