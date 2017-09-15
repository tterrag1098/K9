package com.blamejared.mcbot.commands;

import com.blamejared.mcbot.commands.api.*;
import com.blamejared.mcbot.mcp.DataDownloader;
import com.blamejared.mcbot.mcp.VersionJson;
import com.blamejared.mcbot.mcp.VersionJson.MappingsJson;

import com.google.common.collect.Lists;
import sx.blah.discord.util.EmbedBuilder;

import java.io.*;
import java.nio.file.Paths;

@Command
public class CommandMCPVersions extends CommandBase {

    private static final Flag FLAG_FILE = new SimpleFlag("file", "Causes the bot to send zip file with the SRGs.", false);
    
    public CommandMCPVersions() {
        super("mcpv", false, Lists.newArrayList(FLAG_FILE), Lists.newArrayList(CommandMCP.ARG_VERSION));
    }

    @Override
    public void process(CommandContext ctx) throws CommandException {
        String version = ctx.getArg(CommandMCP.ARG_VERSION);
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
        ctx.reply(builder.build());
        if(version !=null && ctx.hasFlag(FLAG_FILE)){
            File srgs = Paths.get(".", "data", version, "srgs", "mcp-" + version + "-srg.zip").toFile();
            File[] mappings = Paths.get(".", "data", version, "mappings").toFile().listFiles();
            try {
                ctx.getChannel().sendFiles(srgs);
                if(mappings!=null){
                    for(File file : mappings) {
                        ctx.getChannel().sendFiles(file);
                    }
                }
            } catch(FileNotFoundException e) {
                e.printStackTrace();
            }
        }
        
    }

    @Override
    public String getDescription() {
        return "Lists the latest mappings versions.";
    }

}
