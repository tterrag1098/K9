package com.tterrag.k9.commands;

import com.tterrag.k9.commands.api.Command;
import com.tterrag.k9.mappings.MappingType;
import com.tterrag.k9.mappings.yarn.TinyMapping;
import com.tterrag.k9.mappings.yarn.YarnDownloader;
import com.tterrag.k9.util.annotation.NonNull;

@Command
public class CommandYarn extends CommandMappings<@NonNull TinyMapping> {
    
    public CommandYarn() {
        super("Yarn", YarnDownloader.INSTANCE);
    }

    protected CommandYarn(CommandMappings<@NonNull TinyMapping> parent, MappingType type) {
        super("y", parent, type);
    }
    
    @Override
    protected CommandMappings<@NonNull TinyMapping> createChild(MappingType type) {
        return new CommandYarn(this, type);
    }
}
