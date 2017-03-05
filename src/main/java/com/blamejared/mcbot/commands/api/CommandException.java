package com.blamejared.mcbot.commands.api;

import com.blamejared.mcbot.util.Nonnull;
import com.blamejared.mcbot.util.PassthroughException;

@SuppressWarnings("serial")
public class CommandException extends PassthroughException {

    public CommandException(String message) {
        super(message);
    }
    
    public CommandException(@Nonnull Exception parent) {
        super(parent);
    }
}
