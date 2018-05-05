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
import com.tterrag.k9.util.NullHelper;
import com.tterrag.k9.mcp.NoSuchVersionException;
import com.tterrag.k9.mcp.SrgDatabase;
import com.tterrag.k9.mcp.SrgMappingFactory;

import com.tterrag.k9.util.Nullable;
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
    
    @Override
    public Iterable<ICommand> getChildren() {
        if (isTransient()) {
            return NullHelper.notnullJ(Arrays.stream(MappingType.values()).map(CommandMCP::new).collect(Collectors.toList()), "Arrays#stream");
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
                List<ISrgMapping> srgLookup = srgs.lookup(type, m.getSRG());
                if (srgLookup.size() == 0){
                    ctx.reply("Reverse SRG lookup not found");
                    return;
                }
                ISrgMapping srg = srgLookup.get(0);
                outputEntry(mcver, builder, m, srg);
            });
        } else {
            List<ISrgMapping> srgMappings = srgs.lookup(type, name);
            if (!srgMappings.isEmpty()) {
                for (ISrgMapping srg : srgMappings){
                    outputEntry(mcver, builder, null, srg);
                }
            } else {
                builder.append("No information found!");
            }
        }

        rand.setSeed(builder.toString().hashCode());

        final EmbedBuilder embed = new EmbedBuilder()
        	.setLenient(true)
        	.withDesc(builder.toString())
        	.withColor(Color.HSBtoRGB(rand.nextFloat(), 1, 1));
        
        ctx.reply(embed.build());
    }

    private void outputEntry(String mcver, StringBuilder builder, @Nullable IMapping mcp, ISrgMapping srg) {
        builder.append("\n");
        if(builder.length() > 1) {
            builder.append("\n");
        }
        builder.append("**MC ").append(mcver).append(": ").append(srg.getOwner()).append(type == MappingType.PARAM? ": " : ".");
        if (mcp != null)
            builder.append(mcp.getMCP());
        else
            builder.append(srg.getSRG());
        builder.append("**\n");

        builder.append("__Name__: ").append(type == MappingType.PARAM? "\u2603" : srg.getNotch()).append(" => `").append(srg.getSRG()).append("`");
        if (mcp != null)
            builder.append(" => `").append(mcp.getMCP()).append("`");
        else
            builder.append("\nNo MCP name found");
        builder.append("\n");

        if (mcp != null) {
            builder.append("__Side__: `").append(mcp.getSide()).append("`\n");
        }
        if (type != MappingType.PARAM) {
            if (mcp != null)
                builder.append("__Comment__: `").append(mcp.getComment().isEmpty() ? "None" : mcp.getComment()).append("`\n");

            builder.append("__AT__: `public ").append(Strings.nullToEmpty(srg.getOwner()).replaceAll("/", ".")).append(" ").append(srg.getSRG());
            if(srg instanceof SrgMappingFactory.MethodMapping) {
                SrgMappingFactory.MethodMapping map = (SrgMappingFactory.MethodMapping) srg;
                builder.append(map.getSrgDesc());
            }
            if (mcp != null)
                builder.append(" # ").append(mcp.getMCP());
            builder.append("`");
        }
    }

    @Override
    public String getDescription() {
        return "Looks up MCP info for a given " + type.name().toLowerCase(Locale.US) + ".";
    }
}
