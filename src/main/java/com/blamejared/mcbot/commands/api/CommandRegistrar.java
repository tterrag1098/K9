package com.blamejared.mcbot.commands.api;

import java.util.List;
import java.util.Map;

import com.blamejared.mcbot.listeners.ChannelListener;
import com.blamejared.mcbot.util.Nonnull;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.reflect.ClassPath;
import com.google.common.reflect.ClassPath.ClassInfo;

import lombok.SneakyThrows;
import sx.blah.discord.handle.obj.IMessage;

public enum CommandRegistrar {
	
	INSTANCE;
	
	private Map<String, ICommand> commands = Maps.newHashMap();
	
	public void invokeCommand(IMessage message) {
        List<String> split = Lists.newArrayList(Splitter.on(' ').omitEmptyStrings().split(message.getContent().substring(ChannelListener.PREFIX_CHAR.length())));
		ICommand command = commands.get(split.get(0));
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
	    }
	    command.getChildren().forEach(this::registerCommand);
	}

    public void unregisterCommand(ICommand command) {
        commands.remove(command.getName());
    }
	
	public void onShutdown() {
	    for (ICommand command : commands.values()) {
	        command.onShutdown();
	    }
	}
}
