package com.blamejared.mcbot.commands;

import java.awt.Color;
import java.util.Random;

import com.blamejared.mcbot.commands.api.Command;
import com.blamejared.mcbot.commands.api.CommandBase;
import com.blamejared.mcbot.commands.api.CommandContext;
import com.blamejared.mcbot.commands.api.CommandException;
import com.blamejared.mcbot.commands.api.CommandRegistrar;
import com.blamejared.mcbot.listeners.ChannelListener;

import sx.blah.discord.util.EmbedBuilder;

@Command
public class CommandCommands extends CommandBase {
    
    public CommandCommands() {
        super("commands", false);
    }
    
    private final Random rand = new Random();
    
    @Override
    public void process(CommandContext ctx) throws CommandException {
        final EmbedBuilder embed = new EmbedBuilder();
        StringBuilder builder = new StringBuilder();
        CommandRegistrar.INSTANCE.getCommands().forEach((key, val) -> {
            if (val.requirements().matches(ctx.getMessage().getAuthor(), ctx.getGuild())) {
                builder.append(ChannelListener.PREFIX).append(key).append("\n");
            }
        });
        embed.withDesc(builder.toString());
        rand.setSeed(builder.toString().hashCode());
        embed.withColor(Color.HSBtoRGB(rand.nextFloat(), 1, 1));
        embed.withTitle("Commands Available:");
        ctx.reply(embed.build());
    }
    
    public String getDescription() {
        return "Displays all commands";
    }
}
