package com.blamejared.mcbot.commands.api;

import com.blamejared.mcbot.util.Nonnull;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

@RequiredArgsConstructor
@Getter
public abstract class CommandBase implements ICommand {

    private final @Nonnull String name;
    @Accessors(fluent = true)
    private final boolean admin;

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
