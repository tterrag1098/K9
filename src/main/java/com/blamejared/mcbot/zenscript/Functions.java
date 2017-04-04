package com.blamejared.mcbot.zenscript;

import com.blamejared.mcbot.MCBot;
import stanhebben.zenscript.symbols.*;
import stanhebben.zenscript.type.natives.*;

import static com.blamejared.mcbot.zenscript.ZenScript.types;

public class Functions {
    
    public static void sendMessage(String guild, String channel, String message) {
        MCBot.getChannel(MCBot.getGuild(guild), channel).sendMessage(message);
    }
    
    public static IZenSymbol getStaticFunction(String name, Class... arguments) {
        IJavaMethod method = JavaMethod.get(types, Functions.class, name, arguments);
        if(method == null)
            return null;
        return new SymbolJavaStaticMethod(method);
    }
    
    public static IZenSymbol getFunction(Class clazz, String name, Class... arguments) {
        IJavaMethod method = JavaMethod.get(types, clazz, name, arguments);
        if(method == null)
            return null;
        return new SymbolJavaStaticMethod(method);
    }
}
