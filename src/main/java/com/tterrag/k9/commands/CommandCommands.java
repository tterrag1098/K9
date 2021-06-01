package com.tterrag.k9.commands;

import java.util.Random;
import java.util.stream.Collectors;

import com.tterrag.k9.commands.api.Command;
import com.tterrag.k9.commands.api.CommandBase;
import com.tterrag.k9.commands.api.CommandContext;
import com.tterrag.k9.listeners.CommandListener;
import com.tterrag.k9.util.annotation.Nullable;

import discord4j.common.util.Snowflake;
import discord4j.rest.util.Color;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Command
public class CommandCommands extends CommandBase {
    
    public CommandCommands() {
        super("commands", false);
    }
    
    @Override
    public Mono<?> process(CommandContext ctx) {
        final String prefix = CommandListener.getPrefix(ctx.getGuildId());
        return Flux.fromIterable(ctx.getK9().getCommands().getCommands(ctx.getGuildId()))
        	.filterWhen(cmd -> cmd.requirements().matches(ctx))
        	.filter(cmd -> !cmd.admin() || ctx.getK9().isAdmin(ctx.getAuthorId().orElse(Snowflake.of(0))))
        	.map(cmd -> prefix + cmd.getName())
        	.collect(Collectors.joining("\n"))
        	.flatMap(cmds -> ctx.reply(spec -> spec
        			.setDescription(cmds)
		        	.setTitle("Commands Available:")
		        	.setColor(Color.of(java.awt.Color.HSBtoRGB(new Random(cmds.hashCode()).nextFloat(), 1, 1) & 0xFFFFFF))));
    }
    
    @Override
    public String getDescription(@Nullable Snowflake guildId) {
        return "Displays all commands";
    }
}
