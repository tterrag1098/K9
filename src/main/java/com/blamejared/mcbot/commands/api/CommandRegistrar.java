package com.blamejared.mcbot.commands.api;

import java.io.File;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import com.blamejared.mcbot.listeners.ChannelListener;
import com.blamejared.mcbot.util.Nonnull;
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
	
	static final File DATA_FOLDER = Paths.get("command_data").toFile();
	static {
		DATA_FOLDER.mkdirs();
	}
	
	private Map<String, ICommand> commands = Maps.newTreeMap();
	private Timer autoSaveTimer = new Timer();
	
	private final GsonBuilder builder = new GsonBuilder();
	private Gson gson;
	
	private boolean finishedDefaultSlurp;
	private boolean locked;
	
	private CommandRegistrar() {
		autoSaveTimer.scheduleAtFixedRate(new TimerTask() {
			
			@Override
			public void run() {
				INSTANCE.saveAll();
			}
		}, TimeUnit.SECONDS.toMillis(30), TimeUnit.SECONDS.toMillis(5));
	}

	public void invokeCommand(IMessage message) {
        List<String> split = Lists.newArrayList(Splitter.on(' ').omitEmptyStrings().split(message.getContent().substring(ChannelListener.PREFIX_CHAR.length())));
		ICommand command = findCommand(split.get(0));
		List<String> flags = Lists.newArrayList();
		List<String> args = Lists.newArrayList();
		
		split.remove(0);
		
		boolean doneFlags = false;
		for (String s : split) {
		    if (!doneFlags && s.startsWith("-")) {
		        flags.add(s.substring(1));
		    } else {
		        doneFlags = true;
                args.add(s);
            }
        }
        if (command != null) {
            try {
                command.process(message, flags, args);
            } catch (CommandException e) {
                message.getChannel().sendMessage("Error processing command: " + e);
		    } catch (RuntimeException e) {
		        message.getChannel().sendMessage("Error processing command: " + e); // TODO should this be different?
		    }
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
    public void slurpCommands(@Nonnull String packagename) {
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
