package com.tterrag.k9.commands;

import com.tterrag.k9.commands.api.Command;
import com.tterrag.k9.commands.api.CommandContext;
import com.tterrag.k9.commands.api.CommandException;
import com.tterrag.k9.listeners.CommandListener;
import com.tterrag.k9.mappings.MappingType;
import com.tterrag.k9.mappings.mcp.McpDownloader;
import com.tterrag.k9.mappings.mcp.McpMapping;
import com.tterrag.k9.util.NonNull;

@Command
public class CommandMCP extends CommandMappings<@NonNull McpMapping> {
    
    static final int COLOR = 0x810000;

    public CommandMCP() {
        super("MCP", COLOR, McpDownloader.INSTANCE);
    }

    protected CommandMCP(CommandMappings<@NonNull McpMapping> parent, MappingType type) {
        super("mcp", parent, type);
    }

    @Override
    protected CommandMappings<@NonNull McpMapping> createChild(MappingType type) {
        return new CommandMCP(this, type);
    }
    
    @Override
    public void process(CommandContext ctx) throws CommandException {
        super.process(ctx);
        String name = ctx.getArgOrElse(ARG_NAME, "");
        if (name.startsWith("func_") && this.type != null && this.type != MappingType.METHOD) {
            ctx.replyBuffered("The name `" + name + "` looks like a method. Perhaps you meant to use `" + CommandListener.getPrefix(ctx.getGuild()) + "mcpm`?");
        }
        if (name.startsWith("field_") && this.type != null && this.type != MappingType.FIELD) {
            ctx.replyBuffered("The name `" + name + "` looks like a field. Perhaps you meant to use `" + CommandListener.getPrefix(ctx.getGuild()) + "mcpf`?");
        }
    }
}
