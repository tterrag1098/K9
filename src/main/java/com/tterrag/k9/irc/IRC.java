package com.tterrag.k9.irc;


import org.pircbotx.Configuration;
import org.pircbotx.PircBotX;
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.events.MessageEvent;

import com.tterrag.k9.K9;
import com.tterrag.k9.commands.api.CommandContext;

import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.util.RequestBuffer;

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
            IChannel channel = K9.instance.getChannelByID(325106504719925259L);
            if(channel != null) {
                RequestBuffer.request(() -> channel.sendMessage(CommandContext.sanitize(channel.getGuild(), event.getUser().getNick() + "> " + event.getMessage())));
            }
        }
    }
}
