package com.blamejared.mcbot.commands.api;

import java.io.File;
import java.util.Map.Entry;
import java.util.function.Supplier;

import com.blamejared.mcbot.util.GuildStorage;
import com.blamejared.mcbot.util.NonNull;
import com.blamejared.mcbot.util.Nullable;
import com.blamejared.mcbot.util.SaveHelper;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;


public abstract class CommandPersisted<T> extends CommandBase {

    protected GuildStorage<T> storage;
    protected Supplier<T> defaultCreator;
    
    protected CommandPersisted(@NonNull String name, boolean admin, @Nullable Supplier<T> defaultCreator) {
        super(name, admin);
        this.defaultCreator = defaultCreator == null ? () -> null : defaultCreator;
    }
    
    @Override
    public void init(File dataFolder, Gson gson) {
        super.init(dataFolder, gson);
        storage = new GuildStorage<T>(id -> newHelper(dataFolder, id, gson).fromJson(getFileName(), getDataType()));
    }
    
    @Override
    public void save(File dataFolder, Gson gson) {
        for (Entry<Long, T> e : storage) {
            SaveHelper<T> helper = newHelper(dataFolder, e.getKey(), gson);
            helper.writeJson(getFileName(), e.getValue());
        }
    }
    
    private SaveHelper<T> newHelper(File root, long guild, Gson gson) {
        return new SaveHelper<T>(getGuildFolder(root, guild), gson, defaultCreator.get());
    }
    
    private File getGuildFolder(File root, long guild) {
        return new File(root, Long.toString(guild));
    }
    
    private String getFileName() {
        return getName() + ".json";
    }
    
    protected abstract TypeToken<T> getDataType();
}
