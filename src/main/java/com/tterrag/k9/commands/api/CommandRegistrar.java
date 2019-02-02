package com.tterrag.k9.commands.api;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
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
import com.tterrag.k9.commands.CommandControl.ControlData;
import com.tterrag.k9.util.NullHelper;
import com.tterrag.k9.util.Nullable;
import com.tterrag.k9.util.Patterns;

import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.util.annotation.NonNull;

@Slf4j
public enum CommandRegistrar {
	
	INSTANCE;
	
    @NonNull
	static final File DATA_FOLDER = NullHelper.notnullJ(Paths.get("command_data").toFile(), "Path#toFile");
	static {
		DATA_FOLDER.mkdirs();
	}
	
	private final Map<String, ICommand> commands = Maps.newTreeMap();
	private final CommandControl ctrl = new CommandControl();
	private final Timer autoSaveTimer = new Timer();
	
	private final @NonNull GsonBuilder builder = new GsonBuilder();
	private @NonNull Gson gson = new Gson();
	
	private boolean finishedDefaultSlurp;
	private boolean locked;
	
	private CommandRegistrar() {
		autoSaveTimer.scheduleAtFixedRate(new TimerTask() {
			
			@Override
			public void run() {
				INSTANCE.saveAll();
			}
		}, TimeUnit.SECONDS.toMillis(30), TimeUnit.MINUTES.toMillis(5));
	}

	public Mono<?> invokeCommand(Message message, String name, String argstr) {
		Mono<ICommand> commandReq = message.getGuild().flatMap(g -> findCommand(g, name));

        // This is hardcoded BS but it's for potentially destructive actions like killing the bot, or wiping caches, so I think it's fine. Proper permission handling below.
		ICommand command = commandReq.filterWhen(c -> message.getAuthor().map(u -> !c.admin() || isAdmin(u))).block();
		if (command == null) {
		    return Mono.empty();
		}
		
//		command = command.flatMap(c -> {
//		    Requirements req = c.requirements();
//		    if (!req.matches(message.getAuthor))
//		}
//		message.getAuthor()
//		       .zipWith(message.getGuild())
//		       .flatMap(t -> req.matches(t.getT1(), t.getT2()))
//		       .
//		if (!req.matches(message.getAuthor(), message.getGuild())) {
//		    Message msg = message.getChannel().sendMessage("You do not have permission to use this command!");
//		    Threads.sleep(5000);
//		    msg.delete();
//		    return;
//		}
		
		argstr = Strings.nullToEmpty(argstr);
		
		CommandContext ctx = new CommandContext(message);

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
                
                try {
                    StringBuilder sb = new StringBuilder();
                    for (byte b : match.getBytes("UTF-32")) {
                        sb.append(String.format("%02x", b));
                    }
                    System.out.println(arg.name() + ": " + sb);
                } catch (UnsupportedEncodingException e1) {
                    // TODO Auto-generated catch block
                    e1.printStackTrace();
                }
            } else if (required) {
                return ctx.reply("Argument " + arg.name() + " does not accept input: " + argstr);
            }
        }

        try {
            return command.process(ctx.withFlags(flags).withArgs(args));
        } catch (CommandException e) {
            return ctx.reply("Could not process command: " + e);
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

    public Mono<ICommand> findCommand(Guild guild, String name) {
        return guild == null ? Mono.justOrEmpty(commands.get(name)) : 
            Mono.just(guild)
        		.map(ctrl::getData)
        		.map(ControlData::getCommandBlacklist)
        		.filter(blacklist -> !blacklist.contains(name))
        		.flatMap(bl -> Mono.justOrEmpty(commands.get(name)));
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
        ClassLoader loader = getClass().getClassLoader();
        if (loader == null) {
            return; // ??
        }
        ClassPath classpath = ClassPath.from(loader);
		for (ClassInfo foo : classpath.getTopLevelClassesRecursive(packagename)) {
			if (!foo.getName().equals(getClass().getName())) {
				Class<?> c = foo.load();
				if (c.isAnnotationPresent(Command.class)) {
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
	        command.onRegister();
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
            c.init(DATA_FOLDER, gson);
        }
    }
    
    private void saveAll() {
        for (ICommand c : commands.values()) {
            c.save(DATA_FOLDER, gson);
        }
    }

	public void onShutdown() {
	    saveAll();
		for (ICommand c : commands.values()) {
		    c.onShutdown();
		}
	}
    
    public Iterable<ICommand> getCommands(@Nullable Guild guild) {
        if (guild == null) {
            return commands.values();
        }
        return commands.values().stream().filter(c -> !ctrl.getData(guild).getCommandBlacklist().contains(c.getName()))::iterator;
    }
}
