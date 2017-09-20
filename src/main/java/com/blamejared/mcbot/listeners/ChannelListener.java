package com.blamejared.mcbot.listeners;

import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.blamejared.mcbot.commands.CommandCustomPing;
import com.blamejared.mcbot.commands.CommandCustomPing.CustomPing;
import com.blamejared.mcbot.commands.api.CommandRegistrar;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageUpdateEvent;
import sx.blah.discord.handle.obj.IMessage;

public enum ChannelListener {
    
    INSTANCE;

    public static final String PREFIX = "!";
	public static final Pattern COMMAND_PATTERN = Pattern.compile("!(\\w+)(?:[^\\S\\n](.*))?$");

    @EventSubscriber
    public void onMessageRecieved(MessageReceivedEvent event) {
        checkCustomPing(event.getMessage());
        tryInvoke(event.getMessage());
    }
    
    @EventSubscriber
    public void onMessageEdited(MessageUpdateEvent event){
        checkCustomPing(event.getMessage());
        tryInvoke(event.getMessage());
    }
    
    private void tryInvoke(IMessage msg) {
        Matcher matcher = COMMAND_PATTERN.matcher(msg.getContent());
        if (matcher.matches()) {
            CommandRegistrar.INSTANCE.invokeCommand(msg, matcher.group(1), matcher.group(2));
        }
    }
    
    private void checkCustomPing(IMessage msg) {
        CommandCustomPing cmd = (CommandCustomPing) CommandRegistrar.INSTANCE.findCommand(CommandCustomPing.NAME);
        Multimap<Long, CustomPing> pings = HashMultimap.create();
        cmd.getPingsForGuild(msg.getGuild()).forEach(pings::putAll);
        for (Entry<Long, CustomPing> e : pings.entries()) {
            Matcher matcher = e.getValue().getPattern().matcher(msg.getContent());
            if (matcher.find()) {
                msg.getGuild().getUserByID(e.getKey()).getOrCreatePMChannel().sendMessage(e.getValue().getText() + " <#" + msg.getChannel().getStringID() + ">");
            }
        }
    }
}
