package com.blamejared.mcbot.commands.api;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import lombok.SneakyThrows;
import sx.blah.discord.handle.obj.IMessage;

import com.blamejared.mcbot.listeners.ChannelListener;
import com.blamejared.mcbot.util.Nonnull;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.reflect.ClassPath;
import com.google.common.reflect.ClassPath.ClassInfo;
import com.google.gson.Gson;

public enum CommandRegistrar {
	
	INSTANCE;
	
	private static final File DATA_FOLDER = Paths.get("command_data").toFile();
	static {
		DATA_FOLDER.mkdirs();
	}

	private static final Gson GSON = new Gson();
	
	private Map<String, ICommand> commands = Maps.newTreeMap();
	private Timer autoSaveTimer = new Timer();
	
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
		    }
		}
	}
	
	public ICommand findCommand(String name) {
	    return commands.get(name);
	}
	
	public void slurpCommands() {
		slurpCommands("com.blamejared.mcbot.commands");
	}

	@SneakyThrows
	public void slurpCommands(@Nonnull String packagename) {
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
	    if (!command.isTransient()) {
	        commands.put(command.getName(), command);
	        command.readJson(DATA_FOLDER, GSON);
	    }
	    command.getChildren().forEach(this::registerCommand);
	}

    public void unregisterCommand(ICommand command) {
        commands.remove(command.getName());
    }
    
    public void saveAll() {
    	System.out.println("Saving command data...");
		commands.values().forEach(c -> c.writeJson(DATA_FOLDER, GSON));
    }
	
	public void onShutdown() {
		saveAll();
		commands.values().forEach(ICommand::onShutdown);
	}
    
    public Map<String, ICommand> getCommands() {
        return commands;
    }
}
