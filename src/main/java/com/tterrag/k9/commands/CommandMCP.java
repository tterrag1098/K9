package com.tterrag.k9.commands;

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
import com.tterrag.k9.mcp.IMCPMapping.Side;
import com.tterrag.k9.mcp.IMemberInfo;
import com.tterrag.k9.mcp.ISrgMapping;
import com.tterrag.k9.mcp.ISrgMapping.MappingType;
import com.tterrag.k9.mcp.NoSuchVersionException;
import com.tterrag.k9.mcp.SrgDatabase;
import com.tterrag.k9.util.BakedMessage;
import com.tterrag.k9.util.ListMessageBuilder;
import com.tterrag.k9.util.NullHelper;
import com.tterrag.k9.util.PaginatedMessageFactory.PaginatedMessage;
import com.tterrag.k9.util.Requirements;
import com.tterrag.k9.util.Requirements.RequiredType;

import sx.blah.discord.api.internal.json.objects.EmbedObject;
import sx.blah.discord.handle.obj.Permissions;

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
    
    static final Argument<String> ARG_VERSION = new WordArgument("version", "The MC version to consider. If not given, will use the default for this guild, or else latest.", false);
    
    private static final Flag FLAG_DEFAULT_VERSION = new SimpleFlag('v', "version", "Set the default lookup version for this guild. Use \"latest\" to unset. Requires manage server permissions.", true);
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
            if ("latest".equals(version)) {
                parent.storage.put(ctx, null);
            } else if (DataDownloader.INSTANCE.getVersions().getVersions().contains(version)) {
                parent.storage.put(ctx, version);
            } else {
                throw new CommandException("Invalid version.");
            }
            ctx.replyBuffered("Set default version for this guild to " + version);
            return;
        }
    
        String mcver = ctx.getArgOrGet(ARG_VERSION, () -> {
            String ret = ctx.getChannel().isPrivate() ? null : parent.storage.get(ctx);
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

        Collection<IMemberInfo> mappings;
        try {
            mappings = DataDownloader.INSTANCE.lookup(type, name, mcver);
        } catch (NoSuchVersionException e) {
            throw new CommandException(e);
        }

        // This might take a lil bit
        ctx.getChannel().setTypingStatus(mappings.size() > 20);
        try {
            if (!mappings.isEmpty()) {
                PaginatedMessage msg = new ListMessageBuilder<IMemberInfo>("Mappings")
                    .objectsPerPage(5)
                    .showIndex(false)
                    .addObjects(mappings)
                    .stringFunc(m -> getMappingData(mcver, m))
                    .build(ctx);
                
                if (mappings.size() <= 5) {
                    BakedMessage baked = msg.getMessage(0);
                    EmbedObject embed = baked.getEmbed();
                    embed.title = null;
                    ctx.replyBuffered(embed);
                } else {
                    msg.send();
                }
            } else {
                ctx.replyBuffered("No information found!");
            }
        } finally {
            ctx.getChannel().setTypingStatus(false);
        }
    }
    
    private String getMappingData(String mcver, IMemberInfo m) {
        StringBuilder builder = new StringBuilder();
        String mcp = m.getMCP();
        builder.append("\n");
        builder.append("**MC " + mcver + ": " + m.getOwner() + "." + (mcp == null ? m.getSRG().replace("_", "\\_") : mcp) + "**\n");
        builder.append("__Name__: " + (m.getType() == MappingType.PARAM ? "`" : m.getNotch() + " => `") + m.getSRG() + (mcp == null ? "`\n" : "` => `" + m.getMCP() + "`\n"));
        String desc = m.getDesc();
        if (desc != null) {
            builder.append("__Descriptor__: `" + desc + "`\n");
        }
        String comment = m.getComment();
        if (comment != null) {
            builder.append("__Comment__: `" + (comment.isEmpty() ? "None" : m.getComment()) + "`\n");
        }
        Side side = m.getSide();
        if (side != null) {
            builder.append("__Side__: `" + side + "`\n");
        }
        builder.append("__AT__: `public ").append(Strings.nullToEmpty(m.getOwner()).replaceAll("/", "."));
        if (m.getType() != MappingType.PARAM) {
            builder.append(" ").append(m.getSRG());
        }
        if (desc != null) {
            builder.append(m.getDesc());
        }
        builder.append(" # ").append(mcp == null ? m.getSRG() : mcp).append("`\n");
        String type = m.getParamType();
        if (type != null) {
            builder.append("__Type__: `" + type + "`\n");
        }
        return builder.toString();
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
