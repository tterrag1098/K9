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
import com.tterrag.k9.util.EmbedCreator;

import reactor.core.publisher.Mono;

@Command
public class CommandHelp extends CommandBase {
    
    private static final Argument<String> ARG_COMMAND = new WordArgument("command", "The command to get help on.", false);

    public CommandHelp() {
        super("help", false);
    }

    @Override
    public void process(CommandContext ctx) throws CommandException {
        String cmdstr = ctx.getArg(ARG_COMMAND);
        Mono<String> prefix = ctx.getGuild().map(CommandListener::getPrefix);
        if (cmdstr == null) {
        	prefix.subscribe(p -> ctx.replyFinal("To get help on a command, use `" + p + "help <command>`. To see a list of commands, use `" + p + "commands`."));
            return;
        }
        Mono<ICommand> command = ctx.getGuild().flatMap(g -> CommandRegistrar.INSTANCE.findCommand(g, ctx.getArg(ARG_COMMAND)));
        if (command == null) {
        	prefix.subscribe(p -> ctx.replyFinal("`" + p + cmdstr + "` is not a valid command!"));
        } else {
            Mono.just(EmbedCreator.builder())
            	.zipWith(command, (embed, cmd) -> {
                    embed.title("**Help for " + prefix + cmd.getName() + "**");
                    embed.description(cmd.getDescription());
                    
                    StringBuilder usage = new StringBuilder();
                    usage.append('`').append(prefix).append(cmd.getName()).append(' ');
                    for (Argument<?> arg : cmd.getArguments()) {
                        if (arg.required(Collections.emptyList())) {
                            usage.append('<').append(arg.name()).append('>');
                        } else {
                            usage.append('[').append(arg.name()).append(']');
                        }
                        usage.append(' ');
                    }
                    usage.append("`\n");
                    for (Argument<?> arg : cmd.getArguments()) {
                        usage.append("- ").append(arg.name()).append(": ").append(arg.description()).append('\n');
                    }
                    embed.field("Usage:", usage.toString(), false);

                    if (!cmd.getFlags().isEmpty()) {
                        StringBuilder flags = new StringBuilder();
                        flags.append("*\"VALUE\" is required, \"[VALUE]\" is optional.*\n\n");
                        for (Flag flag : cmd.getFlags()) {
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
                        embed.field("Flags:", flags.toString(), false);
                    }
                    
                    embed.field("Required Permissions:", cmd.requirements().toString(), false);
                    
                    return embed.build();
            	})
            	.subscribe(ctx::replyFinal);
        }
    }

    @Override
    public String getDescription() {
        return "Displays help for a given command.";
    }
}
