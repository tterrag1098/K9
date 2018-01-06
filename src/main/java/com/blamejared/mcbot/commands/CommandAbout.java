package com.blamejared.mcbot.commands;

import com.blamejared.mcbot.MCBot;
import com.blamejared.mcbot.commands.api.Command;
import com.blamejared.mcbot.commands.api.CommandBase;
import com.blamejared.mcbot.commands.api.CommandContext;
import com.blamejared.mcbot.commands.api.CommandException;
import com.blamejared.mcbot.listeners.CommandListener;

import sx.blah.discord.api.internal.json.objects.EmbedObject;
import sx.blah.discord.util.EmbedBuilder;

@Command
public class CommandAbout extends CommandBase {
    
    public CommandAbout() {
        super("about", false);
    }

    @Override
    public void process(CommandContext ctx) throws CommandException {
        String ver = MCBot.getVersion();
        if (ver == null) {
            ver = "DEV";
        }
        EmbedObject embed = new EmbedBuilder()
                .withTitle("K9 " + ver)
                .withThumbnail(MCBot.instance.getOurUser().getAvatarURL())
                .withDesc("A bot for looking up MCP names, and other useful things.\nFor more info, try `" + CommandListener.getPrefix(ctx.getGuild()) + "help`.")
                .appendField("Source", "https://github.com/tterrag1098/MCBot", false)
                .build();
        ctx.reply(embed);
    }

    @Override
    public String getDescription() {
        return "Provides info about the current version of the bot.";
    }

}
