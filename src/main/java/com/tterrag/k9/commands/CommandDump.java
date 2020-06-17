package com.tterrag.k9.commands;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.google.gson.GsonBuilder;
import com.tterrag.k9.commands.api.Argument;
import com.tterrag.k9.commands.api.Command;
import com.tterrag.k9.commands.api.CommandBase;
import com.tterrag.k9.commands.api.CommandContext;
import com.tterrag.k9.commands.api.CommandPersisted;
import com.tterrag.k9.util.BakedMessage;
import com.tterrag.k9.util.Requirements;
import com.tterrag.k9.util.Requirements.RequiredType;

import discord4j.core.object.util.Permission;
import discord4j.core.object.util.Snowflake;
import reactor.core.publisher.Mono;

@Command
public class CommandDump extends CommandBase {
    
    private static final Argument<String> COMMAND_NAME = new WordArgument("command", "The command to dump data for", true);
    
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy.MM.dd-HHmmss");
    
    public CommandDump() {
        super("dump", false);
    }

    @Override
    public Mono<?> process(CommandContext ctx) {
        // Find the command by name
        final String cmd = ctx.getArg(COMMAND_NAME);
        return Mono.justOrEmpty(ctx.getK9().getCommands().findCommand(ctx, cmd))
                .switchIfEmpty(ctx.error("Invalid command: " + cmd))
                // Check if it's a command that has data
                .ofType(CommandPersisted.class)
                .map(c -> (CommandPersisted<?>) c)
                .switchIfEmpty(ctx.error("Command '" + cmd + "' has no data"))
                // Create a Gson for this command and serialize the data, if present
                .flatMap(c -> Mono.justOrEmpty(c.getData(ctx))
                        .flatMap(d -> Mono.just(new GsonBuilder().setPrettyPrinting())
                                .doOnNext(c::gatherParsers)
                                .map(GsonBuilder::create)
                                .map(g -> g.toJson(d))))
                .switchIfEmpty(ctx.error("No data found for command '" + cmd + "' in this guild"))
                // Create a message with the input attached as a file
                .map(s -> new BakedMessage()
                        .withFile(new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8)))
                        .withFileName(ctx.getArg(COMMAND_NAME) + "-"
                                + ctx.getGuildId().map(Snowflake::asString).orElse("DM") + "-"
                                + LocalDateTime.now().format(TIME_FORMAT) + ".json"))
                // DM to the command author
                .flatMap(m -> ctx.getMember()
                        .flatMap(u -> u.getPrivateChannel())
                        .flatMap(m::send)
                        // Delete the command if all succeeded
                        .flatMap(msg -> ctx.getMessage().delete().thenReturn(msg)))
                .switchIfEmpty(ctx.error("Could not create private channel"));
    }
    
    @Override
    public Requirements requirements() {
        return Requirements.builder().with(Permission.MANAGE_GUILD, RequiredType.ALL_OF).build();
    }

    @Override
    public String getDescription(CommandContext ctx) {
        return "Dump the data for this command in this guild to a file, which will be direct messaged to the user.";
    }
}
