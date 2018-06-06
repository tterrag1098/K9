package com.tterrag.k9.irc;


import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.pircbotx.Configuration;
import org.pircbotx.PircBotX;
import org.pircbotx.exception.IrcException;
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.events.MessageEvent;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.tterrag.k9.commands.api.CommandContext;

import lombok.val;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.obj.Channel;
import sx.blah.discord.util.RequestBuffer;

public enum IRC {
    
    INSTANCE;
    
    private PircBotX bot;
    
    private final Multimap<String, Channel> relays = HashMultimap.create();
    private final Map<Channel, String> sendableChannels = new HashMap<>();
    
    public void connect(String username, String password) {
        Configuration<PircBotX> esper = new Configuration.Builder<>().setAutoReconnect(true).setLogin(username).setNickservPassword(password).setServer("irc.esper.net", 6667).addListener(new Listener()).setName(username).buildConfiguration();
        bot = new PircBotX(esper);
        try {
            bot.startBot();
        } catch(IOException | IrcException e) {
            throw new RuntimeException(e);
        }
    }
    
    public void addChannel(String channel, Channel relay, boolean readonly) {
       if (bot != null) { 
           bot.sendIRC().joinChannel(channel);
           relays.put(channel, relay);
           if (!readonly) {
               sendableChannels.put(relay, channel);
           }
       }
    }
    
    public void removeChannel(String channel, Channel relay) {
        Collection<Channel> chans = relays.get(channel);
        if (chans.remove(relay) && chans.isEmpty()) {
            relays.removeAll(channel);
            sendableChannels.remove(relay);
            bot.sendRaw().rawLine("PART " + channel);
        }
    }
    
    @EventSubscriber
    public void onMessageRecieved(MessageReceivedEvent event) {
        if (bot == null) return;
        Channel chan = event.getChannel();
        for (val e : sendableChannels.entrySet()) {
            if (e.getKey().equals(chan)) {
                bot.sendIRC().message(e.getValue(), 
                        "<" + event.getMessage().getAuthor().getDisplayName(event.getGuild()) + "> " + event.getMessage().getFormattedContent());
            }
        }
    }
    
    private class Listener extends ListenerAdapter<PircBotX> {
        
        @Override
        public void onMessage(MessageEvent<PircBotX> event) throws Exception {
            if (event.getUser().getNick().startsWith("Not-")) return; // Ignore notification bots
            Collection<Channel> chans = relays.get(event.getChannel().getName());
            for (Channel channel : chans) {
                RequestBuffer.request(() -> channel.sendMessage(CommandContext.sanitize(channel.getGuild(), "<" + event.getUser().getNick() + "> " + event.getMessage())));
            }
        }
    }
}
