package com.tterrag.k9.commands;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import com.google.common.base.Charsets;
import com.tterrag.k9.commands.api.Argument;
import com.tterrag.k9.commands.api.Command;
import com.tterrag.k9.commands.api.CommandBase;
import com.tterrag.k9.commands.api.CommandContext;
import com.tterrag.k9.commands.api.CommandException;
import com.tterrag.k9.commands.api.Flag;

@Command
public class CommandLMGTFY extends CommandBase {
    
    private static final Flag FLAG_IE = new SimpleFlag('e', "internet-explain", "Enable internet explainer mode.", false);
    
    private static final Argument<String> ARG_QUERY = new SentenceArgument("query", "The query to google.", true);

    public CommandLMGTFY() {
        super("lmgtfy", false);
    }
    
    @Override
    public void process(CommandContext ctx) throws CommandException {
        if (ctx.getArgs().size() < 1) {
            throw new CommandException("Not enough arguments.");
        }
        int iie = ctx.getFlags().containsKey(FLAG_IE) ? 1 : 0;
        StringBuilder url = new StringBuilder("<http://lmgtfy.com/?iie=").append(iie).append("&q=");
        String arg = ctx.getArg(ARG_QUERY);
        try {
            ctx.reply(url.append(URLEncoder.encode(ctx.sanitize(arg), Charsets.UTF_8.name())).append(">").toString());
        } catch (UnsupportedEncodingException e) {
            throw new CommandException(e);
        }
    }
    
    @Override
    public String getDescription() {
        return "Google something for someone.";
    }
}
