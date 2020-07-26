package com.tterrag.k9.commands.api;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.tterrag.k9.K9;
import com.tterrag.k9.util.NullHelper;
import com.tterrag.k9.util.Requirements;

import discord4j.core.GatewayDiscordClient;
import reactor.core.publisher.Mono;

public interface ICommand {

	String getName();
	
	boolean admin();
	
	Mono<?> process(CommandContext ctx);
	
	Collection<Flag> getFlags();
	
	List<Argument<?>> getArguments();
	
	String getDescription(CommandContext ctx);
	
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
    default Iterable<ICommand> getChildren() {
	    return NullHelper.notnullJ(Collections.emptyList(), "Collections#emptyList");
	}
	
	/* == Event Hooks == */

    default void onRegister(K9 k9) {}
    
    default Mono<?> onReady(ReadyContext ctx) { return Mono.empty(); }
    
    default void save(File dataFolder, Gson gson) {}
    
    default void onUnregister() {}
    
    default void onShutdown() {}

}
