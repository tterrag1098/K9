package com.blamejared.mcbot.mcp;


public class NoSuchVersionException extends Exception {
    private static final long serialVersionUID = 6490544564976171400L;
    
    public NoSuchVersionException(String message) {
        super(message);
    }

    @Override
    public String toString() {
        return "No such version: " + getMessage();
    }
}
