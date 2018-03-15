package com.tterrag.k9.mcp;


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
