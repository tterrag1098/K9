package com.tterrag.k9.irc;


import org.pircbotx.Configuration;
import org.pircbotx.PircBotX;
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.events.MessageEvent;

import com.tterrag.k9.K9;
import com.tterrag.k9.commands.api.CommandContext;

public class IRC {
    
    public IRC(String password) {
        Configuration<PircBotX> esper = new Configuration.Builder<>().setAutoReconnect(true).setLogin("MCDis").setNickservPassword(password).setServer("irc.esper.net", 6667).addListener(new Listener()).setName("MCDis").addAutoJoinChannel("#minecraftforge").buildConfiguration();
        PircBotX bot = new PircBotX(esper);
        try {
            bot.startBot();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
    
    private class Listener extends ListenerAdapter<PircBotX> {
        
        @Override
        public void onMessage(MessageEvent<PircBotX> event) throws Exception {
            if(K9.getChannel("minecraftforgeirc") != null) {
                K9.getChannel("minecraftforgeirc").sendMessage(CommandContext.sanitize(K9.getChannel("minecraftforgeirc").getGuild(), event.getUser().getNick() + "> " + event.getMessage()));
            }
        }
    }
}
