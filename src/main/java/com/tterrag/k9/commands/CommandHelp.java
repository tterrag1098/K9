package com.tterrag.k9.commands;

import java.util.Collections;

import com.tterrag.k9.commands.api.Argument;
import com.tterrag.k9.commands.api.Command;
import com.tterrag.k9.commands.api.CommandBase;
import com.tterrag.k9.commands.api.CommandContext;
import com.tterrag.k9.commands.api.CommandException;
import com.tterrag.k9.commands.api.CommandRegistrar;
import com.tterrag.k9.commands.api.Flag;
import com.tterrag.k9.commands.api.ICommand;
import com.tterrag.k9.listeners.CommandListener;

import sx.blah.discord.util.EmbedBuilder;

@Command
public class CommandHelp extends CommandBase {
    
    private static final Argument<String> ARG_COMMAND = new WordArgument("command", "The command to get help on.", false);

    public CommandHelp() {
        super("help", false);
    }

    @Override
    public void process(CommandContext ctx) throws CommandException {
        String cmdstr = ctx.getArg(ARG_COMMAND);
        String prefix = CommandListener.getPrefix(ctx.getGuild());
        if (cmdstr == null) {
            ctx.reply("To get help on a command, use `" + prefix + "help <command>`. To see a list of commands, use `" + prefix + "commands`.");
            return;
        }
        ICommand command = CommandRegistrar.INSTANCE.findCommand(ctx.getArg(ARG_COMMAND));
        if (command == null) {
            ctx.reply("`" + prefix + cmdstr + "` is not a valid command!");
        } else {
            EmbedBuilder embed = new EmbedBuilder();
            embed.withTitle("**Help for " + prefix + command.getName() + "**");
            embed.withDesc(command.getDescription());
            
            StringBuilder usage = new StringBuilder();
            usage.append('`').append(prefix).append(command.getName()).append(' ');
            for (Argument<?> arg : command.getArguments()) {
                if (arg.required(Collections.emptyList())) {
                    usage.append('<').append(arg.name()).append('>');
                } else {
                    usage.append('[').append(arg.name()).append(']');
                }
                usage.append(' ');
            }
            usage.append("`\n");
            for (Argument<?> arg : command.getArguments()) {
                usage.append("- ").append(arg.name()).append(": ").append(arg.description()).append('\n');
            }
            embed.appendField("Usage:", usage.toString(), false);

            if (!command.getFlags().isEmpty()) {
                StringBuilder flags = new StringBuilder();
                flags.append("*\"VALUE\" is required, \"[VALUE]\" is optional.*\n\n");
                for (Flag flag : command.getFlags()) {
                    flags.append("`-").append(flag.name());
                    if (flag.longFormName().length() > 1) {
                        flags.append(", --").append(flag.longFormName());
                        if (flag.canHaveValue()) {
                            if (!flag.needsValue()) {
                                flags.append('[');
                            }
                            flags.append('=').append("VALUE");
                            if (!flag.needsValue()) {
                                flags.append(']');
                            }
                        }
                    } else if (flag.canHaveValue()) {
                        flags.append(flag.needsValue() ? " VALUE" : " [VALUE]");
                    }
                    flags.append("` - ").append(flag.description()).append("\n\n");
                }
                embed.appendField("Flags:", flags.toString(), false);
            }
            
            embed.appendField("Required Permissions:", command.requirements().toString(), false);

            ctx.reply(embed.build());
        }
    }

    @Override
    public String getDescription() {
        return "Displays help for a given command.";
    }
}
