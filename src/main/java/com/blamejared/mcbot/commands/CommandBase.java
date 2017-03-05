package com.blamejared.mcbot.commands;

import com.blamejared.mcbot.MCBot;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.util.*;

import java.util.List;


public abstract class CommandBase{
	
	private final String name;
	
	public CommandBase(String name) {
		this.name = name;
		MCBot.commands.put(name, this);
	}
	
	public String getName() {
		return name;
	}
	
	public abstract void exectute(IMessage message) throws RateLimitException, DiscordException, MissingPermissionsException;
	
	public abstract String getUsage();
}
