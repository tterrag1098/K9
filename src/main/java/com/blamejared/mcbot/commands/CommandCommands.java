package com.blamejared.mcbot.commands;

import java.awt.Color;
import java.util.List;
import java.util.Random;

import com.blamejared.mcbot.commands.api.Command;
import com.blamejared.mcbot.commands.api.CommandBase;
import com.blamejared.mcbot.commands.api.CommandException;
import com.blamejared.mcbot.commands.api.CommandRegistrar;
import com.blamejared.mcbot.listeners.ChannelListener;

import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.util.EmbedBuilder;

@Command
public class CommandCommands extends CommandBase {
    
    public CommandCommands() {
        super("commands", false);
    }
    
    private final Random rand = new Random();
    
    @Override
    public void process(IMessage message, List<String> flags, List<String> args) throws CommandException {
        final EmbedBuilder embed = new EmbedBuilder();
        StringBuilder builder = new StringBuilder();
        CommandRegistrar.INSTANCE.getCommands().forEach((key, val) -> {
            if (val.requirements().matches(message.getAuthor(), message.getGuild())) {
                builder.append(ChannelListener.PREFIX_CHAR).append(key).append(" ").append(val.getUsage()).append("\n");
            }
        });
        embed.withDesc(builder.toString());
        rand.setSeed(builder.toString().hashCode());
        embed.withColor(Color.HSBtoRGB(rand.nextFloat(), 1, 1));
        embed.withTitle("Commands Available:");
        message.getChannel().sendMessage(embed.build());
    }
    
    public String getUsage() {
        return "- Displays all commands";
    }
}
