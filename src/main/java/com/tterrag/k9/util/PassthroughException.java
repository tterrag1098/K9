package com.tterrag.k9.util;

import lombok.experimental.Delegate;

@SuppressWarnings("serial")
public class PassthroughException extends Exception {
    
    private interface Excludes {
        Throwable fillInStackTrace();
        
        void addSuppressed(Throwable exception);
        
        Throwable[] getSuppressed();
    }

    @Delegate(excludes = Excludes.class)
    private final Throwable parent;

    public PassthroughException(String message) {
        this(new Exception(message));
    }
    
    public PassthroughException(String message, Throwable parent) {
        this(new RuntimeException(message, parent));
    }

    public PassthroughException(Throwable parent) {
        this.parent = parent;
    }
 
    @Override
    public String toString() {
        String s = parent.getClass().getName();
        String message = getLocalizedMessage();
        if (parent.getClass() == Exception.class && message != null) {
            return message;
        }
        return (message != null) ? (s + ": " + message) : s;
    }
}
