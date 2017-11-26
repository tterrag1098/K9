package com.blamejared.mcbot.commands;

import java.io.IOException;
import java.net.URL;

import org.apache.commons.io.IOUtils;

import com.blamejared.mcbot.commands.api.Command;
import com.blamejared.mcbot.commands.api.CommandBase;
import com.blamejared.mcbot.commands.api.CommandContext;
import com.blamejared.mcbot.commands.api.CommandException;
import com.google.common.base.Charsets;
import com.google.gson.Gson;

import lombok.Value;
import sx.blah.discord.api.internal.json.objects.EmbedObject;
import sx.blah.discord.util.EmbedBuilder;

@Command
public class CommandDrama extends CommandBase {
    
    @Value
    private static class Drama {
        String drama;
        String version;
        String seed;

        public String url() {
            return "https://ftb-drama.herokuapp.com/" + this.getVersion() + "/" + this.getSeed();
        }
    }
    private static class DramaBackup {
        String drama;
        String version;
        String seed;

        public String url() {
            return "https://drama.blacksun.network/" + this.getVersion() + "/" + this.getSeed();
        }

    public CommandDrama() {
        super("drama", false);
    }
    
    @Override
    public void process(CommandContext ctx) throws CommandException {
        try {
            String json = IOUtils.readLines(new URL("http://ftb-drama.herokuapp.com/json").openStream(), Charsets.UTF_8).get(0);
            Drama drama = new Gson().fromJson(json, Drama.class);
            EmbedObject reply = new EmbedBuilder()
                    .withTitle(ctx.getAuthor().getDisplayName(ctx.getGuild()) + " started some drama!")
                    .withUrl(drama.url())
                    .withDesc(drama.getDrama())
                    .build();
            ctx.replyBuffered(reply);
        } catch (IOException e) {
            try {
                String json = IOUtils.readLines(new URL("http://drama.blacksun.network/json").openStream(), Charsets.UTF_8).get(0);
                DramaBackup drama = new Gson().fromJson(json, Drama.class);
                EmbedObject reply = new EmbedBuilder()
                        .withTitle(ctx.getAuthor().getDisplayName(ctx.getGuild()) + " started some drama!")
                        .withUrl(drama.url())
                        .withDesc(drama.getDrama())
                        .build();
                ctx.replyBuffered(reply);
            } catch (IOException e2) {
                throw new CommandException(e);
                }
                
            }
        }
    }
    
    @Override
    public String getDescription() {
        return "Creates some drama.";
    }
}
