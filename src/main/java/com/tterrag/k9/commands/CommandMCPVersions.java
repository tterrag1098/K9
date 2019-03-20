package com.tterrag.k9.commands;

import java.io.File;

import com.google.gson.Gson;
import com.tterrag.k9.K9;
import com.tterrag.k9.commands.api.Argument;
import com.tterrag.k9.commands.api.Command;
import com.tterrag.k9.commands.api.CommandBase;
import com.tterrag.k9.commands.api.CommandContext;
import com.tterrag.k9.mappings.mcp.McpDownloader;
import com.tterrag.k9.mappings.mcp.McpVersionJson;
import com.tterrag.k9.mappings.mcp.McpVersionJson.McpMappingsJson;
import com.tterrag.k9.util.EmbedCreator;

import discord4j.core.DiscordClient;
import reactor.core.publisher.Mono;

@Command
public class CommandMCPVersions extends CommandBase {
    
    private static final Argument<String> ARG_VERSION = CommandMappings.ARG_VERSION;
    
    private CommandMCP mcpCommand;
    
    public CommandMCPVersions() {
        super("mcpv", false);
    }
    
    @Override
    public void init(DiscordClient client, File dataFolder, Gson gson) {
        super.init(client, dataFolder, gson);
        
        mcpCommand = (CommandMCP) K9.commands.findCommand(null, "mcp").block();
    }

    @Override
    public Mono<?> process(CommandContext ctx) {
        String version = ctx.getArgOrGet(ARG_VERSION, () -> mcpCommand.getData(ctx).block());
        if (version == null) {
            version = McpDownloader.INSTANCE.getLatestMinecraftVersion();
        }
        McpVersionJson versions = McpDownloader.INSTANCE.getVersions();
        EmbedCreator.Builder builder = EmbedCreator.builder();

        for (String s : versions.getVersions()) {
            if (s.equals(version)) {
                McpMappingsJson mappings = versions.getMappings(s);
                if (mappings == null) {
                    throw new IllegalStateException("No mappings found for MC version: " + s);
                }
                builder.title("Latest mappings for MC " + s);
                StringBuilder desc = new StringBuilder();
                String stableVersion = null;
                if (mappings.latestStable() > 0) {
                    stableVersion = "stable_" + mappings.latestStable();
                    desc.append(stableVersion).append("\n");
                }
                String snapshotVersion = "snapshot_" + mappings.latestSnapshot();
                desc.append(snapshotVersion);
                builder.description(desc.toString());
                String pattern = Integer.parseInt(s.split("\\.")[1]) >= 13 ? "`mappings channel: '%s', version: '%s-%s'`" : "`mappings = '%s_%s'`";
                if (stableVersion != null) {
                    builder.field("Gradle String (Stable)", String.format(pattern, "stable", mappings.latestStable(), version), true);
                }
                builder.field("Gradle String (Snapshot)", String.format(pattern, "snapshot", mappings.latestSnapshot(), version), true);
                builder.color(CommandMCP.COLOR);
            }
        }
        
        if (builder.getFieldCount() == 0) {
            return ctx.error("No such version: " + version);
        }
        return ctx.reply(builder.build());        
    }

    @Override
    public String getDescription() {
        return "Lists the latest mappings version for the given MC version. If none is given, uses the guild default.";
    }

}
