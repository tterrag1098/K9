package com.blamejared.mcbot.commands.api;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.blamejared.mcbot.listeners.ChannelListener;
import com.blamejared.mcbot.util.NonNull;
import com.blamejared.mcbot.util.Requirements;
import com.blamejared.mcbot.util.Threads;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.reflect.ClassPath;
import com.google.common.reflect.ClassPath.ClassInfo;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import lombok.SneakyThrows;
import sx.blah.discord.handle.obj.IMessage;

public enum CommandRegistrar {
	
	INSTANCE;
	
    @SuppressWarnings("null")
    @NonNull
	static final File DATA_FOLDER = Paths.get("command_data").toFile();
	static {
		DATA_FOLDER.mkdirs();
	}
	
	private Map<String, ICommand> commands = Maps.newTreeMap();
	private Timer autoSaveTimer = new Timer();
	
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

	private static final Pattern FLAG_PATTERN = Pattern.compile("--?(\\w+)(?:=(\\w+))?");

	public void invokeCommand(IMessage message) {
        List<String> split = Lists.newArrayList(Splitter.on(' ').omitEmptyStrings().split(message.getContent().substring(ChannelListener.PREFIX_CHAR.length())));
		ICommand command = findCommand(split.get(0));
		if (command == null) {
		    return;
		}
        // This is hardcoded BS but it's for potentially destructive actions like killing the bot, or wiping caches, so I think it's fine. Proper permission handling below.
		if (command.admin()) {
		    long id = message.getAuthor().getLongID();
		    if (!(
		               id == 140245257416736769L // tterrag
		            || id == 79179147875721216L  // Jared
		       )) {
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
		
		CommandContext ctx = new CommandContext(message);

		Map<Flag, String> flags = new HashMap<>();
		List<String> args = new ArrayList<>();
		
		split.remove(0);
		
		Map<String, Flag> keyToFlag = command.getFlags().stream().collect(Collectors.toMap(Flag::name, f -> f));
	    Map<String, Flag> longKeyToFlag = command.getFlags().stream().collect(Collectors.toMap(Flag::longFormName, f -> f));

		boolean doneFlags = false;
		Matcher matcher = FLAG_PATTERN.matcher("");
		for (String s : split) {
		    matcher.reset(s);
		    if (!doneFlags && matcher.matches()) {
		        String flagname = matcher.group(1);
		        Flag flag;
		        if (s.startsWith("--")) {
		            flag = longKeyToFlag.get(flagname);
		        } else if (s.startsWith("-")) {
	                flag = keyToFlag.get(flagname);
		        } else {
		            continue;
		        }
                if (flag == null) {
                    ctx.reply("Unknown flag \"" + flagname + "\".");
                    return;
                }
                String value = matcher.group(2);
                if (value != null && !flag.canHaveValue()) {
                    ctx.reply("Flag \"" + flagname + "\" does not support a value.");
                    return;
                } else if (value == null && flag.needsValue()) {
                    ctx.reply("Flag \"" + flagname + "\" requires a value.");
                    return;
                }

		        flags.put(flag, value == null ? flag.getDefaultValue() : value);
		    } else {
		        doneFlags = true;
                args.add(s);
            }
		}

        try {
            command.process(ctx.withFlags(flags).withArgs(args));
        } catch (CommandException e) {
            ctx.reply("Error processing command: " + e);
        } catch (RuntimeException e) {
            ctx.reply("Error processing command: " + e); // TODO should this be different?
            e.printStackTrace();
        }
    }

    public ICommand findCommand(String name) {
        return commands.get(name);
    }

    public void slurpCommands() {
        if (!finishedDefaultSlurp) {
            slurpCommands("com.blamejared.mcbot.commands");
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
    
    @SuppressWarnings("null")
    public void complete() {
        locked = true;
        gson = builder.create();
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
