package com.tterrag.k9.mappings.yarn;

import java.io.File;
import java.io.IOException;
import java.util.List;

import com.tterrag.k9.mappings.FastIntLookupDatabase;
import com.tterrag.k9.mappings.NoSuchVersionException;

public class YarnDatabase extends FastIntLookupDatabase<TinyMapping> {

    public YarnDatabase(String minecraftVersion) {
        super(minecraftVersion);
    }

    @Override
    protected List<TinyMapping> parseMappings() throws NoSuchVersionException, IOException {        
        File folder = YarnDownloader.INSTANCE.getDataFolder().resolve(getMinecraftVersion()).toFile();
        if (!folder.exists()) {
            throw new NoSuchVersionException(getMinecraftVersion());
        }
        File file = folder.listFiles()[0];
        if (file.getName().endsWith("-tiny.gz")) {
            return new TinyV1Parser(this).parse(file);
        } else if (file.getName().endsWith("-v2.jar")) {
            return new TinyV2Parser(this).parse(file);
        } else {
            throw new IllegalStateException("Could not find usable tiny mappings file");
        }
    }
}
