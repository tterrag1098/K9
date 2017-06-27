package com.blamejared.mcbot.commands;

import java.util.List;

import com.blamejared.mcbot.commands.api.Command;
import com.blamejared.mcbot.commands.api.CommandBase;
import com.blamejared.mcbot.commands.api.CommandException;
import com.blamejared.mcbot.mcp.DataDownloader;
import com.blamejared.mcbot.mcp.VersionJson;
import com.blamejared.mcbot.mcp.VersionJson.MappingsJson;

import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.util.EmbedBuilder;

@Command
public class CommandMCPVersions extends CommandBase {

    public CommandMCPVersions() {
        super("mcpv", false);
    }

    @Override
    public void process(IMessage message, List<String> flags, List<String> args) throws CommandException {
        String version = null;
        if (args.size() > 0) {
            version = args.get(0);
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
        
        message.getChannel().sendMessage(builder.build());
    }

    @Override
    public String getUsage() {
        return "[mcver] - Lists all the latest mapping versions.";
    }

}
