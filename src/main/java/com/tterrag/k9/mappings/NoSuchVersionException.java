package com.tterrag.k9.mappings;


public class NoSuchVersionException extends RuntimeException {
    private static final long serialVersionUID = 6490544564976171400L;
    private final String type;

    public NoSuchVersionException(String type, String message) {
        super(message);
        this.type = type;
    }

    @Override
    public String toString() {
        return "No such " + type + " version: " + getMessage();
    }
}
