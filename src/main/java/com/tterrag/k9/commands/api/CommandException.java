package com.tterrag.k9.commands.api;

import com.tterrag.k9.util.NonNull;
import com.tterrag.k9.util.PassthroughException;

@SuppressWarnings("serial")
public class CommandException extends PassthroughException {

    public CommandException(String message) {
        super(message);
    }
    
    public CommandException(@NonNull Throwable parent) {
        super(parent);
    }
}
