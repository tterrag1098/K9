package com.tterrag.k9.commands.api;

import java.io.File;
import java.nio.file.Paths;
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
import com.tterrag.k9.util.NonNull;
import com.tterrag.k9.util.NullHelper;
import com.tterrag.k9.util.Requirements;
import com.tterrag.k9.util.Threads;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.handle.obj.IUser;
import sx.blah.discord.util.RequestBuffer;

@Slf4j
public enum CommandRegistrar {
	
	INSTANCE;
	
    @NonNull
	static final File DATA_FOLDER = NullHelper.notnullJ(Paths.get("command_data").toFile(), "Path#toFile");
	static {
		DATA_FOLDER.mkdirs();
	}
	
	private final Map<String, ICommand> commands = Maps.newTreeMap();
	private final Timer autoSaveTimer = new Timer();
	
	private final @NonNull GsonBuilder builder = new GsonBuilder();
	private @NonNull Gson gson = new Gson();;
	
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

	private static final Pattern FLAG_PATTERN = Pattern.compile("^(--?)(\\w+)(?:[=\\s](?:\"(.*?)\"|(\\S+)))?");

	public void invokeCommand(IMessage message, String name, String argstr) {
		ICommand command = findCommand(name);
		if (command == null) {
		    return;
		}
        // This is hardcoded BS but it's for potentially destructive actions like killing the bot, or wiping caches, so I think it's fine. Proper permission handling below.
		if (command.admin()) {
		    if (!isAdmin(message.getAuthor())) {
		        return;
		    }
		}
		
		Requirements req = command.requirements();
		if (!req.matches(message.getAuthor(), message.getGuild())) {
		    IMessage msg = message.getChannel().sendMessage("You do not have permission to use this command!");
		    Threads.sleep(5000);
		    msg.delete();
		    return;
		}
		
		argstr = Strings.nullToEmpty(argstr);
		
		CommandContext ctx = new CommandContext(message);

		Map<Flag, String> flags = new HashMap<>();
		Map<Argument<?>, String> args = new HashMap<>();
		Map<Flag, Matcher> matchers = new HashMap<>();

		
		Map<Character, Flag> keyToFlag = command.getFlags().stream().collect(Collectors.toMap(Flag::name, f -> f));
	    Map<String, Flag> longKeyToFlag = command.getFlags().stream().collect(Collectors.toMap(Flag::longFormName, f -> f));

		Matcher matcher = FLAG_PATTERN.matcher(argstr);
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
                ctx.reply("Unknown flag \"" + flagname + "\".");
                return;
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
                    ctx.reply("Flag \"" + flag.longFormName() + "\" requires a value.");
                    return;
                }

                flags.put(flag, value == null ? flag.getDefaultValue() : value);
                matchers.put(flag, FLAG_PATTERN.matcher(argstr));
            }
            toreplace = Pattern.quote(toreplace) + "\\s*";
            argstr = argstr.replaceFirst(toreplace, "").trim();
            matcher.reset(argstr);
        }

        for (Argument<?> arg : command.getArguments()) {
            boolean required = arg.required(flags.keySet());
            if (required && argstr.isEmpty()) {
                long count = command.getArguments().stream().filter(a -> a.required(flags.keySet())).count();
                ctx.reply("This command requires at least " + count + " argument" + (count > 1 ? "s" : "") + ".");
                return;
            }
            
            matcher = arg.pattern().matcher(argstr);
            
            if (matcher.find()) {
                String match = matcher.group();
                argstr = argstr.replaceFirst(Pattern.quote(match) + "\\s*", "").trim();
                args.put(arg, match);
            } else if (required) {
                ctx.reply("Argument " + arg.name() + " does not accept input: " + argstr);
                return;
            }
        }

        try {
            command.process(ctx.withFlags(flags).withArgs(args).withMatchers(matchers));
        } catch (CommandException e) {
            RequestBuffer.request(() -> ctx.reply("Could not process command: " + e));
        } catch (RuntimeException e) {
            RequestBuffer.request(() -> ctx.reply("Unexpected error processing command: " + e)); // TODO should this be different?
            log.error("Exception invoking command: ", e);
        }
    }
	
	public static boolean isAdmin(IUser user) {
	    return isAdmin(user.getLongID());
	}

	public static boolean isAdmin(long id) {
	    return id == 140245257416736769L; // tterrag
	}

    public ICommand findCommand(String name) {
        return commands.get(name);
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
    
    public Map<String, ICommand> getCommands() {
        return commands;
    }
}
