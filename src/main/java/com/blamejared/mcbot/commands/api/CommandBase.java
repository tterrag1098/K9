package com.blamejared.mcbot.commands.api;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import com.blamejared.mcbot.util.NonNull;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;

@RequiredArgsConstructor
@Getter
public abstract class CommandBase implements ICommand {

    @RequiredArgsConstructor
    @Getter
    @Accessors(fluent = true)
    @ToString
    @EqualsAndHashCode
    public static class SimpleFlag implements Flag {

        private final String name;
        private final String description;
        private final boolean hasValue;
        private final String defaultValue;

        public SimpleFlag(String name, String desc, boolean hasValue) {
            this(name, desc, hasValue, null);
        }

        @Override
        public String longFormName() {
            return name;
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
    
    private final Collection<Flag> flags;
    
    private final List<Argument<?>> arguments; // Requires list due to ordered nature
    
    protected CommandBase(@NonNull String name, boolean admin) {
        this(name, admin, Collections.emptyList(), Collections.emptyList());
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
