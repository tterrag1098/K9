package com.blamejared.mcbot.irc;


import com.blamejared.mcbot.MCBot;
import com.blamejared.mcbot.commands.api.CommandBase;
import org.pircbotx.*;
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.events.MessageEvent;

public class MCBotIRC {
    
    public MCBotIRC(String password) {
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
            if(MCBot.getChannel("minecraftforgeirc") != null) {
                MCBot.getChannel("minecraftforgeirc").sendMessage(CommandBase.escapeMentions(MCBot.getChannel("minecraftforgeirc").getGuild(), event.getUser().getNick() + "> " + event.getMessage()));
            }
        }
    }
}
