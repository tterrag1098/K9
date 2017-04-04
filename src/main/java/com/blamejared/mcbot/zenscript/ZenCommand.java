package com.blamejared.mcbot.zenscript;

public class ZenCommand {
    
    private final IZenScriptCommand command;
    public ZenCommand(IZenScriptCommand command) {
        this.command = command;
    }
    
    public IZenScriptCommand getCommand() {
        return command;
    }
}
