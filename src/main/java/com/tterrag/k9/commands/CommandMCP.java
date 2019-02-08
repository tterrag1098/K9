package com.tterrag.k9.commands;

import com.tterrag.k9.commands.api.Command;
import com.tterrag.k9.mappings.MappingType;
import com.tterrag.k9.mappings.mcp.McpDownloader;
import com.tterrag.k9.mappings.mcp.McpMapping;
import com.tterrag.k9.util.annotation.NonNull;

@Command
public class CommandMCP extends CommandMappings<@NonNull McpMapping> {
    
    public CommandMCP() {
        super("MCP", McpDownloader.INSTANCE);
    }

    protected CommandMCP(CommandMappings<@NonNull McpMapping> parent, MappingType type) {
        super("mcp", parent, type);
    }

    @Override
    protected CommandMappings<@NonNull McpMapping> createChild(MappingType type) {
        return new CommandMCP(this, type);
    }
}
