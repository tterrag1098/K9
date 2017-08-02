package com.blamejared.mcbot.commands;

import com.blamejared.mcbot.commands.api.Command;
import com.blamejared.mcbot.commands.api.CommandBase;
import com.blamejared.mcbot.commands.api.CommandContext;
import com.blamejared.mcbot.commands.api.CommandException;
import com.blamejared.mcbot.mcp.DataDownloader;
import com.blamejared.mcbot.mcp.VersionJson;
import com.blamejared.mcbot.mcp.VersionJson.MappingsJson;

import sx.blah.discord.util.EmbedBuilder;

@Command
public class CommandMCPVersions extends CommandBase {

    public CommandMCPVersions() {
        super("mcpv", false);
    }

    @Override
    public void process(CommandContext ctx) throws CommandException {
        String version = null;
        if (ctx.argCount() > 0) {
            version = ctx.getArg(0);
        }
        VersionJson versions = DataDownloader.INSTANCE.getVersions();
        EmbedBuilder builder = new EmbedBuilder();

        for (String s : versions.getVersions()) {
            if (version == null || s.equals(version)) {
                MappingsJson mappings = versions.getMappings(s);
                StringBuilder body = new StringBuilder();
                if (mappings != null) {
                    if (mappings.latestStable() > 0) {
                        body.append("stable_").append(mappings.latestStable()).append("\n");
                    }
                    body.append("snapshot_").append(mappings.latestSnapshot());
                }
                builder.appendField("MC " + s, body.toString(), false);
            }
        }
        
        ctx.getMessage().getChannel().sendMessage(builder.build());
    }

    @Override
    public String getUsage() {
        return "[mcver] - Lists all the latest mapping versions.";
    }

}
