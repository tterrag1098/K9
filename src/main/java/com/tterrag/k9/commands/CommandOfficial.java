package com.tterrag.k9.commands;

import com.google.common.collect.ImmutableSet;
import com.tterrag.k9.commands.api.Command;
import com.tterrag.k9.commands.api.CommandContext;
import com.tterrag.k9.listeners.CommandListener;
import com.tterrag.k9.mappings.MappingType;
import com.tterrag.k9.mappings.official.OfficialDownloader;
import com.tterrag.k9.mappings.official.OfficialMapping;
import com.tterrag.k9.util.annotation.NonNull;
import reactor.core.publisher.Mono;

@Command
public class CommandOfficial extends CommandMappings<@NonNull OfficialMapping> {
    static final int COLOR = 0xFFFFFF;

    public CommandOfficial() {
        super("Official", ImmutableSet.of("moj", "mm"), COLOR, OfficialDownloader.INSTANCE);
    }

    protected CommandOfficial(CommandMappings<@NonNull OfficialMapping> parent, MappingType type) {
        super("official", parent, type);
    }

    @Override
    protected CommandMappings<@NonNull OfficialMapping> createChild(MappingType type) {
        return new CommandOfficial(this, type);
    }
    
    @Override
    public Mono<?> process(CommandContext ctx) {
        String name = ctx.getArgOrElse(ARG_NAME, "");
        if (name.startsWith("func_") && this.type != null && this.type != MappingType.METHOD) {
            return ctx.reply("The name `" + name + "` looks like a method. Perhaps you meant to use `" + CommandListener.getPrefix(ctx.getGuildId()) + "officialm`?");
        }
        if (name.startsWith("field_") && this.type != null && this.type != MappingType.FIELD) {
            return ctx.reply("The name `" + name + "` looks like a field. Perhaps you meant to use `" + CommandListener.getPrefix(ctx.getGuildId()) + "officialf`?");
        }
        return super.process(ctx);
    }
}
