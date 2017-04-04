package com.blamejared.mcbot.commands;

import com.blamejared.mcbot.commands.api.*;
import com.blamejared.mcbot.zenscript.*;
import sx.blah.discord.handle.obj.IMessage;

import java.io.*;
import java.nio.file.Paths;
import java.util.List;

@Command
public class CommandZenAdd extends CommandBase {
    
    public CommandZenAdd() {
        super("zenadd", true);
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
            FileWriter writer  = new FileWriter(new File(zen, "command.zs"));
            writer.write(message.getContent().substring(this.getName().length()).substring(message.getContent().split(" ")[0].length()));
            writer.close();
        } catch(IOException e) {
            e.printStackTrace();
        }
    }
    
    public String getUsage() {
        return "<name>, function(guild, channel, author)";
    }
}
