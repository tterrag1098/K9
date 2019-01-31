package com.tterrag.k9.irc;


import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.pircbotx.Configuration;
import org.pircbotx.PircBotX;
import org.pircbotx.dcc.ReceiveChat;
import org.pircbotx.exception.IrcException;
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.events.IncomingChatRequestEvent;
import org.pircbotx.hooks.events.MessageEvent;
import org.pircbotx.hooks.events.NoticeEvent;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.tterrag.k9.commands.api.CommandContext;
import com.tterrag.k9.util.Threads;

import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Channel;
import discord4j.core.object.entity.GuildChannel;
import discord4j.core.object.entity.MessageChannel;
import discord4j.core.object.entity.TextChannel;
import discord4j.core.object.util.Snowflake;
import lombok.Synchronized;
import lombok.Value;
import lombok.val;

public enum IRC {
    
    INSTANCE;
    
    @Value
    private class DCCRequest {
        
        private final String message;
        private final Consumer<String> callback;
        
    }
    
    private PircBotX bot;
    
    private final Multimap<String, TextChannel> relays = HashMultimap.create();
    private final Map<Snowflake, String> sendableChannels = new HashMap<>();
    
    private final BlockingQueue<DCCRequest> dccQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<String> responseQueue = new LinkedBlockingQueue<>();
    
    private final AtomicReference<ReceiveChat> dccSession = new AtomicReference<>();
    
    private final Thread dccSender = new Thread(this::sendDCC, "DCC Send Thread");
    private final Thread dccReceiver = new Thread(this::receiveDCC, "DCC Receive Thread");
        
    private final AtomicReference<DCCRequest> activeRequest = new AtomicReference<>();

    public void connect(String username, String password) {
        Configuration<PircBotX> esper = new Configuration.Builder<>().setAutoReconnect(true).setLogin(username).setNickservPassword(password).setServer("irc.esper.net", 6667).addListener(new Listener()).setName(username).buildConfiguration();
        bot = new PircBotX(esper);
        try {
            bot.startBot();
        } catch(IOException | IrcException e) {
            dccSender.interrupt();
            dccReceiver.interrupt();
            throw new RuntimeException(e);
        }
    }
    
    public void addChannel(String channel, TextChannel relay, boolean readonly) {
       if (bot != null) { 
           bot.sendIRC().joinChannel(channel);
           relays.put(channel, relay);
           if (!readonly) {
               sendableChannels.put(relay.getId(), channel);
           }
       }
    }
    
    public void removeChannel(String channel, Channel relay) {
        Collection<TextChannel> chans = relays.get(channel);
        if (chans.remove(relay) && chans.isEmpty()) {
            relays.removeAll(channel);
            sendableChannels.remove(relay.getId());
            bot.sendRaw().rawLine("PART " + channel);
        }
    }
    
    @Synchronized("dccQueue")
    public void queueDCC(String content, Consumer<String> resultCallback) {
        dccQueue.add(new DCCRequest(content, resultCallback));
    }
    
    private void sendDCC() {
        bot.sendIRC().message("MCPBot_Reborn", "!dcc");
        // Wait for session to become active
        while (dccSession.get() == null) {
            Threads.sleep(100);
        }
        ReceiveChat session;
        while ((session = dccSession.get()) != null) {
            // Session went missing? Try to reconnect periodically
            if (session.isFinished()) {
                bot.sendIRC().message("MCPBot_Reborn", "!dcc");
                Threads.sleep(5000);
                continue;
            }
            try {
                DCCRequest req = dccQueue.take();
                activeRequest.set(req);
                try {
                    session.sendLine(req.getMessage());
                    
                    final StringBuilder sb = new StringBuilder("```\n");
                    String line;
                    while ((line = responseQueue.poll(1, TimeUnit.SECONDS)) != null) {
                        sb.append(line).append("\n");
                    }
                    req.getCallback().accept(sb.append("```").toString());                    
                } catch (IOException e) {
                    req.getCallback().accept("Exception sending query: " + e.getMessage());
                    e.printStackTrace();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            Threads.sleep(100);
        }
    }
    
    private void receiveDCC() {
        // Wait for session to become active
        while (dccSession.get() == null) {
            Threads.sleep(100);
        }
        ReceiveChat session;
        while ((session = dccSession.get()) != null) {
            final DCCRequest req = activeRequest.get();
            if (req != null) {
                try {
                    responseQueue.put(session.readLine());
                } catch (IOException e) {
                    e.printStackTrace();
                    req.getCallback().accept("Exception receving query response: " + e.getMessage());
                } catch (InterruptedException e) {}
            } else {
                Threads.sleep(100);
            }
        }
    }
    
    public void onMessageRecieved(MessageCreateEvent event) {
        if (bot == null) return;
        Snowflake chan = event.getMessage().getChannelId();
        for (Entry<Snowflake, String> e : sendableChannels.entrySet()) {
            if (e.getKey().equals(chan)) {
                bot.sendIRC().message(e.getValue(), 
                        "<" + event.getMember().get().getDisplayName() + "> " + event.getMessage().getContent().get());
            }
        }
    }
    
    private class Listener extends ListenerAdapter<PircBotX> {
        
        @Override
        public void onMessage(MessageEvent<PircBotX> event) throws Exception {
            if (event.getUser().getNick().startsWith("Not-")) return; // Ignore notification bots
            Collection<TextChannel> chans = relays.get(event.getChannel().getName());
            for (TextChannel channel : chans) {
                channel.getGuild().flatMap(g -> CommandContext.sanitize(g, "<" + event.getUser().getNick() + "> " + event.getMessage()))
                       .subscribe(s -> channel.createMessage(spec -> spec.setContent(s)));
            }
        }
        
        @Override
        public void onNotice(NoticeEvent<PircBotX> event) throws Exception {
            if (event.getMessage().startsWith("You are now identified for")) {
                dccSender.start();
                dccReceiver.start();
            }
        }
        
        @Override
        public void onIncomingChatRequest(IncomingChatRequestEvent<PircBotX> event) throws Exception {
            if (event.getUser().getNick().equals("MCPBot_Reborn")) {
                dccSession.set(event.accept());
            }
        }
    }
}
