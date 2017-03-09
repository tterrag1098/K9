package com.blamejared.mcbot.commands;

import java.awt.Color;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Scanner;
import java.util.stream.Collectors;

import com.blamejared.mcbot.commands.api.Command;
import com.blamejared.mcbot.commands.api.CommandBase;
import com.blamejared.mcbot.commands.api.CommandException;
import com.blamejared.mcbot.commands.api.ICommand;
import com.blamejared.mcbot.mcp.DataDownloader;
import com.blamejared.mcbot.mcp.IMapping;
import com.blamejared.mcbot.mcp.ISrgMapping;
import com.blamejared.mcbot.mcp.ISrgMapping.MappingType;

import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.util.EmbedBuilder;

@Command
public class CommandMCP extends CommandBase {
    
    private final MappingType type;
    private final Random rand = new Random();
    
    public CommandMCP() {
        this(null);
    }
    
    private CommandMCP(MappingType type) {
        super("mcp" + (type == null ? "" : type.getKey()), false);
        this.type = type;
    }
    
    @Override
    public boolean isTransient() {
        return type == null;
    }
    
    @Override
    public Iterable<ICommand> getChildren() {
        if (isTransient()) {
            return Arrays.stream(MappingType.values()).map(CommandMCP::new).collect(Collectors.toList());
        }
        return super.getChildren();
    }
    
    @Override
    public void process(IMessage message, List<String> flags, List<String> args) throws CommandException {
        if (type == MappingType.CLASS) {
            ISrgMapping classmapping = DataDownloader.INSTANCE.lookupSRG(MappingType.CLASS, args.get(0), "1.11");
            if (classmapping != null) {
                message.getChannel().sendMessage(classmapping.toString());
            } else {
                message.getChannel().sendMessage("No class found.");
            }
            return;
        }

        String mcver = args.size() > 1 ? args.get(1) : "1.11";
        List<IMapping> mappings = DataDownloader.INSTANCE.lookup(type, args.get(0), mcver);
        
        StringBuilder builder = new StringBuilder();
        final EmbedBuilder embed = new EmbedBuilder();

        if (!mappings.isEmpty()) {
            mappings.forEach(m -> {
                // FIXME implement param lookup
                ISrgMapping srg = DataDownloader.INSTANCE.lookupSRG(type, m.getSRG(), mcver);
                builder.append("\n");
                if (m != mappings.get(0)) {
                    builder.append("\n");
                }
                builder.append("**MC " + mcver + ": " + srg.getOwner() + "." + m.getMCP() + "**\n");
                builder.append("__Name__: " + srg.getNotch() + " => " + m.getSRG() + " => " + m.getMCP() + "\n");
                builder.append("__Comment__: " + (m.getComment().isEmpty() ? "None" : m.getComment()) + "\n");
                builder.append("__Side__: " + m.getSide() + "\n");
            });
        } else {
            builder.append("No information found!");
        }

        embed.ignoreNullEmptyFields();
        embed.withDesc(builder.toString());
        rand.setSeed(builder.toString().hashCode());
        embed.withColor(Color.HSBtoRGB(rand.nextFloat(), 1, 1));
        message.getChannel().sendMessage(escapeMentions(embed.build()));
    }
    
    @Override
    public String getUsage() {
        return "<name> [mcver] - Looks up MCP info for a given " + type.name().toLowerCase(Locale.US) + ".";
    }
}
