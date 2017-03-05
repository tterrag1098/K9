package com.blamejared.mcbot.commands.api;

import java.util.Collections;
import java.util.List;

import com.blamejared.mcbot.util.DefaultNonNull;

import sx.blah.discord.handle.obj.IMessage;

@DefaultNonNull
public interface ICommand {
	
	String getName();
	
	boolean admin();
	
	void process(IMessage message, List<String> flags, List<String> args) throws CommandException;
	
	String getUsage();
	
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
