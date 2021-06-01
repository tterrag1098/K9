package com.tterrag.k9.commands;

import com.tterrag.k9.commands.api.Argument;
import com.tterrag.k9.commands.api.Command;
import com.tterrag.k9.commands.api.CommandBase;
import com.tterrag.k9.commands.api.CommandContext;
import com.tterrag.k9.commands.api.ReadyContext;
import com.tterrag.k9.mappings.yarn.YarnDownloader;
import com.tterrag.k9.util.EmbedCreator;
import com.tterrag.k9.util.annotation.Nullable;

import discord4j.common.util.Snowflake;
import reactor.core.publisher.Mono;

@Command
public class CommandYarnVersions extends CommandBase {

    private static final Argument<String> ARG_VERSION = CommandMappings.ARG_VERSION;
    
    private CommandYarn yarnCommand;
    
    public CommandYarnVersions() {
        super("yv", false);
    }
    
    @Override
    public Mono<?> onReady(ReadyContext ctx) {   
        yarnCommand = (CommandYarn) ctx.getK9().getCommands().findCommand((Snowflake) null, "yarn").get();
        return super.onReady(ctx);
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
    public String getDescription(@Nullable Snowflake guildId) {
        return "Lists the latest mappings version for the given MC version. If none is given, uses the guild default.";
    }

}
