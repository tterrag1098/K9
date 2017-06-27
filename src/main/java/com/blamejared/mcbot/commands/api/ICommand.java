package com.blamejared.mcbot.commands.api;

import java.io.File;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;

import com.blamejared.mcbot.util.DefaultNonNull;
import com.blamejared.mcbot.util.Requirements;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import sx.blah.discord.handle.obj.IMessage;

@DefaultNonNull
public interface ICommand {

	String getName();
	
	boolean admin();
	
	void process(IMessage message, List<String> flags, List<String> args) throws CommandException;
	
	String getUsage();
	
	/**
	 * @return A map of permissions to their requirement type. Recommended to use {@link EnumMap} for performance.
	 */
    default Requirements requirements() {
	    return Requirements.none();
	}
	
	default void gatherParsers(GsonBuilder builder) {}

	/**
	 * Use this if this command is only a proxy to register children commands.
	 */
	default boolean isTransient() {
	    return false;
	}
	
	/**
	 * A set of commands to be registered at the time this command is registered. Use this for special constructors.
	 */
	@SuppressWarnings("null")
    default Iterable<ICommand> getChildren() {
	    return Collections.emptyList();
	}
	
	/* == Event Hooks == */

    default void onRegister() {}
    
    default void init(File dataFolder, Gson gson) {}
    
    default void save(File dataFolder, Gson gson) {}
    
    default void onUnregister() {}
    
    default void onShutdown() {}

}
