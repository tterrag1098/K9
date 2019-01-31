package com.tterrag.k9.commands;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tterrag.k9.commands.api.Argument;
import com.tterrag.k9.commands.api.CommandContext;
import com.tterrag.k9.commands.api.CommandContext.TypingStatus;
import com.tterrag.k9.commands.api.CommandException;
import com.tterrag.k9.commands.api.CommandPersisted;
import com.tterrag.k9.commands.api.Flag;
import com.tterrag.k9.commands.api.ICommand;
import com.tterrag.k9.mappings.Mapping;
import com.tterrag.k9.mappings.MappingDatabase;
import com.tterrag.k9.mappings.MappingDownloader;
import com.tterrag.k9.mappings.MappingType;
import com.tterrag.k9.mappings.NoSuchVersionException;
import com.tterrag.k9.util.BakedMessage;
import com.tterrag.k9.util.EmbedCreator;
import com.tterrag.k9.util.ListMessageBuilder;
import com.tterrag.k9.util.NonNull;
import com.tterrag.k9.util.NullHelper;
import com.tterrag.k9.util.PaginatedMessageFactory.PaginatedMessage;
import com.tterrag.k9.util.Requirements;
import com.tterrag.k9.util.Requirements.RequiredType;

import discord4j.core.object.entity.GuildChannel;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.PrivateChannel;
import discord4j.core.object.util.Permission;
import reactor.core.publisher.Mono;

public abstract class CommandMappings<@NonNull M extends Mapping> extends CommandPersisted<String> {
    
    private static final Argument<String> ARG_NAME = new WordArgument(
            "name", 
            "The name to lookup. Makes a best guess for matching, but for best results use an exact name or intermediate ID (i.e. method_1234 -> 1234).", 
            true) {

        @Override
        public boolean required(Collection<Flag> flags) {
            return !flags.contains(FLAG_DEFAULT_VERSION);
        }
    };
    
    static final Argument<String> ARG_VERSION = new WordArgument("version", "The MC version to consider. If not given, will use the default for this guild, or else latest.", false);
    
    private static final Flag FLAG_DEFAULT_VERSION = new SimpleFlag('v', "version", "Set the default lookup version for this guild. Use \"latest\" to unset. Requires manage server permissions.", true);
    private static final Requirements DEFAULT_VERSION_PERMS = Requirements.builder().with(Permission.MANAGE_GUILD, RequiredType.ALL_OF).build();
    
    private final CommandMappings<M> parent;
    
    private final MappingType type;
    
    private final String name;
    private final MappingDownloader<M, ?> downloader;
    
    protected CommandMappings(String name, MappingDownloader<M, ? extends MappingDatabase<M>> downloader) {
        super(name.toLowerCase(Locale.ROOT), false, () -> "");
        this.parent = null;
        this.type = null;
        this.name = name;
        this.downloader = downloader;
    }
    
    protected CommandMappings(String prefix, CommandMappings<M> parent, MappingType type) {
        super(prefix + type.getKey(), false, () -> null);
        this.parent = parent;
        this.type = type;
        this.name = parent.name;
        this.downloader = parent.downloader;
    }
    
    protected abstract CommandMappings<M> createChild(MappingType type);
    
    @Override
    public boolean isTransient() {
        return type == null;
    }
    
    @Override
    public Iterable<ICommand> getChildren() {
        if (isTransient()) {
            return NullHelper.notnullJ(Arrays.stream(MappingType.values()).map(type -> createChild(type)).collect(Collectors.toList()), "Arrays#stream");
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
            if (!DEFAULT_VERSION_PERMS.matches(ctx.getChannel().cast(GuildChannel.class).block().getEffectivePermissions(ctx.getMessage().getAuthorId().get()).block())) {
                throw new CommandException("You do not have permission to update the default version!");
            }
            String version = ctx.getFlag(FLAG_DEFAULT_VERSION);
            if ("latest".equals(version)) {
                parent.storage.put(ctx, "");
            } else if (downloader.getMinecraftVersions().contains(version)) {
                parent.storage.put(ctx, version);
            } else {
                throw new CommandException("Invalid version.");
            }
            ctx.replyFinal("Set default version for this guild to " + version);
            return;
        }
    
        String mcver = ctx.getArgOrGet(ARG_VERSION, () -> {
            String ret = ctx.getChannel().block() instanceof PrivateChannel ? "" : parent.storage.get(ctx).block();
            if (ret.isEmpty()) {
                ret = downloader.getLatestMinecraftVersion();
            }
            return ret;
        });
        
        String name = ctx.getArg(ARG_NAME);

        Future<Collection<M>> mappingsFuture = downloader.lookup(type, name, mcver);
        Collection<M> mappings;
        try {
            mappings = mappingsFuture.get(500, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            try (TypingStatus typing = ctx.setTyping()) {
                Message waitMsg = ctx.reply("Building mappings database, this may take a moment.").block();
                mappings = mappingsFuture.get();
                waitMsg.delete().subscribe();
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
        try (TypingStatus typing = ctx.setTyping()) {
            if (!mappings.isEmpty()) {
                PaginatedMessage msg = new ListMessageBuilder<M>(this.name + " Mappings")
                    .objectsPerPage(5)
                    .showIndex(false)
                    .addObjects(mappings)
                    .stringFunc(m -> m.formatMessage(mcver))
                    .build(ctx);
                
                if (mappings.size() <= 5) {
                    BakedMessage baked = msg.getMessage(0);
                    EmbedCreator.Builder embed = baked.getEmbed().title(null);
                    ctx.replyFinal(embed.build());
                } else {
                    msg.send();
                }
            } else {
                ctx.replyFinal("No information found!");
            }
        }
    }
    
    @Override
    public String getDescription() {
        return "Looks up " + name + " info for a given " + type.name().toLowerCase(Locale.US) + ".";
    }

    @Override
    protected TypeToken<String> getDataType() {
        return TypeToken.get(String.class);
    }
}
