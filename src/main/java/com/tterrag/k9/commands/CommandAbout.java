package com.tterrag.k9.commands;

import com.tterrag.k9.K9;
import com.tterrag.k9.commands.api.Command;
import com.tterrag.k9.commands.api.CommandBase;
import com.tterrag.k9.commands.api.CommandContext;
import com.tterrag.k9.commands.api.CommandException;
import com.tterrag.k9.listeners.CommandListener;

import reactor.core.publisher.Mono;

@Command
public class CommandAbout extends CommandBase {
    
    public CommandAbout() {
        super("about", false);
    }

    @Override
    public Mono<?> process(CommandContext ctx) throws CommandException {
        String ver = K9.getVersion();
        return K9.instance.getSelf()
            .flatMap(u -> ctx.reply(spec ->
                spec.setThumbnail("https://cdn.discordapp.com/avatars/" + u.getAvatarUrl())
                    .setDescription("A bot for looking up MCP names, and other useful things.\nFor more info, try `" + CommandListener.getPrefix(ctx.getGuildId()) + "help`.")
                    .setTitle("K9 " + ver)
                    .setUrl("http://tterrag.com/k9")
                    .addField("Source", "https://github.com/tterrag1098/K9", false)
            ));
    }

    @Override
    public String getDescription() {
        return "Provides info about the current version of the bot.";
    }

}
