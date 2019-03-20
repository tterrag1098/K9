package com.tterrag.k9.commands;

import com.tterrag.k9.commands.api.Command;
import com.tterrag.k9.commands.api.CommandContext;
import com.tterrag.k9.commands.api.CommandException;
import com.tterrag.k9.listeners.CommandListener;
import com.tterrag.k9.mappings.MappingType;
import com.tterrag.k9.mappings.yarn.TinyMapping;
import com.tterrag.k9.mappings.yarn.YarnDownloader;
import com.tterrag.k9.util.annotation.NonNull;

@Command
public class CommandYarn extends CommandMappings<@NonNull TinyMapping> {
    
    static final int COLOR = 0xDBD0B4;
    
    public CommandYarn() {
        super("Yarn", COLOR, YarnDownloader.INSTANCE);
    }

    protected CommandYarn(CommandMappings<@NonNull TinyMapping> parent, MappingType type) {
        super("y", parent, type);
    }
    
    @Override
    protected CommandMappings<@NonNull TinyMapping> createChild(MappingType type) {
        return new CommandYarn(this, type);
    }
    
    @Override
    public void process(CommandContext ctx) throws CommandException {
        super.process(ctx);
        String name = ctx.getArgOrElse(ARG_NAME, "");
        if (name.startsWith("method_") && this.type != null && this.type != MappingType.METHOD) {
            ctx.replyBuffered("The name `" + name + "` looks like a method. Perhaps you meant to use `" + CommandListener.getPrefix(ctx.getGuild()) + "ym`?");
        }
        if (name.startsWith("field_") && this.type != null && this.type != MappingType.FIELD) {
            ctx.replyBuffered("The name `" + name + "` looks like a field. Perhaps you meant to use `" + CommandListener.getPrefix(ctx.getGuild()) + "yf`?");
        }
    }
}
