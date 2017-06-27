package com.blamejared.mcbot.util;

import java.io.PrintStream;
import java.io.PrintWriter;

@SuppressWarnings("serial")
public class PassthroughException extends Exception {

    private final Exception parent;

    public PassthroughException(String message) {
        this(new Exception(message));
    }

    public PassthroughException(@NonNull Exception parent) {
        this.parent = parent;
    }
    
    // Hopefully these are enough

    @Override
    public String getMessage() {
        return parent.getMessage();
    }

    @Override
    public String toString() {
        return parent.getClass() == Exception.class ? getMessage() : parent.toString();
    }

    @Override
    public synchronized Throwable getCause() {
        return parent.getCause();
    }

    @Override
    public void printStackTrace(PrintWriter arg0) {
        parent.printStackTrace(arg0);
    }

    @Override
    public void printStackTrace(PrintStream arg0) {
        parent.printStackTrace(arg0);
    }
}
