package com.blamejared.mcbot.zenscript;

import com.blamejared.mcbot.commands.api.Command;
import stanhebben.zenscript.*;
import stanhebben.zenscript.annotations.*;
import stanhebben.zenscript.compiler.TypeRegistry;
import stanhebben.zenscript.symbols.*;

import java.util.*;

@ZenClass("<root>")
public class ZenScript {
    public static final Map<String, IZenSymbol> globals = new HashMap<>();
    public static final TypeRegistry types = new TypeRegistry();
    public static final SymbolPackage root = new SymbolPackage("<root>");
    public static final IZenErrorLogger errors = new MyErrorLogger();
    public static final IZenCompileEnvironment environment = new MyCompileEnvironment();
    public static final Map<String, TypeExpansion> expansions = new HashMap<>();
    
    
    public static final Map<String, IZenScriptCommand> zenCommands = new HashMap<>();
    
    public ZenScript() {
        globals.put("sendMessage", Functions.getStaticFunction("sendMessage", String.class, String.class, String.class));
    }
    
    @ZenMethod
    public static void registerCommand(String name, IZenScriptCommand command){
        zenCommands.put(name, command);
    }
    
    
}
