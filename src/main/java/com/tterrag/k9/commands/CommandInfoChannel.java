package com.tterrag.k9.commands;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.security.Permissions;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.commons.io.IOUtils;

import com.google.common.base.Charsets;
import com.tterrag.k9.K9;
import com.tterrag.k9.commands.api.Argument;
import com.tterrag.k9.commands.api.CommandBase;
import com.tterrag.k9.commands.api.CommandContext;
import com.tterrag.k9.commands.api.CommandException;
import com.tterrag.k9.commands.api.Flag;
import com.tterrag.k9.commands.api.CommandContext.TypingStatus;
import com.tterrag.k9.util.Requirements;
import com.tterrag.k9.util.Requirements.RequiredType;

import sx.blah.discord.util.MessageHistory;
import sx.blah.discord.util.RequestBuffer;
import sx.blah.discord.util.RequestBuffer.IRequest;
import sx.blah.discord.util.RequestBuffer.RequestFuture;
import sx.blah.discord.util.RequestBuilder;


public class CommandInfoChannel extends CommandBase {

    private static final Flag FLAG_REPLACE = new SimpleFlag('r', "replace", "Replace the current contents of the channel.", false);
    
    private static final Argument<String> ARG_URL = new WordArgument("url", "The url to load the content from", true);

    public CommandInfoChannel() {
        super("info", false);
    }

    @Override
    public void process(CommandContext ctx) throws CommandException {
        try(TypingStatus typing = ctx.setTyping()) {
            URL url = new URL(ctx.getArg(ARG_URL));
            List<String> lines = IOUtils.readLines(new InputStreamReader(url.openConnection().getInputStream(), Charsets.UTF_8));
            RequestBuilder builder = new RequestBuilder(K9.instance).shouldBufferRequests(true).doAction(() -> true);
            if (ctx.hasFlag(FLAG_REPLACE)) {
                RequestFuture<MessageHistory> future = RequestBuffer.request((IRequest<MessageHistory>) () -> ctx.getChannel().getFullMessageHistory());
                try {
                    MessageHistory history = future.get(30, TimeUnit.SECONDS);
                    if (history.size() > 250) {
                        throw new CommandException("Too many messages in this channel!");
                    }
                    for (int i = 0; i < history.size(); i++) {
                        final int idx = i;
                        builder.andThen(() -> {
                            history.get(idx).delete();
                            return true;
                        });
                    }
                } catch (TimeoutException e) {
                    throw new CommandException("Sorry, the message history in this channel is too long, or otherwise took too long to load.");
                } catch (InterruptedException e) {
                    throw new CommandException("Gathering message history was interrupted for an unknown reason.");
                }
            }
            StringBuilder sb = new StringBuilder();
            String embed = null;
            for (String s : lines) {
                if (s.equals("=>")) {
                    final String msg = sb.toString();
                    final String emb = embed;
                    builder.andThen(() -> {
                        // SKIP SANITIZATION
                        if (emb != null) {
                            try (InputStream in = new URL(emb).openStream()) {
                                String filename = emb.substring(emb.lastIndexOf('/') + 1);
                                ctx.getChannel().sendFile(msg, in, filename);
                            }
                        } else {
                            ctx.getChannel().sendMessage(msg);
                        }
                        return true;
                    });
                    sb = new StringBuilder();
                    embed = null;
                } else if (s.matches("<<https?://\\S+>>")) {
                    embed = s.substring(2, s.length() - 2);
                } else {
                    sb.append(s + "\n");
                }
            }
            builder.andThen(() -> {
                ctx.getMessage().delete();
                return true;
            });
            builder.execute();
        } catch (IOException e) {
            throw new CommandException(e);
        }
    }
    
    @Override
    public String getDescription() {
        return "Loads messages in a channel from a URL.";
    }
    
    @Override
    public Requirements requirements() {
        return Requirements.builder().with(Permissions.ADMINISTRATOR, RequiredType.ALL_OF).build();
    }
}
