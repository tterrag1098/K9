package com.blamejared.mcbot.commands.api;

import java.io.File;
import java.util.Collections;
import java.util.List;

import sx.blah.discord.handle.obj.IMessage;

import com.blamejared.mcbot.util.DefaultNonNull;
import com.google.gson.Gson;

@DefaultNonNull
public interface ICommand {
	
	String getName();
	
	boolean admin();
	
	void process(IMessage message, List<String> flags, List<String> args) throws CommandException;
	
	String getUsage();
	
	default void readJson(File dataFolder, Gson gson) {}
	
	default void writeJson(File dataFolder, Gson gson) {}
	
	/**
	 * Use this if this command is only a proxy to register children commands.
	 */
	default boolean isTransient() {
	    return false;
	}
	
	/**
	 * A set of commands to be registered at the time this command is registered. Use this for special constructors.
	 */
	default Iterable<ICommand> getChildren() {
	    return Collections.emptyList();
	}

    default void onShutdown() {}

}
