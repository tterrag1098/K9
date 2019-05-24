package com.tterrag.k9.commands.api;

import java.io.File;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.reflect.ClassPath;
import com.google.common.reflect.ClassPath.ClassInfo;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.tterrag.k9.commands.CommandControl;
import com.tterrag.k9.util.Monos;
import com.tterrag.k9.util.NullHelper;
import com.tterrag.k9.util.Patterns;
import com.tterrag.k9.util.annotation.Nullable;

import discord4j.core.DiscordClient;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.User;
import discord4j.core.object.util.Snowflake;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.annotation.NonNull;

@Slf4j
public class CommandRegistrar {
	
    @NonNull
	static final File DATA_FOLDER = NullHelper.notnullJ(Paths.get("command_data").toFile(), "Path#toFile");
	static {
		DATA_FOLDER.mkdirs();
	}
	
	private final DiscordClient client;
	
	private final Map<String, ICommand> commands = Maps.newTreeMap();
	private final CommandControl ctrl = new CommandControl();
	
	private final @NonNull GsonBuilder builder = new GsonBuilder();
	private @NonNull Gson gson = new Gson();
	
	private boolean finishedDefaultSlurp;
	private boolean locked;
	
	private Disposable autoSaveSubscriber;
	
	public CommandRegistrar(DiscordClient client) {
	    this.client = client;
	}

	public Mono<?> invokeCommand(MessageCreateEvent evt, String name, String argstr) {
		Mono<ICommand> commandReq = evt.getGuild()
		        .transform(Monos.asOptional()) // Wrap in optional to hold "null"
		        .flatMap(g -> Mono.justOrEmpty(findCommand(g.orElse(null), name))); // Unwrap null since findCommand handles it TODO improve this API
		
        // This is hardcoded BS but it's for potentially destructive actions like killing the bot, or wiping caches, so I think it's fine. Proper permission handling below.
		ICommand command = commandReq.filter(c -> !c.admin() || evt.getMessage().getAuthor().map(CommandRegistrar::isAdmin).orElse(false)).block();
		if (command == null) {
		    return Mono.empty();
		}
		
	    CommandContext ctx = new CommandContext(evt);
		
		if (!command.requirements().matches(ctx).block()) {
		    return evt.getMessage().getChannel()
		            .flatMap(c -> c.createMessage("You do not have permission to use this command!"))
		            .delayElement(Duration.ofSeconds(5))
		            .flatMap(m -> m.delete());
		}
		
//		evt.getMessage().getChannel().flatMap(c -> c.type()).subscribe();
		
		argstr = Strings.nullToEmpty(argstr);
		
		Map<Flag, String> flags = new HashMap<>();
		Map<Argument<?>, String> args = new HashMap<>();
		
		Map<Character, Flag> keyToFlag = command.getFlags().stream().collect(Collectors.toMap(Flag::name, f -> f));
	    Map<String, Flag> longKeyToFlag = command.getFlags().stream().collect(Collectors.toMap(Flag::longFormName, f -> f));

		Matcher matcher = Patterns.FLAGS.matcher(argstr);
        while (matcher.find()) {
            String flagname = matcher.group(2);
            List<Flag> foundFlags;
            if (matcher.group().startsWith("--")) {
                foundFlags = Collections.singletonList(longKeyToFlag.get(flagname));
            } else if (matcher.group().startsWith("-")) {
                foundFlags = Lists.newArrayList(flagname.chars().mapToObj(i -> keyToFlag.get((char) i)).toArray(Flag[]::new));
            } else {
                continue;
            }
            if (foundFlags.contains(null)) {
                return ctx.reply("Unknown flag(s) \"" + flagname + "\".");
            }
            
            String toreplace = matcher.group(1) + matcher.group(2);

            for (int i = 0; i < foundFlags.size(); i++) {
                Flag flag = foundFlags.get(i);
                String value = null;
                if (i == foundFlags.size() - 1) {
                    if (flag.canHaveValue()) {
                        value = matcher.group(3);
                        if (value == null) {
                            value = matcher.group(4);
                        }
                        toreplace = matcher.group();
                    }
                }
                if (value == null && flag.needsValue()) {
                    return ctx.reply("Flag \"" + flag.longFormName() + "\" requires a value.");
                }

                flags.put(flag, value == null ? flag.getDefaultValue() : value);
            }
            toreplace = Pattern.quote(toreplace) + "\\s*";
            argstr = argstr.replaceFirst(toreplace, "").trim();
            matcher.reset(argstr);
        }

        for (Argument<?> arg : command.getArguments()) {
            boolean required = arg.required(flags.keySet());
            if (required && argstr.isEmpty()) {
                long count = command.getArguments().stream().filter(a -> a.required(flags.keySet())).count();
                return ctx.reply("This command requires at least " + count + " argument" + (count > 1 ? "s" : "") + ".");
            }
            
            matcher = arg.pattern().matcher(argstr);
            
            if (matcher.find()) {
                String match = matcher.group();
                argstr = argstr.replaceFirst(Pattern.quote(match) + "\\s*", "").trim();
                args.put(arg, match);
            } else if (required) {
                return ctx.reply("Argument " + arg.name() + " does not accept input: " + argstr);
            }
        }

        try {
            final Mono<?> commandResult = command.process(ctx.withFlags(flags).withArgs(args))
                    .onErrorResume(CommandException.class, t -> ctx.reply("Could not process command: " + t).then(Mono.empty()))
                    .onErrorResume(t -> ctx.reply("Unexpected error processing command: " + t).then(Mono.empty()));
            return evt.getMessage().getChannel() // Automatic typing indicator
                    .flatMap(c -> c.typeUntil(commandResult).then());
//        } catch (CommandException e) { // TODO remove these blocks
//            return ctx.reply("Could not process command: " + e);
        } catch (RuntimeException e) {
            log.error("Exception invoking command: ", e);
            return ctx.reply("Unexpected error processing command: " + e); // TODO should this be different?
        }
    }
	
	public static boolean isAdmin(User user) {
	    return isAdmin(user.getId().asLong());
	}

	public static boolean isAdmin(long id) {
	    return id == 140245257416736769L; // tterrag
	}
	
	public Mono<ICommand> findCommand(CommandContext ctx, String name) {
	    return ctx.getGuild().transform(Monos.asOptional())
	            .transform(Monos.mapOptional(g -> findCommand(g.orElse(null), name)));
	}

    public Optional<ICommand> findCommand(@Nullable Guild guild, String name) {
        if (guild != null && ctrl.getData(guild).getCommandBlacklist().contains(name)) {
            return Optional.empty();
        }
        return Optional.ofNullable(commands.get(name));
    }

    public void slurpCommands() {
        if (!finishedDefaultSlurp) {
            slurpCommands("com.tterrag.k9.commands");
            finishedDefaultSlurp = true;
        }
    }

	@SneakyThrows
    public void slurpCommands(@NonNull String packagename) {
        if (locked) {
            throw new IllegalStateException("Cannot slurp commands in locked registrar.");
        }
        log.info("Finding all commands in package: {}", packagename);
        ClassLoader loader = getClass().getClassLoader();
        if (loader == null) {
            return; // ??
        }
        ClassPath classpath = ClassPath.from(loader);
		for (ClassInfo foo : classpath.getTopLevelClassesRecursive(packagename)) {
			if (!foo.getName().equals(getClass().getName())) {
				Class<?> c = foo.load();
				if (c.isAnnotationPresent(Command.class)) {
				    log.info("Found annotation command: {}", c.getName());
					registerCommand((ICommand) c.newInstance());
				}
			}
		}
	}
	
	public void registerCommand(ICommand command) {
	    if (locked) {
	        throw new IllegalStateException("Cannot register command to locked registrar.");
	    }
	    if (!command.isTransient()) {
	        commands.put(command.getName(), command);
	        command.gatherParsers(builder);
	        command.onRegister(client);
	    }
	    command.getChildren().forEach(this::registerCommand);
	}

    public void unregisterCommand(ICommand command) {
        commands.remove(command.getName());
        command.onUnregister();
    }
    
    public void complete() {
        registerCommand(ctrl);
        locked = true;
        gson = NullHelper.notnullL(builder.create(), "GsonBuilder#create");
        for (ICommand c : commands.values()) {
            c.init(client, DATA_FOLDER, gson);
        }
        autoSaveSubscriber = Flux.interval(Duration.ofSeconds(30), Duration.ofMinutes(5))
                .doOnNext($ -> saveAll())
                .publishOn(Schedulers.newSingle("Command Auto-save"))
                .subscribe();
    }
    
    private void saveAll() {
        log.info("Saving all command data.");
        for (ICommand c : commands.values()) {
            c.save(DATA_FOLDER, gson);
        }
    }

	public void onShutdown() {
	    saveAll();
		for (ICommand c : commands.values()) {
		    c.onShutdown();
		}
		if (autoSaveSubscriber != null) {
		    autoSaveSubscriber.dispose();
		}
	}
	
	public Iterable<ICommand> getCommands(Optional<Snowflake> guild) {
	    return getCommands(guild.orElse(null));
	}
    
    public Iterable<ICommand> getCommands(@Nullable Snowflake guild) {
        if (guild == null) {
            return commands.values();
        }
        return commands.values().stream().filter(c -> !ctrl.getData(guild).getCommandBlacklist().contains(c.getName()))::iterator;
    }
}
