package com.blamejared.mcbot.commands;

import java.io.File;
import java.util.Collections;

import com.blamejared.mcbot.commands.api.Command;
import com.blamejared.mcbot.commands.api.CommandContext;
import com.blamejared.mcbot.commands.api.CommandException;
import com.blamejared.mcbot.commands.api.CommandPersisted;
import com.blamejared.mcbot.listeners.CommandListener;
import com.blamejared.mcbot.util.Requirements;
import com.blamejared.mcbot.util.Requirements.RequiredType;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import sx.blah.discord.handle.obj.Permissions;

@Command
public class CommandPrefix extends CommandPersisted<String> {
    
    private static final WordArgument ARG_PREFIX = new WordArgument("prefix", "The prefix to set. Leave out to reset to default.", false);
    
    public CommandPrefix() {
        super("prefix", false, Collections.emptyList(), Lists.newArrayList(ARG_PREFIX), () -> CommandListener.DEFAULT_PREFIX);
    }

    @Override
    protected TypeToken<String> getDataType() {
        return TypeToken.get(String.class);
    }
    
    @Override
    public void process(CommandContext ctx) throws CommandException {
        this.storage.put(ctx, ctx.getArgOrElse(ARG_PREFIX, CommandListener.DEFAULT_PREFIX));
        ctx.reply("Prefix for " + ctx.getGuild().getName() + " set to `" + storage.get(ctx) + "`.");
    }
    
    @Override
    public void init(File dataFolder, Gson gson) {
        super.init(dataFolder, gson);
        CommandListener.prefixes = this.storage;
    }
    
    @Override
    public String getDescription() {
        return "Set the bot's command prefix for this guild.";
    }
    
    @Override
    public Requirements requirements() {
        return Requirements.builder().with(Permissions.MANAGE_SERVER, RequiredType.ALL_OF).build();
    }
}
