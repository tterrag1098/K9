package com.tterrag.k9.commands;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedList;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import com.google.common.base.Charsets;
import com.tterrag.k9.commands.api.Argument;
import com.tterrag.k9.commands.api.Command;
import com.tterrag.k9.commands.api.CommandBase;
import com.tterrag.k9.commands.api.CommandContext;
import com.tterrag.k9.commands.api.Flag;
import com.tterrag.k9.util.BakedMessage;
import com.tterrag.k9.util.Fluxes;
import com.tterrag.k9.util.Monos;
import com.tterrag.k9.util.Requirements;
import com.tterrag.k9.util.Requirements.RequiredType;

import discord4j.core.object.entity.Message;
import discord4j.rest.util.Permission;
import discord4j.common.util.Snowflake;
import lombok.Value;
import lombok.experimental.Wither;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClient.ResponseReceiver;

@Command
public class CommandInfoChannel extends CommandBase {
    
    private enum ParseState {
        TEXT,
        EMBED,
        SEND,
        ;
    }
    
    @Value
    @Wither
    private static class ParseLine {
        ParseState state;
        String message;
        String file;
        
        static ParseLine parse(String s) {
            if (s.equals("=>")) {
                return new ParseLine(ParseState.SEND, null, null);
            } else if (s.matches("<<https?://\\S+>>")) {
                return new ParseLine(ParseState.EMBED, null, s.substring(2, s.length() - 2));
            } else {
                return new ParseLine(ParseState.TEXT, s, null);
            }
        }
    }

    private static final Flag FLAG_REPLACE = new SimpleFlag('r', "replace", "Replace the current contents of the channel.", false);
    
    private static final Argument<String> ARG_URL = new WordArgument("url", "The url to load the content from", true);

    public CommandInfoChannel() {
        super("info", false);
    }

    @Override
    public Mono<?> process(CommandContext ctx) {
        if (!ctx.getGuildId().isPresent()) {
            return ctx.error("Infochannel is not available in DMs.");
        }
        Mono<Void> replacer = Mono.empty();
        if (ctx.hasFlag(FLAG_REPLACE)) {
            Flux<Message> history = ctx.getChannel().flatMapMany(c -> c.getMessagesBefore(Snowflake.of(Instant.now())));
            replacer = history.timeout(Duration.ofSeconds(30))
                   .flatMap(Message::delete)
                   .onErrorResume(TimeoutException.class, e -> ctx.progress("Sorry, the message history in this channel is too long, or otherwise took too long to load.").then())
                   .then();
        }
        
        ResponseReceiver<?> request = HttpClient.create().get().uri(ctx.getArg(ARG_URL));
        
        Flux<BakedMessage> messages = replacer.then(request.responseSingle(($, content) -> content.asString(Charsets.UTF_8)))
                .flatMapMany(s -> Flux.just(s.split("\n")))
                .map(ParseLine::parse)
                .reduce(new LinkedList<>(), this::appendLine)
                .flatMapIterable(Function.identity())
                .flatMapSequential(msg -> {
                    return Mono.justOrEmpty(msg.getFile())
                            .flatMap(file -> HttpClient.create().get().uri(file).responseSingle(($, content) -> content.asInputStream()))
                            .transform(Monos.asOptional()) // Wrap in optional to capture nulls
                            .map(opt -> {
                                BakedMessage ret = new BakedMessage().withContent(msg.getMessage());
                                if (opt.isPresent()) {
                                    ret = ret.withFile(opt.get()).withFileName(msg.getFile().substring(msg.getFile().lastIndexOf('/') + 1));
                                }
                                return ret;
                            });
                });
                return ctx.getMessage().delete()
                        .thenMany(messages)
                        .transform(Fluxes.flatZipWith(ctx.getChannel().repeat(), BakedMessage::send))
                        .then();
    }
    
    // Append the incoming ParseLine to the current message queue, either by concatenating contents, or pushing a new message
    private LinkedList<ParseLine> appendLine(LinkedList<ParseLine> msgs, ParseLine cur) {
        // Begin first message
        if (msgs.isEmpty()) {
            msgs.add(cur);
            return msgs;
        }
        ParseLine prev = msgs.removeLast();
        // Last message is sent, start a new one
        if (prev.getState() == ParseState.SEND) {
            msgs.add(prev);
            msgs.add(cur);
            return msgs;
        }
        // Append this line to the unsent previous message
        switch (cur.getState()) {
        case SEND:
            prev = prev.withState(ParseState.SEND);
            break;
        case EMBED:
            prev = prev.withFile(cur.getFile());
            break;
        case TEXT:
            prev = prev.withMessage(prev.getMessage() + "\n" + cur.getMessage());
            break;
        }
        msgs.add(prev);
        return msgs;
    }
    
    @Override
    public String getDescription(CommandContext ctx) {
        return "Loads messages in a channel from a URL.";
    }
    
    @Override
    public Requirements requirements() {
        return Requirements.builder().with(Permission.ADMINISTRATOR, RequiredType.ALL_OF).build();
    }
}
