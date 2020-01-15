package com.tterrag.k9.commands.api;

import java.io.File;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Supplier;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tterrag.k9.K9;
import com.tterrag.k9.util.GuildStorage;
import com.tterrag.k9.util.SaveHelper;

import discord4j.core.object.entity.Guild;
import discord4j.core.object.util.Snowflake;
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
    public void init(K9 k9, File dataFolder, Gson gson) {
        super.init(k9, dataFolder, gson);
        storage = new GuildStorage<>(id -> newHelper(dataFolder, id, gson).fromJson(getFileName(), getDataType()));
    }
    
    @Override
    public void save(File dataFolder, Gson gson) {
        if (storage != null) {
            for (Entry<Long, T> e : storage) {
                SaveHelper<T> helper = newHelper(dataFolder, e.getKey(), gson);
                helper.writeJson(getFileName(), e.getValue(), getDataType());
            }
        }
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
    
    public final T getData(Guild guild) {
        return storage.get(guild);
    }
    
    public final T getData(Snowflake guild) {
        return storage.get(guild);
    }
}
