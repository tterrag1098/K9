package com.tterrag.k9.commands;

import com.tterrag.k9.commands.api.Argument;
import com.tterrag.k9.commands.api.Command;
import com.tterrag.k9.commands.api.CommandBase;
import com.tterrag.k9.commands.api.CommandContext;
import com.tterrag.k9.commands.api.CommandException;
import com.tterrag.k9.mappings.mcp.McpDownloader;
import com.tterrag.k9.mappings.mcp.McpVersionJson;
import com.tterrag.k9.mappings.mcp.McpVersionJson.McpMappingsJson;
import com.tterrag.k9.util.EmbedCreator;

@Command
public class CommandMCPVersions extends CommandBase {
    
    private static final Argument<String> ARG_VERSION = CommandMappings.ARG_VERSION;
    
    public CommandMCPVersions() {
        super("mcpv", false);
    }

    @Override
    public void process(CommandContext ctx) throws CommandException {
        String version = ctx.getArg(ARG_VERSION);
        McpVersionJson versions = McpDownloader.INSTANCE.getVersions();
        EmbedCreator.Builder builder = EmbedCreator.builder();

        for (String s : versions.getVersions()) {
            if (version == null || s.equals(version)) {
                McpMappingsJson mappings = versions.getMappings(s);
                StringBuilder body = new StringBuilder();
                if (mappings != null) {
                    if (mappings.latestStable() > 0) {
                        body.append("stable_").append(mappings.latestStable()).append("\n");
                    }
                    body.append("snapshot_").append(mappings.latestSnapshot());
                }
                builder.field("MC " + s, body.toString(), false);
            }
        }
        ctx.replyFinal(builder.build());        
    }

    @Override
    public String getDescription() {
        return "Lists the latest mappings versions.";
    }

}
