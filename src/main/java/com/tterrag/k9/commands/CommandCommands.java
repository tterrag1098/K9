package com.tterrag.k9.commands;

import java.awt.Color;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.tterrag.k9.commands.api.Command;
import com.tterrag.k9.commands.api.CommandBase;
import com.tterrag.k9.commands.api.CommandContext;
import com.tterrag.k9.commands.api.CommandException;
import com.tterrag.k9.commands.api.CommandRegistrar;
import com.tterrag.k9.listeners.CommandListener;
import com.tterrag.k9.util.Monos;

import discord4j.core.object.entity.GuildChannel;
import discord4j.core.spec.EmbedCreateSpec;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Command
public class CommandCommands extends CommandBase {
    
    public CommandCommands() {
        super("commands", false);
    }
    
    @Override
    public Mono<?> process(CommandContext ctx) throws CommandException {
        final String prefix = CommandListener.getPrefix(ctx.getGuildId());
        return Flux.fromIterable(CommandRegistrar.INSTANCE.getCommands(ctx.getGuildId()))
        	.filterWhen(cmd -> ctx.getMember().transform(Monos.flatZipWith(ctx.getChannel().ofType(GuildChannel.class), cmd.requirements()::matches)))
        	.map(cmd -> prefix + cmd.getName())
        	.collect(Collectors.joining("\n"))
        	.flatMap(cmds -> ctx.reply(spec -> spec
        			.setDescription(cmds)
		        	.setTitle("Commands Available:")
		        	.setColor(new Color(Color.HSBtoRGB(new Random(cmds.hashCode()).nextFloat(), 1, 1) & 0xFFFFFF))));
    }
    
    @Override
    public String getDescription() {
        return "Displays all commands";
    }
}
