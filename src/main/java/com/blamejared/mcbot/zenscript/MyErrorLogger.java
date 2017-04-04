package com.blamejared.mcbot.zenscript;

import com.blamejared.mcbot.MCBot;
import stanhebben.zenscript.IZenErrorLogger;
import stanhebben.zenscript.util.ZenPosition;

public class MyErrorLogger implements IZenErrorLogger {
	
	@Override
	public void error(ZenPosition position, String message) {
	    MCBot.getChannel(MCBot.getGuild("Modders Corner"), "bot-log").sendMessage("Error in Script: " + position.getFile().getFileName() + " on Line: " + position.getLine() + ": at position: " + position.getLineOffset() + ": " + message);
	}
	
	@Override
	public void warning(ZenPosition position, String message) {
        MCBot.getChannel(MCBot.getGuild("Modders Corner"), "bot-log").sendMessage("Warning in Script: " + position.getFile().getFileName() + " on Line: " + position.getLine() + ": at position: " + position.getLineOffset() + ": " + message);
	}
}
