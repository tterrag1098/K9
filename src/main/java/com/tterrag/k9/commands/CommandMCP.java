package com.tterrag.k9.commands;

import java.awt.Color;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.stream.Collectors;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.tterrag.k9.commands.api.Argument;
import com.tterrag.k9.commands.api.Command;
import com.tterrag.k9.commands.api.CommandBase;
import com.tterrag.k9.commands.api.CommandContext;
import com.tterrag.k9.commands.api.CommandException;
import com.tterrag.k9.commands.api.ICommand;
import com.tterrag.k9.mcp.DataDownloader;
import com.tterrag.k9.mcp.IMapping;
import com.tterrag.k9.mcp.ISrgMapping;
import com.tterrag.k9.mcp.ISrgMapping.MappingType;
import com.tterrag.k9.mcp.NoSuchVersionException;
import com.tterrag.k9.mcp.SrgDatabase;
import com.tterrag.k9.mcp.SrgMappingFactory;

import sx.blah.discord.util.EmbedBuilder;

@Command
public class CommandMCP extends CommandBase {
    
    private static final Argument<String> ARG_NAME = new WordArgument(
            "name",
            "The name to lookup. Can be a deobf name, srg name, or shortened srg name (i.e. func_12345_x -> 12345).",
            true
    );
    
    static final Argument<String> ARG_VERSION = new WordArgument("version", "The MC version to consider, defaults to latest.", false);
    
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
    public void process(CommandContext ctx) throws CommandException {
    
        String mcver = ctx.getArgOrGet(ARG_VERSION, DataDownloader.INSTANCE.getVersions()::getLatestVersion);
        
        SrgDatabase srgs;
        try {
            srgs = DataDownloader.INSTANCE.getSrgDatabase(mcver);
        } catch (NoSuchVersionException e) {
            throw new CommandException(e);
        }
        
        String name = ctx.getArg(ARG_NAME);

        if (type == MappingType.CLASS) {
            List<ISrgMapping> classmappings = srgs.lookup(MappingType.CLASS, name);
            if (!classmappings.isEmpty()) {
                ctx.reply(Joiner.on('\n').join(classmappings));
            } else {
                ctx.reply("No class found.");
            }
            return;
        }

        List<IMapping> mappings;
        try {
            mappings = DataDownloader.INSTANCE.lookup(type, name, mcver);
        } catch (NoSuchVersionException e) {
            throw new CommandException(e);
        }
        
        StringBuilder builder = new StringBuilder();
        if (!mappings.isEmpty()) {
            mappings.forEach(m -> {
                // FIXME implement param lookup
                ISrgMapping srg = srgs.lookup(type, m.getSRG()).get(0);
                if(type == MappingType.PARAM){
                    builder.append("**MC " + mcver + ": " + srg.getOwner() + "." + m.getMCP() + "**\n");
                    builder.append("\n`").append(m.getSRG()).append("` <=> `").append(m.getMCP()).append("`");
                    builder.append("\n").append("Side: ").append(m.getSide());
                } else {
                    builder.append("\n");
                    if(m != mappings.get(0)) {
                        builder.append("\n");
                    }
                    builder.append("**MC " + mcver + ": " + srg.getOwner() + "." + m.getMCP() + "**\n");
                    builder.append("__Name__: " + srg.getNotch() + " => `" + m.getSRG() + "` => " + m.getMCP() + "\n");
                    builder.append("__Comment__: `" + (m.getComment().isEmpty() ? "None" : m.getComment()) + "`\n");
                    builder.append("__Side__: `" + m.getSide() + "`\n");
                    builder.append("__AT__: `public ").append(Strings.nullToEmpty(srg.getOwner()).replaceAll("/", ".")).append(" ").append(srg.getSRG());
                    if(srg instanceof SrgMappingFactory.MethodMapping) {
                        SrgMappingFactory.MethodMapping map = (SrgMappingFactory.MethodMapping) srg;
                        builder.append(map.getSrgDesc());
                    }
                    builder.append(" # ").append(m.getMCP()).append("`");
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
        
        ctx.reply(embed.build());
    }
    
    @Override
    public String getDescription() {
        return "Looks up MCP info for a given " + type.name().toLowerCase(Locale.US) + ".";
    }
}
