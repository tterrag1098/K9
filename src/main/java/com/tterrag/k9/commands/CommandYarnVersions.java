package com.tterrag.k9.commands;

import java.io.File;

import com.google.gson.Gson;
import com.tterrag.k9.K9;
import com.tterrag.k9.commands.api.Argument;
import com.tterrag.k9.commands.api.Command;
import com.tterrag.k9.commands.api.CommandBase;
import com.tterrag.k9.commands.api.CommandContext;
import com.tterrag.k9.mappings.yarn.YarnDownloader;
import com.tterrag.k9.util.EmbedCreator;

import discord4j.core.DiscordClient;
import discord4j.core.object.util.Snowflake;
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
        
        yarnCommand = (CommandYarn) K9.commands.findCommand((Snowflake) null, "yarn").get();
    }

    @Override
    public Mono<?> process(CommandContext ctx) {
        return ctx.getArgOrElse(ARG_VERSION, Mono.justOrEmpty(yarnCommand.getData(ctx)))
                .filter(v -> !v.isEmpty())
                .switchIfEmpty(YarnDownloader.INSTANCE.getLatestMinecraftVersion(false))
                .flatMap(version -> Mono.justOrEmpty(YarnDownloader.INSTANCE.getIndexedVersions().get(version))
                    .map(v -> v.get(0))
                    .map(v -> EmbedCreator.builder()
                            .title("Latest mappings for MC " + version)
                            .description("Version: " + v.getBuild())
                            .field("Full Version", "`" + v.getVersion() + "`", true)
                            .field("Gradle String", "`mappings '" + v.getMaven() + "'`", true)
                            .color(CommandYarn.COLOR))
                    .switchIfEmpty(ctx.error("No such version: " + version)))
                .flatMap(emb -> ctx.reply(emb.build()));
    }

    @Override
    public String getDescription(CommandContext ctx) {
        return "Lists the latest mappings version for the given MC version. If none is given, uses the guild default.";
    }

}
