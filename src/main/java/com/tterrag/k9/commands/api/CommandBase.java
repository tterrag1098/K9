package com.tterrag.k9.commands.api;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;
import reactor.util.annotation.NonNull;

@Getter
public abstract class CommandBase implements ICommand {

    @RequiredArgsConstructor
    @Getter
    @Accessors(fluent = true)
    @ToString
    @EqualsAndHashCode
    public static class SimpleFlag implements Flag {

        private final char name;
        private final String longName;
        private final String description;
        private final boolean hasValue;
        private final String defaultValue;

        public SimpleFlag(char name, String desc, boolean hasValue) {
            this(name, desc, hasValue, null);
        }
        
        public SimpleFlag(char name, String desc, boolean hasValue, String defaultValue) {
            this(name, Character.toString(name), desc, hasValue, defaultValue);
        }
        
        public SimpleFlag(char name, String longName, String desc, boolean hasValue) {
            this(name, longName, desc, hasValue, null);
        }

        @Override
        public String longFormName() {
            return longName;
        }

        @Override
        public boolean needsValue() {
            return hasValue && defaultValue == null;
        }

        @Override
        public boolean canHaveValue() {
            return hasValue;
        }
    }

    @RequiredArgsConstructor
    @Getter
    @Accessors(fluent = true)
    @EqualsAndHashCode
    @ToString
    public static abstract class SimpleArgument<T> implements Argument<T> {
        
        private final String name;
        private final String description;
        private final boolean required;
        
        @Override
        public boolean required(Collection<Flag> flags) {
            return required();
        }
    }
    
    public static class SentenceArgument extends SimpleArgument<String> {

        public SentenceArgument(String name, String description, boolean required) {
            super(name, description, required);
        }

        @Override
        public String parse(String input) {
            return input;
        }
    }
    
    public static class WordArgument extends SimpleArgument<String> {

        public WordArgument(String name, String description, boolean required) {
            super(name, description, required);
        }

        @Override
        public Pattern pattern() {
            return MATCH_WORD;
        }
        
        @Override
        public String parse(String input) {
            return input;
        }
    }
    
    public static class IntegerArgument extends SimpleArgument<Integer> {
        
        private static final Pattern MATCH_INT = Pattern.compile("[-+]?\\d+\\b");

        public IntegerArgument(String name, String description, boolean required) {
            super(name, description, required);
        }
        
        @Override
        public Pattern pattern() {
            return MATCH_INT;
        }
        
        @Override
        public Integer parse(String input) {
            // Avoid autobox
            return new Integer(input);
        }
    }
    
    public static class DecimalArgument extends SimpleArgument<Double> {

        private static final Pattern MATCH_DOUBLE = Pattern.compile("[-+]?\\d+(\\.\\d+)?\\b");

        public DecimalArgument(String name, String description, boolean required) {
            super(name, description, required);
        }
        
        @Override
        public Pattern pattern() {
            return MATCH_DOUBLE;
        }
        
        @Override
        public Double parse(String input) {
            // Avoid autobox
            return new Double(input);
        }
    }

    private final @NonNull String name;
    @Accessors(fluent = true)
    private final boolean admin;
    
    private final Collection<Flag> flags = new ArrayList<>();
    
    private final List<Argument<?>> arguments = new ArrayList<>();
    
    protected CommandBase(@NonNull String name, boolean admin) {
        this.name = name;
        this.admin = admin;
        
        Class<?> clazz = getClass();
        while (clazz != CommandBase.class) {
            for (Field f : clazz.getDeclaredFields()) {
                f.setAccessible(true);
                Object val;
                try {
                    if ((f.getModifiers() & Modifier.STATIC) > 0) {
                        val = f.get(null);
                    } else {
                        val = f.get(this);
                    }
                    if (val instanceof Flag) {
                        flags.add((Flag) val);
                    } else if (val instanceof Argument) {
                        arguments.add((Argument<?>) val);
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            clazz = clazz.getSuperclass();
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + getName().hashCode();
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        
        CommandBase other = (CommandBase) obj;
        return getName().equals(other.getName());
    }
}
