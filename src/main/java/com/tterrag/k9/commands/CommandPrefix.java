package com.tterrag.k9.commands;

import java.io.File;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tterrag.k9.commands.api.CommandContext;
import com.tterrag.k9.commands.api.CommandException;
import com.tterrag.k9.commands.api.CommandPersisted;
import com.tterrag.k9.listeners.CommandListener;
import com.tterrag.k9.util.Requirements;
import com.tterrag.k9.util.Requirements.RequiredType;

import discord4j.core.object.util.Permission;


public class CommandPrefix extends CommandPersisted<String> {
    
    private static final WordArgument ARG_PREFIX = new WordArgument("prefix", "The prefix to set. Leave out to reset to default.", false);
    
    public CommandPrefix() {
        super("prefix", false, () -> CommandListener.DEFAULT_PREFIX);
    }

    @Override
    protected TypeToken<String> getDataType() {
        return TypeToken.get(String.class);
    }
    
    @Override
    public void process(CommandContext ctx) throws CommandException {
        ctx.getGuild()
            .subscribe(guild -> {
                this.storage.put(ctx, ctx.getArgOrElse(ARG_PREFIX, CommandListener.DEFAULT_PREFIX));
                ctx.replyFinal("Prefix for " + guild.getName() + " set to `" + storage.get(ctx) + "`.");
            });
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
        return Requirements.builder().with(Permission.MANAGE_GUILD, RequiredType.ALL_OF).build();
    }
}
