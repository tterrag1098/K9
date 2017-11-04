package com.blamejared.mcbot.commands;

import java.io.IOException;
import java.net.URL;

import org.apache.commons.io.IOUtils;

import com.blamejared.mcbot.commands.api.Command;
import com.blamejared.mcbot.commands.api.CommandBase;
import com.blamejared.mcbot.commands.api.CommandContext;
import com.blamejared.mcbot.commands.api.CommandException;
import com.google.common.base.Charsets;

@Command
public class CommandDrama extends CommandBase {

    public CommandDrama() {
        super("drama", false);
    }
    
    @Override
    public void process(CommandContext ctx) throws CommandException {
        try {
            String drama = IOUtils.readLines(new URL("http://ftb-drama.herokuapp.com/txt").openStream(), Charsets.UTF_8).get(0);
            ctx.replyBuffered(drama);
        } catch (IOException e) {
            throw new CommandException(e);
        }
    }
    
    @Override
    public String getDescription() {
        return "Creates some drama.";
    }
}
