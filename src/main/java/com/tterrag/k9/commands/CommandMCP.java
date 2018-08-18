package com.tterrag.k9.commands;

import java.awt.Color;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.stream.Collectors;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tterrag.k9.commands.api.Argument;
import com.tterrag.k9.commands.api.Command;
import com.tterrag.k9.commands.api.CommandContext;
import com.tterrag.k9.commands.api.CommandException;
import com.tterrag.k9.commands.api.CommandPersisted;
import com.tterrag.k9.commands.api.Flag;
import com.tterrag.k9.commands.api.ICommand;
import com.tterrag.k9.mcp.DataDownloader;
import com.tterrag.k9.mcp.IMapping;
import com.tterrag.k9.mcp.ISrgMapping;
import com.tterrag.k9.mcp.ISrgMapping.MappingType;
import com.tterrag.k9.mcp.NoSuchVersionException;
import com.tterrag.k9.mcp.SrgDatabase;
import com.tterrag.k9.mcp.SrgMappingFactory;
import com.tterrag.k9.util.NullHelper;
import com.tterrag.k9.util.Requirements;
import com.tterrag.k9.util.Requirements.RequiredType;

import sx.blah.discord.handle.obj.Permissions;
import sx.blah.discord.util.EmbedBuilder;

@Command
public class CommandMCP extends CommandPersisted<String> {
    
    private static final Argument<String> ARG_NAME = new WordArgument(
            "name", 
            "The name to lookup. Can be a deobf name, srg name, or shortened srg name (i.e. func_12345_x -> 12345).", 
            true) {

        @Override
        public boolean required(Collection<Flag> flags) {
            return !flags.contains(FLAG_DEFAULT_VERSION);
        }
    };
    
    static final Argument<String> ARG_VERSION = new WordArgument("version", "The MC version to consider, defaults to latest.", false);
    
    private static final Flag FLAG_DEFAULT_VERSION = new SimpleFlag('v', "version", "Set the default lookup version for this guild. Requires manage server permissions.", true);
    private static final Requirements DEFAULT_VERSION_PERMS = Requirements.builder().with(Permissions.MANAGE_SERVER, RequiredType.ALL_OF).build();
    
    private final CommandMCP parent;
    
    private final MappingType type;
    private final Random rand = new Random();
    
    public CommandMCP() {
        this(null, null);
    }
    
    private CommandMCP(CommandMCP parent, MappingType type) {
        super("mcp" + (type == null ? "" : type.getKey()), false, () -> null);
        this.parent = parent;
        this.type = type;
    }
    
    @Override
    public boolean isTransient() {
        return type == null;
    }
    
    @Override
    public Iterable<ICommand> getChildren() {
        if (isTransient()) {
            return NullHelper.notnullJ(Arrays.stream(MappingType.values()).map(type -> new CommandMCP(this, type)).collect(Collectors.toList()), "Arrays#stream");
        }
        return super.getChildren();
    }
    
    @Override
    public void init(File dataFolder, Gson gson) {
        if (parent != null || storage == null) {
            super.init(dataFolder, gson);
        }
        if (parent != null) {
            parent.init(dataFolder, gson);
        }
    }
    
    @Override
    public void save(File dataFolder, Gson gson) {
        super.save(dataFolder, gson);
        if (parent != null) {
            parent.save(dataFolder, gson);
        }
    }
    
    @Override
    public void process(CommandContext ctx) throws CommandException {
        
        if (ctx.hasFlag(FLAG_DEFAULT_VERSION)) {
            if (!DEFAULT_VERSION_PERMS.matches(ctx.getChannel().getModifiedPermissions(ctx.getAuthor()))) {
                throw new CommandException("You do not have permission to update the default version!");
            }
            String version = ctx.getFlag(FLAG_DEFAULT_VERSION);
            if (DataDownloader.INSTANCE.getVersions().getVersions().contains(version)) {
                parent.storage.put(ctx, version);
            } else if ("latest".equals(version)) {
                parent.storage.put(ctx, null);
            } else {
                throw new CommandException("Invalid version.");
            }
            return;
        }
    
        String mcver = ctx.getArgOrGet(ARG_VERSION, () -> {
            String ret = parent.storage.get(ctx);
            if (ret == null) {
                ret = DataDownloader.INSTANCE.getVersions().getLatestVersion();
            }
            return ret;
        });
        
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

    @Override
    protected TypeToken<String> getDataType() {
        return TypeToken.get(String.class);
    }
}
