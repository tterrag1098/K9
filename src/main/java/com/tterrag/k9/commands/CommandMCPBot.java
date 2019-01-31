package com.tterrag.k9.commands;

import com.tterrag.k9.commands.api.Argument;
import com.tterrag.k9.commands.api.Command;
import com.tterrag.k9.commands.api.CommandBase;
import com.tterrag.k9.commands.api.CommandContext;
import com.tterrag.k9.commands.api.CommandException;
import com.tterrag.k9.irc.IRC;
import com.tterrag.k9.util.Patterns;

public class CommandMCPBot extends CommandBase {
    
    private static final Argument<String> ARG_CONTENT = new SentenceArgument("content", "The exact content to send to MCPBot, including the command", true);

    public CommandMCPBot() {
        super("mcpb", false);
    }

    @Override
    public void process(CommandContext ctx) throws CommandException {
        IRC.INSTANCE.queueDCC(ctx.getArg(ARG_CONTENT), s -> {
            s = Patterns.IRC_FORMATTING.matcher(s).replaceAll("");
            if (!s.trim().isEmpty()) {
                 ctx.replyBuffered(s);
            }
        });
    }

    @Override
    public String getDescription() {
        return "Allows direct interfacing with the IRC frontend for MCP mappings. Use this if you want to set names or use more advanced commands, like findall.";
    }
}
