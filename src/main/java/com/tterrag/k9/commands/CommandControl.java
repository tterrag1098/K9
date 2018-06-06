package com.tterrag.k9.commands;

import java.util.HashSet;
import java.util.Set;

import com.google.gson.reflect.TypeToken;
import com.tterrag.k9.commands.CommandControl.ControlData;
import com.tterrag.k9.commands.api.Argument;
import com.tterrag.k9.commands.api.CommandContext;
import com.tterrag.k9.commands.api.CommandException;
import com.tterrag.k9.commands.api.CommandPersisted;
import com.tterrag.k9.commands.api.Flag;
import com.tterrag.k9.util.Requirements;
import com.tterrag.k9.util.Requirements.RequiredType;

import discord4j.core.object.util.Permission;
import lombok.Value;

// This is created manually by CommandRegistrar, so no @Command
public class CommandControl extends CommandPersisted<ControlData> {

    @Value
    public static class ControlData {
        Set<String> commandBlacklist = new HashSet<>();
    }
    
    private static final Flag FLAG_COMMANDS = new SimpleFlag('c', "commands", "Control settings related to commands.", false);
    
    private static final Flag FLAG_BLACKLIST = new SimpleFlag('b', "blacklist", "Blacklist something (a command, etc.)", false);
    private static final Flag FLAG_WHITELIST = new SimpleFlag('w', "whitelist", "Whitelist something (a command, etc.)", false);
    
    private static final Argument<String> ARG_OBJECT = new WordArgument("object", "The object to configure, be it a command name or otherwise", true);

    public CommandControl() {
        super("ctrl", false, ControlData::new);
    }
    
    @Override
    protected TypeToken<ControlData> getDataType() {
        return TypeToken.get(ControlData.class);
    }

    @Override
    public void process(CommandContext ctx) throws CommandException {
        if (ctx.hasFlag(FLAG_COMMANDS)) {
            if (ctx.hasFlag(FLAG_WHITELIST) && ctx.hasFlag(FLAG_BLACKLIST)) {
                throw new CommandException("Illegal flag combination: Cannot whitelist and blacklist");
            }
            if (ctx.hasFlag(FLAG_WHITELIST)) {
                getData(ctx).subscribe(data -> data.getCommandBlacklist().remove(ctx.getArg(ARG_OBJECT)));
                ctx.reply("Whitelisted command.");
            } else if (ctx.hasFlag(FLAG_BLACKLIST)) {
                getData(ctx).subscribe(data -> data.getCommandBlacklist().add(ctx.getArg(ARG_OBJECT)));
                ctx.reply("Blacklisted command.");
            }
        }
    }
    
    @Override
    public String getDescription() {
        return "Control various aspects about the bot within this guild.";
    }
    
    @Override
    public Requirements requirements() {
        return Requirements.builder().with(Permission.MANAGE_GUILD, RequiredType.ALL_OF).build();
    }
}
