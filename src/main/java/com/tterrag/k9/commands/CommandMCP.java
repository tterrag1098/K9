package com.tterrag.k9.commands;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tterrag.k9.commands.api.Argument;
import com.tterrag.k9.commands.api.Command;
import com.tterrag.k9.commands.api.CommandContext;
import com.tterrag.k9.commands.api.CommandContext.TypingStatus;
import com.tterrag.k9.commands.api.CommandException;
import com.tterrag.k9.commands.api.CommandPersisted;
import com.tterrag.k9.commands.api.Flag;
import com.tterrag.k9.commands.api.ICommand;
import com.tterrag.k9.mappings.Mapping;
import com.tterrag.k9.mappings.MappingType;
import com.tterrag.k9.mappings.NoSuchVersionException;
import com.tterrag.k9.mappings.mcp.McpDownloader;
import com.tterrag.k9.mappings.mcp.McpMapping;
import com.tterrag.k9.mappings.mcp.McpMapping.Side;
import com.tterrag.k9.util.BakedMessage;
import com.tterrag.k9.util.ListMessageBuilder;
import com.tterrag.k9.util.NullHelper;
import com.tterrag.k9.util.PaginatedMessageFactory.PaginatedMessage;
import com.tterrag.k9.util.Requirements;
import com.tterrag.k9.util.Requirements.RequiredType;

import sx.blah.discord.api.internal.json.objects.EmbedObject;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.handle.obj.Permissions;
import sx.blah.discord.util.RequestBuffer;
import sx.blah.discord.util.RequestBuffer.RequestFuture;

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
            } else if (McpDownloader.INSTANCE.getVersions().getVersions().contains(version)) {
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
                ret = McpDownloader.INSTANCE.getVersions().getLatestVersion();
            }
            return ret;
        });
        
        String name = ctx.getArg(ARG_NAME);

        Future<Collection<McpMapping>> mappingsFuture = McpDownloader.INSTANCE.lookup(type, name, mcver);
        Collection<McpMapping> mappings;
        try {
            mappings = mappingsFuture.get(500, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            try (TypingStatus typing = ctx.setTyping()) {
                RequestFuture<IMessage> waitMsg = ctx.replyBuffered("Building mappings database, this may take a moment.");
                mappings = mappingsFuture.get();
                RequestBuffer.request(waitMsg.get()::delete);
            } catch (Exception e2) {
                throw new RuntimeException(e2);
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
        
        if (mappings == null) {
            throw new CommandException(new NoSuchVersionException(mcver));
        }

        // This might take a lil bit
        try (TypingStatus typing = ctx.setTyping(mappings.size() > 20)) {
            if (!mappings.isEmpty()) {
                PaginatedMessage msg = new ListMessageBuilder<McpMapping>("Mappings")
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
        }
    }
    
    private String getMappingData(String mcver, McpMapping m) {
        StringBuilder builder = new StringBuilder();
        String mcp = m.getName();
        builder.append("\n");
        builder.append("**MC " + mcver + ": " + m.getOwner() + "." + (mcp == null ? m.getIntermediate().replace("_", "\\_") : mcp) + "**\n");
        builder.append("__Name__: " + (m.getType() == MappingType.PARAM ? "`" : m.getOriginal() + " => `") + m.getIntermediate() + (mcp == null ? "`\n" : "` => `" + m.getName() + "`\n"));
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
            builder.append(" ").append(m.getIntermediate());
        }
        if (desc != null) {
            builder.append(m.getDesc());
        }
        Mapping parent = m.getParent();
        String parentMcp = parent.getName();
        builder.append(" # ").append(parentMcp == null ? parent.getIntermediate() : parentMcp).append("`\n");
        String type = m.getMemberClass();
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
