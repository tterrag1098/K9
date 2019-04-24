package com.tterrag.k9.commands;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.google.gson.Gson;
import com.tterrag.k9.commands.api.Argument;
import com.tterrag.k9.commands.api.Command;
import com.tterrag.k9.commands.api.CommandBase;
import com.tterrag.k9.commands.api.CommandContext;
import com.tterrag.k9.commands.api.CommandException;
import com.tterrag.k9.commands.api.CommandRegistrar;
import com.tterrag.k9.mappings.yarn.MappingsVersion;
import com.tterrag.k9.mappings.yarn.YarnDownloader;

import gnu.trove.list.array.TIntArrayList;
import sx.blah.discord.util.EmbedBuilder;

@Command
public class CommandYarnVersions extends CommandBase {

    private static final Argument<String> ARG_VERSION = CommandMappings.ARG_VERSION;
    
    private CommandYarn yarnCommand;
    
    public CommandYarnVersions() {
        super("yv", false);
    }
    
    @Override
    public void init(File dataFolder, Gson gson) {
        super.init(dataFolder, gson);
        
        yarnCommand = (CommandYarn) CommandRegistrar.INSTANCE.findCommand(null, "yarn");
    }

    @Override
    public void process(CommandContext ctx) throws CommandException {
        String version = ctx.getArgOrGet(ARG_VERSION, () -> yarnCommand.getData(ctx));
        if (version == null) {
            version = YarnDownloader.INSTANCE.getLatestMinecraftVersion();
        }
        EmbedBuilder builder = new EmbedBuilder();
        Map<String, List<MappingsVersion>> versions = YarnDownloader.INSTANCE.getIndexedVersions();
        for (Entry<String, List<MappingsVersion>> e : versions.entrySet()) {
            if (e.getKey().equals(version)) {
                List<MappingsVersion> mappings = e.getValue();
                builder.withTitle("Latest mappings for MC " + e.getKey());
                MappingsVersion v = mappings.get(0);
                builder.withDesc("Version: " + v.getBuild());
                builder.appendField("Full Version", "`" + v.getVersion() + "`", true);
                builder.appendField("Gradle String", "`mappings '" + v.getMaven() + "'`", true);
                builder.withColor(CommandYarn.COLOR);
            }
        }
        if (builder.getFieldCount() == 0) {
            throw new CommandException("No such version: " + version);
        }
        ctx.reply(builder.build());
    }

    @Override
    public String getDescription() {
        return "Lists the latest mappings version for the given MC version. If none is given, uses the guild default.";
    }

}
