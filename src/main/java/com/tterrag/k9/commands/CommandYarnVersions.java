package com.tterrag.k9.commands;

import java.io.File;
import java.util.Map;
import java.util.Map.Entry;

import com.google.gson.Gson;
import com.tterrag.k9.K9;
import com.tterrag.k9.commands.api.Argument;
import com.tterrag.k9.commands.api.Command;
import com.tterrag.k9.commands.api.CommandBase;
import com.tterrag.k9.commands.api.CommandContext;
import com.tterrag.k9.mappings.yarn.YarnDownloader;
import com.tterrag.k9.util.EmbedCreator;

import discord4j.core.DiscordClient;
import gnu.trove.list.array.TIntArrayList;
import reactor.core.publisher.Mono;

@Command
public class CommandYarnVersions extends CommandBase {

    private static final Argument<String> ARG_VERSION = CommandMappings.ARG_VERSION;
    
    private CommandYarn yarnCommand;
    
    public CommandYarnVersions() {
        super("yv", false);
    }
    
    @Override
    public void init(DiscordClient client, File dataFolder, Gson gson) {
        super.init(client, dataFolder, gson);
        
        yarnCommand = (CommandYarn) K9.commands.findCommand(null, "yarn").block();
    }

    @Override
    public Mono<?> process(CommandContext ctx) {
        String version = ctx.getArgOrGet(ARG_VERSION, () -> yarnCommand.getData(ctx).block());
        if (version == null) {
            version = YarnDownloader.INSTANCE.getLatestMinecraftVersion();
        }
        EmbedCreator.Builder builder = EmbedCreator.builder();
        Map<String, TIntArrayList> versions = YarnDownloader.INSTANCE.getIndexedVersions();
        for (Entry<String, TIntArrayList> e : versions.entrySet()) {
            if (e.getKey().equals(version)) {
                TIntArrayList mappings = e.getValue();
                builder.title("Latest mappings for MC " + e.getKey());
                int v = mappings.get(mappings.size() - 1);
                builder.description("Version: " + v);
                String fullVersion = version + "." + v;
                builder.field("Full Version", "`" + fullVersion + "`", true);
                builder.field("Gradle String", "`mappings 'net.fabricmc:yarn:" + fullVersion + "'`", true);
                builder.color(CommandYarn.COLOR);
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
