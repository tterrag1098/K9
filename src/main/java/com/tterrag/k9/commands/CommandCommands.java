package com.tterrag.k9.commands;

import java.awt.Color;
import java.util.Random;

import com.tterrag.k9.commands.api.Command;
import com.tterrag.k9.commands.api.CommandBase;
import com.tterrag.k9.commands.api.CommandContext;
import com.tterrag.k9.commands.api.CommandException;
import com.tterrag.k9.commands.api.CommandRegistrar;
import com.tterrag.k9.listeners.CommandListener;

import sx.blah.discord.util.EmbedBuilder;

@Command
public class CommandCommands extends CommandBase {
    
    public CommandCommands() {
        super("commands", false);
    }
    
    private final Random rand = new Random();
    
    @Override
    public void process(CommandContext ctx) throws CommandException {
        final StringBuilder builder = new StringBuilder();
        final String prefix = CommandListener.getPrefix(ctx.getGuild());
        CommandRegistrar.INSTANCE.getCommands().forEach((key, val) -> {
            if (val.requirements().matches(ctx.getMessage().getAuthor(), ctx.getGuild())) {
                builder.append(prefix).append(key).append("\n");
            }
        });
        rand.setSeed(builder.toString().hashCode());
        EmbedBuilder embed = new EmbedBuilder()
                .withDesc(builder.toString())
                .withColor(Color.HSBtoRGB(rand.nextFloat(), 1, 1))
                .withTitle("Commands Available:");
        ctx.reply(embed.build());
    }
    
    @Override
    public String getDescription() {
        return "Displays all commands";
    }
}
