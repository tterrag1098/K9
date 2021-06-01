package com.tterrag.k9.commands;

import com.tterrag.k9.commands.api.Argument;
import com.tterrag.k9.commands.api.Command;
import com.tterrag.k9.commands.api.CommandBase;
import com.tterrag.k9.commands.api.CommandContext;
import com.tterrag.k9.commands.api.ReadyContext;
import com.tterrag.k9.mappings.mcp.McpDownloader;
import com.tterrag.k9.util.EmbedCreator;
import com.tterrag.k9.util.Monos;
import com.tterrag.k9.util.annotation.Nullable;

import discord4j.common.util.Snowflake;
import reactor.core.publisher.Mono;

@Command
public class CommandMCPVersions extends CommandBase {
    
    private static final Argument<String> ARG_VERSION = CommandMappings.ARG_VERSION;
    
    private CommandMCP mcpCommand;
    
    public CommandMCPVersions() {
        super("mcpv", false);
    }
    
    @Override
    public Mono<?> onReady(ReadyContext ctx) {
        mcpCommand = (CommandMCP) ctx.getK9().getCommands().findCommand((Snowflake) null, "mcp").get();
        return super.onReady(ctx);
    }

    @Override
    public Mono<?> process(CommandContext ctx) {
        return ctx.getArgOrElse(ARG_VERSION, Mono.justOrEmpty(mcpCommand.getData(ctx)))
                .filter(v -> !v.isEmpty())
                .switchIfEmpty(McpDownloader.INSTANCE.getLatestMinecraftVersion(false))
                .flatMap(version -> McpDownloader.INSTANCE.getVersions()
                    .transform(Monos.mapOptional(versions -> versions.getMappings(version)))
                    .map(mappings -> {
                        EmbedCreator.Builder builder = EmbedCreator.builder().title("Latest mappings for MC " + version);
                        StringBuilder desc = new StringBuilder();
                        String stableVersion = null;
                        if (mappings.latestStable() > 0) {
                            stableVersion = "stable_" + mappings.latestStable();
                            desc.append(stableVersion).append("\n");
                        }
                        String snapshotVersion = "snapshot_" + mappings.latestSnapshot();
                        desc.append(snapshotVersion);
                        builder.description(desc.toString());
                        String pattern = Integer.parseInt(version.split("\\.")[1]) >= 13 ? "`mappings channel: '%s', version: '%s-%s'`" : "`mappings = '%s_%s'`";
                        if (stableVersion != null) {
                            builder.field("Gradle String (Stable)", String.format(pattern, "stable", mappings.latestStable(), version), true);
                        }
                        return builder.field("Gradle String (Snapshot)", String.format(pattern, "snapshot", mappings.latestSnapshot(), version), true)
                                      .color(CommandMCP.COLOR);
                    })
                    .switchIfEmpty(ctx.error("No such version: " + version)))
                .flatMap(emb -> ctx.reply(emb.build()));
    }

    @Override
    public String getDescription(@Nullable Snowflake guildId) {
        return "Lists the latest mappings version for the given MC version. If none is given, uses the guild default.";
    }

}
