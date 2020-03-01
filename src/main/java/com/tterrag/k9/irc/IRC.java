package com.tterrag.k9.irc;


import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
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
import discord4j.core.object.entity.TextChannel;
import discord4j.core.object.util.Snowflake;
import lombok.Synchronized;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
public enum IRC {
    
    INSTANCE;
    
    @Value
    private class DCCRequest {
        
        private final String message;
        private final Consumer<String> callback;
        
    }
    
    private PircBotX bot;
    
    private final Multimap<String, TextChannel> relays = HashMultimap.create();
    private final Multimap<Snowflake, String> sendableChannels = HashMultimap.create();
    
    private final BlockingQueue<DCCRequest> dccQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<String> responseQueue = new LinkedBlockingQueue<>();
    
    private final AtomicReference<ReceiveChat> dccSession = new AtomicReference<>();
    
    private final Thread dccSender = new Thread(this::sendDCC, "DCC Send Thread");
    private final Thread dccReceiver = new Thread(this::receiveDCC, "DCC Receive Thread");
        
    private final AtomicReference<DCCRequest> activeRequest = new AtomicReference<>();

    public void connect(String username, String password) {
        Configuration esper = new Configuration.Builder().setAutoReconnect(true).setLogin(username).setNickservPassword(password).addServer("irc.esper.net", 6667).addListener(new Listener()).setName(username).buildConfiguration();
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
            relays.remove(channel, relay.getId());
            sendableChannels.remove(relay.getId(), channel);
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
    
    public Mono<MessageCreateEvent> onMessage(MessageCreateEvent event) {
        if (bot == null || event.getMember().flatMap(m -> event.getClient().getSelfId().map(s -> s.equals(m.getId()))).orElse(true)) {
            return Mono.just(event);
        }
        return Flux.fromIterable(sendableChannels.get(event.getMessage().getChannelId()))
                .doOnNext(c -> bot.sendIRC().message(c, "<" + event.getMember().get().getDisplayName() + "> " + event.getMessage().getContent().get()))
                .then()
                .thenReturn(event)
                .doOnError(t -> log.error("Exception processing message for IRC: ", t))
                .onErrorReturn(event);
    }
    
    private class Listener extends ListenerAdapter {
        
        @Override
        public void onMessage(MessageEvent event) throws Exception {
            if (event.getUser().getNick().startsWith("Not-")) return; // Ignore notification bots
            Flux.fromIterable(relays.get(event.getChannel().getName()))
                .flatMap(c -> c.getGuild()
                        .flatMap(g -> CommandContext.sanitize(g, "<" + event.getUser().getNick() + "> " + event.getMessage()))
                        .flatMap(c::createMessage))
                .subscribe();
        }
        
        @Override
        public void onNotice(NoticeEvent event) throws Exception {
            if (event.getMessage().startsWith("You are now identified for")) {
                dccSender.start();
                dccReceiver.start();
            }
        }
        
        @Override
        public void onIncomingChatRequest(IncomingChatRequestEvent event) throws Exception {
            if (event.getUser().getNick().equals("MCPBot_Reborn")) {
                dccSession.set(event.accept());
                queueDCC("", s -> {});
            }
        }
    }
}
