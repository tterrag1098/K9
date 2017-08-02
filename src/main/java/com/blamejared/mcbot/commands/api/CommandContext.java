package com.blamejared.mcbot.commands.api;

import java.util.List;
import java.util.Map;

import com.blamejared.mcbot.util.Nullable;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import sx.blah.discord.handle.obj.IMessage;

@RequiredArgsConstructor
@Getter
public class CommandContext {
    
    private final IMessage message;
    private final Map<Flag, String> flags;
    private final List<String> args;
    
    public boolean hasFlag(Flag flag) {
        return getFlags().containsKey(flag);
    }
    
    public @Nullable String getFlag(Flag flag) {
        return getFlags().get(flag);
    }
    
    public int argCount() {
        return getArgs().size();
    }
    
    public String getArg(int idx) {
        return getArgs().get(idx);
    }
}
