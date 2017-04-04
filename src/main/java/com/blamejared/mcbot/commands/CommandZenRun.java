package com.blamejared.mcbot.commands;

import com.blamejared.mcbot.commands.api.*;
import com.blamejared.mcbot.zenscript.MyCompileEnvironment;
import stanhebben.zenscript.ZenModule;
import sx.blah.discord.handle.obj.IMessage;

import java.io.*;
import java.nio.file.Paths;
import java.util.*;

@Command
public class CommandZenRun extends CommandBase {
    
    public CommandZenRun() {
        super("zen", true);
    }
    
    @Override
    public void process(IMessage message, List<String> flags, List<String> args) throws CommandException {
        if(args.size() < 1) {
            throw new CommandException("Not enough arguments.");
        }
        File zen = Paths.get("data", "zenscript", args.get(0)).toFile();
        if(!zen.exists()) {
            zen.mkdirs();
        }
       
        try {
            File f = new File(zen, "command.zs");
            System.out.println(f.getName());
            ZenModule module = ZenModule.compileScriptFile(f, new MyCompileEnvironment(), ClassLoader.getSystemClassLoader());
            module.getMain().run();
        } catch(IOException e) {
            e.printStackTrace();
        }
    }
    
    public String getUsage() {
        return "<name>, function(guild, channel, author)";
    }
}
