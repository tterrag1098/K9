package com.tterrag.k9.commands;

import com.tterrag.k9.K9;
import com.tterrag.k9.commands.api.Command;
import com.tterrag.k9.commands.api.CommandBase;
import com.tterrag.k9.commands.api.CommandContext;
import com.tterrag.k9.commands.api.CommandException;
import com.tterrag.k9.listeners.CommandListener;

import sx.blah.discord.api.internal.json.objects.EmbedObject;
import sx.blah.discord.util.EmbedBuilder;

@Command
public class CommandAbout extends CommandBase {
    
    public CommandAbout() {
        super("about", false);
    }

    @Override
    public void process(CommandContext ctx) throws CommandException {
        String ver = K9.getVersion();
        EmbedObject embed = new EmbedBuilder()
                .withTitle("K9 " + ver)
                .withUrl("http://tterrag.com/k9")
                .withThumbnail(K9.instance.getOurUser().getAvatarURL())
                .withDesc("A bot for looking up MCP names, and other useful things.\nFor more info, try `" + CommandListener.getPrefix(ctx.getGuild()) + "help`.")
                .appendField("Source", "https://github.com/tterrag1098/K9", false)
                .build();
        ctx.reply(embed);
    }

    @Override
    public String getDescription() {
        return "Provides info about the current version of the bot.";
    }

}
