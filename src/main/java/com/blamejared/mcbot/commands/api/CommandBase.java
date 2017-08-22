package com.blamejared.mcbot.commands.api;

import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.blamejared.mcbot.MCBot;
import com.blamejared.mcbot.util.NonNull;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import sx.blah.discord.api.internal.json.objects.EmbedObject;
import sx.blah.discord.handle.obj.IGuild;
import sx.blah.discord.handle.obj.IUser;

@RequiredArgsConstructor
@Getter
public abstract class CommandBase implements ICommand {
    
    @Getter
    @Accessors(fluent = true)
    @RequiredArgsConstructor
    @EqualsAndHashCode
    public static class SimpleFlag implements Flag {
        private final String name;
        private final boolean hasValue;
        
        @Override
        public String longFormName() {
            return name;
        }
    }

    private final @NonNull String name;
    @Accessors(fluent = true)
    private final boolean admin;
    
    private final Collection<Flag> flags;
    
    protected CommandBase(@NonNull String name, boolean admin) {
        this(name, admin, Collections.emptyList());
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
