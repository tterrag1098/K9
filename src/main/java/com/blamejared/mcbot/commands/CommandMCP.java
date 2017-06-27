package com.blamejared.mcbot.commands;

import com.blamejared.mcbot.commands.api.*;
import com.blamejared.mcbot.mcp.*;
import com.blamejared.mcbot.mcp.ISrgMapping.MappingType;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;

import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.util.EmbedBuilder;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

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
    
    @SuppressWarnings("null")
    @Override
    public Iterable<ICommand> getChildren() {
        if (isTransient()) {
            return Arrays.stream(MappingType.values()).map(CommandMCP::new).collect(Collectors.toList());
        }
        return super.getChildren();
    }
    
    @Override
    public void process(IMessage message, List<String> flags, List<String> args) throws CommandException {
    
        String mcver = args.size() > 1 ? args.get(1) : DataDownloader.INSTANCE.getVersions().getLatestVersion();

        if (type == MappingType.CLASS) {
            List<ISrgMapping> classmappings = DataDownloader.INSTANCE.lookupSRG(MappingType.CLASS, args.get(0), mcver);
            if (!classmappings.isEmpty()) {
                message.getChannel().sendMessage(Joiner.on('\n').join(classmappings));
            } else {
                message.getChannel().sendMessage("No class found.");
            }
            return;
        }

        List<IMapping> mappings = DataDownloader.INSTANCE.lookup(type, args.get(0), mcver);
        
        StringBuilder builder = new StringBuilder();
        if (!mappings.isEmpty()) {
            mappings.forEach(m -> {
                // FIXME implement param lookup
                if(type == MappingType.PARAM){
                    ISrgMapping srg = DataDownloader.INSTANCE.lookupSRG(type, m.getSRG(), mcver).get(0);
                    builder.append("**MC " + mcver + ": " + srg.getOwner() + "." + m.getMCP() + "**\n");
                    builder.append("\n`").append(m.getSRG()).append("` <=> `").append(m.getMCP()).append("`");
                    builder.append("\n").append("Side: ").append(m.getSide());
                }else {
                    ISrgMapping srg = DataDownloader.INSTANCE.lookupSRG(type, m.getSRG(), mcver).get(0);
                    builder.append("\n");
                    if(m != mappings.get(0)) {
                        builder.append("\n");
                    }
                    builder.append("**MC " + mcver + ": " + srg.getOwner() + "." + m.getMCP() + "**\n");
                    builder.append("__Name__: " + srg.getNotch() + " => `" + m.getSRG() + "` => " + m.getMCP() + "\n");
                    builder.append("__Comment__: `" + (m.getComment().isEmpty() ? "None" : m.getComment()) + "`\n");
                    builder.append("__Side__: `" + m.getSide() + "`\n");
                    if(srg instanceof SrgMappingFactory.MethodMapping) {
                        SrgMappingFactory.MethodMapping map = (SrgMappingFactory.MethodMapping) srg;
                        builder.append("__AT__: `public ").append(Strings.nullToEmpty(srg.getOwner()).replaceAll("/", ".")).append(" ").append(srg.getSRG()).append(map.getSrgDesc()).append(" #").append(m.getMCP()).append("`");
                    } else
                        builder.append("__AT__: `public ").append(Strings.nullToEmpty(srg.getOwner()).replaceAll("/", ".")).append(" ").append(srg.getSRG()).append(" #").append(m.getMCP()).append("`");
                }
                });
        } else {
            builder.append("No information found!");
        }

        rand.setSeed(builder.toString().hashCode());

        final EmbedBuilder embed = new EmbedBuilder()
        	.setLenient(true)
        	.withDesc(builder.toString())
        	.withColor(Color.HSBtoRGB(rand.nextFloat(), 1, 1));
        
        message.getChannel().sendMessage(escapeMentions(message.getGuild(), embed.build()));
    }
    
    @Override
    public String getUsage() {
        return "<name> [mcver] - Looks up MCP info for a given " + type.name().toLowerCase(Locale.US) + ".";
    }
}
