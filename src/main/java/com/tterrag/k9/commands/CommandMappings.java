package com.tterrag.k9.commands;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tterrag.k9.K9;
import com.tterrag.k9.commands.api.Argument;
import com.tterrag.k9.commands.api.CommandContext;
import com.tterrag.k9.commands.api.CommandPersisted;
import com.tterrag.k9.commands.api.Flag;
import com.tterrag.k9.commands.api.ICommand;
import com.tterrag.k9.mappings.Mapping;
import com.tterrag.k9.mappings.MappingDatabase;
import com.tterrag.k9.mappings.MappingDownloader;
import com.tterrag.k9.mappings.MappingType;
import com.tterrag.k9.util.BakedMessage;
import com.tterrag.k9.util.EmbedCreator;
import com.tterrag.k9.util.GuildStorage;
import com.tterrag.k9.util.ListMessageBuilder;
import com.tterrag.k9.util.Monos;
import com.tterrag.k9.util.NullHelper;
import com.tterrag.k9.util.PaginatedMessageFactory.PaginatedMessage;
import com.tterrag.k9.util.Requirements;
import com.tterrag.k9.util.Requirements.RequiredType;
import com.tterrag.k9.util.annotation.NonNull;

import discord4j.core.object.util.Permission;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public abstract class CommandMappings<@NonNull M extends Mapping> extends CommandPersisted<String> {
    
    protected static final Argument<String> ARG_NAME = new WordArgument(
            "name", 
            "The name to lookup. Makes a best guess for matching, but for best results use an exact name or intermediate ID (i.e. method_1234 -> 1234).", 
            true) {

        @Override
        public boolean required(Collection<Flag> flags) {
            return !flags.contains(FLAG_FORCE_UPDATE) && !flags.contains(FLAG_DEFAULT_VERSION);
        }
    };
    
    static final Argument<String> ARG_VERSION = new SentenceArgument("version", "The MC version to consider. If not given, will use the default for this guild, or else latest.", false);
    
    private static final Flag FLAG_FORCE_UPDATE = new SimpleFlag('u', "update", "Forces a check for updates before giving results.", false);
    private static final Flag FLAG_DEFAULT_VERSION = new SimpleFlag('v', "version", "Set the default lookup version for this guild. Use \"latest\" to unset. Requires manage server permissions.", true);
    private static final Requirements DEFAULT_VERSION_PERMS = Requirements.builder().with(Permission.MANAGE_GUILD, RequiredType.ALL_OF).build();
    
    private final CommandMappings<M> parent;
    
    protected final MappingType type;
    
    private final String name;
    private final int color;
    private final MappingDownloader<M, ?> downloader;
    
    protected CommandMappings(String name, int color, MappingDownloader<M, ? extends MappingDatabase<M>> downloader) {
        super(name.toLowerCase(Locale.ROOT), false, () -> "");
        this.parent = null;
        this.type = null;
        this.name = name;
        this.color = color;
        this.downloader = downloader;
    }
    
    protected CommandMappings(String prefix, CommandMappings<M> parent, MappingType type) {
        super(prefix + type.getKey(), false, () -> null);
        this.parent = parent;
        this.type = type;
        this.name = parent.name;
        this.color = parent.color;
        this.downloader = parent.downloader;
    }
    
    protected abstract CommandMappings<M> createChild(MappingType type);
    
    @Override
    public Iterable<ICommand> getChildren() {
        if (parent == null) {
            return NullHelper.notnullJ(Arrays.stream(MappingType.values()).map(type -> createChild(type)).collect(Collectors.toList()), "Arrays#stream");
        }
        return super.getChildren();
    }
    
    @Override
    public void init(K9 k9, File dataFolder, Gson gson) {
        if (parent != null || storage == null) {
            super.init(k9, dataFolder, gson);
        }
        if (parent != null) {
            parent.init(k9, dataFolder, gson);
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
    public Mono<?> process(CommandContext ctx) {
        
        final GuildStorage<String> storage = parent == null ? this.storage : parent.storage;
        
        if (ctx.hasFlag(FLAG_DEFAULT_VERSION)) {
            if (!ctx.getGuildId().isPresent()) {
                return ctx.error("Cannot set default version in DMs.");
            }
            if (!DEFAULT_VERSION_PERMS.matches(ctx).block()) {
                return ctx.error("You do not have permission to update the default version!");
            }
            String version = ctx.getFlag(FLAG_DEFAULT_VERSION);
            Mono<String> ret;
            if ("latest".equals(version)) {
                ret = storage.put(ctx, "");
            } else if (downloader.getMinecraftVersions().any(version::equals).block()) {
                ret = storage.put(ctx, version);
            } else {
                return ctx.error("Invalid version.");
            }
            return ret.defaultIfEmpty("latest")
                    .flatMap(prev -> ctx.reply("Changed default version for this guild from " + (prev.isEmpty() ? "latest" : prev) + " to " + version));
        }
    
        String mcver = ctx.getArgOrGet(ARG_VERSION, () -> {
            String ret = storage.get(ctx).orElse("");
            if (ret == null || ret.isEmpty()) {
                ret = downloader.getLatestMinecraftVersion(false).block();
            }
            return ret;
        });
        
        String name = ctx.getArg(ARG_NAME);
        Mono<Void> updateCheck = Mono.empty();
        if (ctx.hasFlag(FLAG_FORCE_UPDATE)) {
            updateCheck = downloader.forceUpdateCheck(mcver);
            if (name == null) {
                return updateCheck.then(ctx.reply("Updated mappings for MC " + mcver));
            }
        }
        Flux<M> mappingsFlux = updateCheck.thenMany(type == null ? downloader.lookup(name, mcver) : downloader.lookup(type, name, mcver));
        
        return mappingsFlux.collectList()
                .transform(Monos.flatZipWith(ctx.getChannel(), (mappings, channel) -> {
                    if (!mappings.isEmpty()) {
                        PaginatedMessage msg = new ListMessageBuilder<M>(this.name + " Mappings")
                            .showIndex(false)
                            .embed(false)
                            .addObjects(mappings)
                            .stringFunc(m -> m.formatMessage(mcver))
                            .color(color)
                            .build(channel, ctx.getMessage());
                        return msg.send();
                    } else {
                        return ctx.reply("No information found!");
                    }
                }));
    }
    
    @Override
    public String getDescription(CommandContext ctx) {
        return type == null ? "Looks up " + name + " info." : "Looks up " + name + " info for a given " + type.name().toLowerCase(Locale.US) + ".";
    }

    @Override
    protected TypeToken<String> getDataType() {
        return TypeToken.get(String.class);
    }
}
