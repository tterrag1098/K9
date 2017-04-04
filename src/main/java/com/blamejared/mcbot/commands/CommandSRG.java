package com.blamejared.mcbot.commands;

import java.util.List;

import com.blamejared.mcbot.commands.api.Command;
import com.blamejared.mcbot.commands.api.CommandBase;
import com.blamejared.mcbot.commands.api.CommandException;
import com.blamejared.mcbot.mcp.DataDownloader;
import com.blamejared.mcbot.mcp.ISrgMapping;
import com.blamejared.mcbot.mcp.ISrgMapping.MappingType;
import com.google.common.base.Joiner;

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
        List<ISrgMapping> mappings = DataDownloader.INSTANCE.lookupSRG(MappingType.FIELD, args.get(0), args.size() > 1 ? args.get(1) : "1.11");
        if (mappings.isEmpty()) {
            message.getChannel().sendMessage("No mapping found.");
        } else {
            message.getChannel().sendMessage(Joiner.on('\n').join(mappings));
        }
    }

    @Override
    public String getUsage() {
        return "<name> [mcver]";
    }
}
