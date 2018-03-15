package com.tterrag.k9.commands;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.List;

import org.apache.commons.io.IOUtils;

import com.google.common.base.Charsets;
import com.tterrag.k9.K9;
import com.tterrag.k9.commands.api.Argument;
import com.tterrag.k9.commands.api.Command;
import com.tterrag.k9.commands.api.CommandBase;
import com.tterrag.k9.commands.api.CommandContext;
import com.tterrag.k9.commands.api.CommandException;
import com.tterrag.k9.commands.api.Flag;
import com.tterrag.k9.util.Requirements;
import com.tterrag.k9.util.Requirements.RequiredType;

import sx.blah.discord.handle.obj.Permissions;
import sx.blah.discord.util.MessageHistory;
import sx.blah.discord.util.RequestBuffer;
import sx.blah.discord.util.RequestBuffer.IRequest;
import sx.blah.discord.util.RequestBuilder;

@Command
public class CommandInfoChannel extends CommandBase {

    private static final Flag FLAG_REPLACE = new SimpleFlag('r', "replace", "Replace the current contents of the channel.", false);
    
    private static final Argument<String> ARG_URL = new WordArgument("url", "The url to load the content from", true);

    public CommandInfoChannel() {
        super("info", false);
    }

    @Override
    public void process(CommandContext ctx) throws CommandException {
        try {
            ctx.getChannel().setTypingStatus(true);
            URL url = new URL(ctx.getArg(ARG_URL));
            List<String> lines = IOUtils.readLines(new InputStreamReader(url.openConnection().getInputStream(), Charsets.UTF_8));
            RequestBuilder builder = new RequestBuilder(K9.instance).shouldBufferRequests(true).doAction(() -> true);
            if (ctx.hasFlag(FLAG_REPLACE)) {
                MessageHistory history = RequestBuffer.request((IRequest<MessageHistory>) () -> ctx.getChannel().getFullMessageHistory()).get();
                for (int i = 0; i < history.size(); i++) {
                    final int idx = i;
                    builder.andThen(() -> {
                        history.get(idx).delete();
                        return true;
                    });
                }
            }
            StringBuilder sb = new StringBuilder();
            for (String s : lines) {
                if (s.equals("=>")) {
                    final String msg = sb.toString();
                    builder.andThen(() -> {
                        ctx.reply(msg);
                        return true;
                    });
                    sb = new StringBuilder();
                } else {
                    sb.append(s + "\n");
                }
            }
            builder.andThen(() -> {
                ctx.getMessage().delete();
                return true;
            });
            builder.execute();

            ctx.getChannel().setTypingStatus(false);
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
