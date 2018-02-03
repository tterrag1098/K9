package com.blamejared.mcbot.commands;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Paths;

import com.blamejared.mcbot.commands.api.Argument;
import com.blamejared.mcbot.commands.api.Command;
import com.blamejared.mcbot.commands.api.CommandBase;
import com.blamejared.mcbot.commands.api.CommandContext;
import com.blamejared.mcbot.commands.api.CommandException;
import com.blamejared.mcbot.commands.api.Flag;
import com.blamejared.mcbot.mcp.DataDownloader;
import com.blamejared.mcbot.mcp.VersionJson;
import com.blamejared.mcbot.mcp.VersionJson.MappingsJson;

import sx.blah.discord.util.EmbedBuilder;

@Command
public class CommandMCPVersions extends CommandBase {

    private static final Flag FLAG_FILE = new SimpleFlag('f', "file", "Causes the bot to send zip file with the SRGs.", false);
    
    private static final Argument<String> ARG_VERSION = CommandMCP.ARG_VERSION;
    
    public CommandMCPVersions() {
        super("mcpv", false);
    }

    @Override
    public void process(CommandContext ctx) throws CommandException {
        String version = ctx.getArg(ARG_VERSION);
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
