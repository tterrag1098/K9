package com.blamejared.mcbot.commands;

import java.util.List;

import com.blamejared.mcbot.commands.api.Command;
import com.blamejared.mcbot.commands.api.CommandBase;
import com.blamejared.mcbot.commands.api.CommandException;
import com.blamejared.mcbot.srg.ISrgMapping;
import com.blamejared.mcbot.srg.SrgDownloader;
import com.blamejared.mcbot.srg.ISrgMapping.MappingType;

import sx.blah.discord.handle.obj.IMessage;

@Command
public class CommandSRG extends CommandBase {

    public CommandSRG() {
        super("srg", false);
    }

    @Override
    public void process(IMessage message, List<String> flags, List<String> args) throws CommandException {
        if (args.size() < 1) {
            throw new CommandException("Not enough arguments.");
        }
        // TODO make this work for different types
        ISrgMapping mapping = SrgDownloader.INSTANCE.lookup(MappingType.FIELD, args.get(0), args.size() > 1 ? args.get(1) : "1.11");
        if(mapping == null && !validateMessage(args.get(0))){
            message.getChannel().sendMessage("Unable to send a message mentioning a user!");
            return;
        }
        message.getChannel().sendMessage(mapping == null ? "No mapping found for input: " + args.get(0) : mapping.toString());
    }

    @Override
    public String getUsage() {
        return "!srg [name] <mcver>";
    }
}
