package com.blamejared.mcbot.commands;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.List;

import org.apache.commons.io.IOUtils;

import com.blamejared.mcbot.MCBot;
import com.blamejared.mcbot.commands.api.Argument;
import com.blamejared.mcbot.commands.api.Command;
import com.blamejared.mcbot.commands.api.CommandBase;
import com.blamejared.mcbot.commands.api.CommandContext;
import com.blamejared.mcbot.commands.api.CommandException;
import com.blamejared.mcbot.commands.api.Flag;
import com.blamejared.mcbot.util.Requirements;
import com.blamejared.mcbot.util.Requirements.RequiredType;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;

import sx.blah.discord.handle.obj.Permissions;
import sx.blah.discord.util.MessageHistory;
import sx.blah.discord.util.RequestBuffer;
import sx.blah.discord.util.RequestBuilder;
import sx.blah.discord.util.RequestBuffer.IRequest;

@Command
public class CommandInfoChannel extends CommandBase {

    private static final Flag FLAG_REPLACE = new SimpleFlag("replace", "Replace the current contents of the channel.", false);
    
    private static final Argument<String> ARG_URL = new WordArgument("url", "The url to load the content from", true);

    public CommandInfoChannel() {
        super("info", false, Lists.newArrayList(FLAG_REPLACE), Lists.newArrayList(ARG_URL));
    }

    @Override
    public void process(CommandContext ctx) throws CommandException {
        try {
            ctx.getChannel().setTypingStatus(true);
            URL url = new URL(ctx.getArg(ARG_URL));
            List<String> lines = IOUtils.readLines(new InputStreamReader(url.openConnection().getInputStream(), Charsets.UTF_8));
            RequestBuilder builder = new RequestBuilder(MCBot.instance).shouldBufferRequests(true).doAction(() -> true);
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
