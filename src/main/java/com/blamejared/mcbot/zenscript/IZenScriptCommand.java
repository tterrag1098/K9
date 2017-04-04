package com.blamejared.mcbot.zenscript;

public interface IZenScriptCommand {

    
    void process(String guild, String channel, String author, String message);
    
}
