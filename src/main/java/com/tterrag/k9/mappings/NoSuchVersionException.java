package com.tterrag.k9.mappings;


public class NoSuchVersionException extends RuntimeException {
    private static final long serialVersionUID = 6490544564976171400L;
    
    public NoSuchVersionException(String message) {
        super(message);
    }

    @Override
    public String toString() {
        return "No such version: " + getMessage();
    }
}
