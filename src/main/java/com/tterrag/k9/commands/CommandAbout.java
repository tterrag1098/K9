package com.tterrag.k9.commands;

import com.tterrag.k9.K9;
import com.tterrag.k9.commands.api.Command;
import com.tterrag.k9.commands.api.CommandBase;
import com.tterrag.k9.commands.api.CommandContext;
import com.tterrag.k9.commands.api.CommandException;
import com.tterrag.k9.listeners.CommandListener;

import discord4j.core.spec.EmbedCreateSpec;
import reactor.core.publisher.Mono;

@Command
public class CommandAbout extends CommandBase {
    
    public CommandAbout() {
        super("about", false);
    }

    @Override
    public void process(CommandContext ctx) throws CommandException {
        String ver = K9.getVersion();
        Mono.just(new EmbedCreateSpec())
        	.zipWhen(e -> K9.instance.getSelf(), (embed, user) -> embed.setThumbnail("https://cdn.discordapp.com/avatars/" + user.getId().asLong() + "/" + user.getAvatarHash().orElse("missingno") + ".png"))
    		.zipWhen(e -> ctx.getGuild(), (embed, guild) -> embed.setDescription("A bot for looking up MCP names, and other useful things.\nFor more info, try `" + CommandListener.getPrefix(guild) + "help`."))
    		.map(embed -> embed
                .setTitle("K9 " + ver)
                .setUrl("http://tterrag.com/k9")
                .addField("Source", "https://github.com/tterrag1098/K9", false)                    
    		).subscribe(ctx::replyFinal);
    }

    @Override
    public String getDescription() {
        return "Provides info about the current version of the bot.";
    }

}
