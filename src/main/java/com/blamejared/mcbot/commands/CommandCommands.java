package com.blamejared.mcbot.commands;

import com.blamejared.mcbot.commands.api.*;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.util.EmbedBuilder;

import java.awt.*;
import java.util.List;
import java.util.*;

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
            builder.append("!").append(key).append(" ").append(val.getUsage()).append("\n");
        });
        embed.ignoreNullEmptyFields();
        embed.withDesc(builder.toString());
        rand.setSeed(builder.toString().hashCode());
        embed.withColor(Color.HSBtoRGB(rand.nextFloat(), 1, 1));
        embed.withTitle("Commands Available:");
        message.getChannel().sendMessage(embed.build());
    }
    
    public String getUsage() {
        return "";
    }
}
