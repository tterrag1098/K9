package com.tterrag.k9.commands.api;

import com.tterrag.k9.util.PassthroughException;

import reactor.util.annotation.NonNull;

@SuppressWarnings("serial")
public class CommandException extends PassthroughException {

    public CommandException(String message) {
        super(message);
    }
    
    public CommandException(@NonNull Throwable parent) {
        super(parent);
    }
    
    public CommandException(String message, @NonNull Throwable parent) {
        super(message, parent);
    }
}
