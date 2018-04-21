package com.tterrag.k9.commands;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.commons.lang3.tuple.Pair;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tterrag.k9.K9;
import com.tterrag.k9.commands.api.Command;
import com.tterrag.k9.commands.api.CommandContext;
import com.tterrag.k9.commands.api.CommandException;
import com.tterrag.k9.commands.api.CommandPersisted;
import com.tterrag.k9.commands.api.Flag;
import com.tterrag.k9.irc.IRC;
import com.tterrag.k9.util.Requirements;
import com.tterrag.k9.util.Requirements.RequiredType;

import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.Permissions;

@Command
public class CommandIRC extends CommandPersisted<Map<Long, Pair<String, Boolean>>> {
    
    private static final Flag FLAG_ADD = new SimpleFlag('a', "add", "Add a new relay channel.", false);
    private static final Flag FLAG_READONLY = new SimpleFlag('o', "readonly", "Mark this relay as readonly, that is, messages cannot be sent to IRC from Discord.", false);
    private static final Flag FLAG_REMOVE = new SimpleFlag('r', "remove", "Remove a relay channel.", false);
    
    private static final WordArgument ARG_DISCORD_CHAN = new WordArgument("discord_channel", "The Discord channel.", true) {
        
        private final Pattern pattern = Pattern.compile("<#([0-9]+)>");
      
        @Override
        public Pattern pattern() {
            return pattern;
        }
    };
    private static final WordArgument ARG_IRC_CHAN = new WordArgument("irc_channel", "The IRC channel.", false) {
        private final Pattern pattern = Pattern.compile("#(\\w+)");
        
        @Override
        public Pattern pattern() {
            return pattern;
        }
    };

    public CommandIRC() {
        super("irc", false, HashMap::new);
    }

    @Override
    protected TypeToken<Map<Long, Pair<String, Boolean>>> getDataType() {
        return new TypeToken<Map<Long, Pair<String, Boolean>>>(){};
    }
    
    @Override
    public void init(File dataFolder, Gson gson) {
        super.init(dataFolder, gson);
        storage.forEach(e -> {
            e.getValue().forEach((chan, data) -> IRC.INSTANCE.addChannel(data.getLeft(), K9.instance.getChannelByID(chan), data.getRight()));
        });
    }

    @Override
    public void process(CommandContext ctx) throws CommandException {
        IChannel chan = ctx.getMessage().getChannelMentions().get(0);
        if (!chan.mention().equals(ctx.getArg(ARG_DISCORD_CHAN))) {
            throw new CommandException("Invalid channel.");
        }
        if (ctx.hasFlag(FLAG_ADD)) {
            String ircChan = ctx.getArg(ARG_IRC_CHAN);
            if (ircChan == null) {
                throw new CommandException("Must provide IRC channel.");
            }
            IRC.INSTANCE.addChannel(ircChan, chan, ctx.hasFlag(FLAG_READONLY));
            getData(ctx).put(chan.getLongID(), Pair.of(ircChan, ctx.hasFlag(FLAG_READONLY)));
        } else if (ctx.hasFlag(FLAG_REMOVE)) {
            Pair<String, Boolean> data = getData(ctx).get(chan.getLongID());
            String ircChan = data == null ? null : data.getLeft();
            if (ircChan == null) {
                throw new CommandException("There is no relay in this channel.");
            }
            IRC.INSTANCE.removeChannel(ircChan, chan);
            getData(ctx).remove(chan.getLongID());
        }
    }

    @Override
    public String getDescription() {
        return "Bind an IRC relay to a channel";
    }

    @Override
    public Requirements requirements() {
        return Requirements.builder().with(Permissions.MANAGE_SERVER, RequiredType.ALL_OF).build();
    }
}
