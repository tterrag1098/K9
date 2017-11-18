package com.blamejared.mcbot.commands;

import java.util.Collections;

import com.blamejared.mcbot.commands.api.Argument;
import com.blamejared.mcbot.commands.api.Command;
import com.blamejared.mcbot.commands.api.CommandBase;
import com.blamejared.mcbot.commands.api.CommandContext;
import com.blamejared.mcbot.commands.api.CommandException;
import com.blamejared.mcbot.commands.api.CommandRegistrar;
import com.blamejared.mcbot.commands.api.Flag;
import com.blamejared.mcbot.commands.api.ICommand;
import com.blamejared.mcbot.listeners.CommandListener;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;

import sx.blah.discord.util.EmbedBuilder;

@Command
public class CommandHelp extends CommandBase {
    
    private static final Argument<String> ARG_COMMAND = new WordArgument("command", "The command to get help on.", false);

    public CommandHelp() {
        super("help", false, Collections.emptyList(), Lists.newArrayList(ARG_COMMAND));
    }

    @Override
    public void process(CommandContext ctx) throws CommandException {
        String cmdstr = ctx.getArg(ARG_COMMAND);
        if (cmdstr == null) {
            ctx.reply("To get help on a command, use `" + CommandListener.PREFIX + "help <command>`. To see a list of commands, use `" + CommandListener.PREFIX + "commands`.");
        }
        ICommand command = CommandRegistrar.INSTANCE.findCommand(ctx.getArg(ARG_COMMAND));
        if (command == null) {
            ctx.reply("`" + CommandListener.PREFIX + cmdstr + "` is not a valid command!");
        } else {
            EmbedBuilder embed = new EmbedBuilder();
            embed.withTitle("**Help for " + CommandListener.PREFIX + command.getName() + "**");
            embed.withDesc(Strings.repeat("\u203e", 60));
            embed.appendField("Description:", command.getDescription(), false);
            
            StringBuilder usage = new StringBuilder();
            usage.append('`').append(CommandListener.PREFIX).append(command.getName()).append(' ');
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
                    if (!flag.name().equals(flag.longFormName())) {
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

            ctx.reply(embed.build());
        }
    }

    public String getDescription() {
        return "Displays help for a given command.";
    }
}
