package com.blamejared.mcbot.commands.api;

import com.blamejared.mcbot.util.Nonnull;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import sx.blah.discord.api.internal.json.objects.EmbedObject;
import sx.blah.discord.handle.obj.IChannel;

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
    
    public boolean validateMessage(Object message){
        if(message instanceof String){
            if(((String)message).contains("@")){
                return false;
            }
        } else if (message instanceof EmbedObject){
            if(((EmbedObject)message).description.contains("@")) {
                return false;
            }
        }
        return true;
        
    }
}
