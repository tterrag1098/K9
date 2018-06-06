package com.tterrag.k9.commands;

import java.awt.Color;
import java.util.Random;
import java.util.stream.Collectors;

import com.tterrag.k9.commands.api.Command;
import com.tterrag.k9.commands.api.CommandBase;
import com.tterrag.k9.commands.api.CommandContext;
import com.tterrag.k9.commands.api.CommandException;
import com.tterrag.k9.commands.api.CommandRegistrar;
import com.tterrag.k9.listeners.CommandListener;

import discord4j.core.object.entity.GuildChannel;
import discord4j.core.spec.EmbedCreateSpec;
import reactor.core.publisher.Mono;

@Command
public class CommandCommands extends CommandBase {
    
    public CommandCommands() {
        super("commands", false);
    }
    
    @Override
    public void process(CommandContext ctx) throws CommandException {
        ctx.getGuild().flatMapIterable(CommandRegistrar.INSTANCE::getCommands)
        	.filterWhen(cmd -> Mono.zip(ctx.getMessage().getAuthor(), ctx.getChannel().cast(GuildChannel.class), cmd.requirements()::matches))
        	.zipWith(ctx.getGuild().map(CommandListener::getPrefix), (cmd, pre) -> pre + cmd.getName())
        	.collect(Collectors.joining("\n"))
        	.map(cmds -> new EmbedCreateSpec()
        			.setDescription(cmds)
		        	.setTitle("Commands Available:")
		        	.setColor(Color.HSBtoRGB(new Random(cmds.hashCode()).nextFloat(), 1, 1)))
        	.subscribe(ctx::replyFinal);
    }
    
    @Override
    public String getDescription() {
        return "Displays all commands";
    }
}
