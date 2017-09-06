package com.blamejared.mcbot.commands;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import com.blamejared.mcbot.commands.api.Command;
import com.blamejared.mcbot.commands.api.CommandBase;
import com.blamejared.mcbot.commands.api.CommandContext;
import com.blamejared.mcbot.commands.api.CommandException;
import com.blamejared.mcbot.commands.api.Flag;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

@Command
public class CommandLMGTFY extends CommandBase {
    
    private static final Flag FLAG_IE = new SimpleFlag("ie", false);

    public CommandLMGTFY() {
        super("lmgtfy", false, Lists.newArrayList(FLAG_IE));
    }
    
    @Override
    public void process(CommandContext ctx) throws CommandException {
        if (ctx.getArgs().size() < 1) {
            throw new CommandException("Not enough arguments.");
        }
        int iie = ctx.getFlags().containsKey(FLAG_IE) ? 1 : 0;
        StringBuilder url = new StringBuilder("<http://lmgtfy.com/?iie=").append(iie).append("&q=");
        String arg = Joiner.on(' ').join(ctx.getArgs());
        try {
            ctx.reply(url.append(URLEncoder.encode(ctx.sanitize(arg), Charsets.UTF_8.name())).append(">").toString());
        } catch (UnsupportedEncodingException e) {
            throw new CommandException(e);
        }
    }
    
    public String getUsage() {
        return "<question>";
    }
}
