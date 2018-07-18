package com.tterrag.k9.listeners;

import java.security.Permissions;
import java.util.EnumSet;
import java.util.concurrent.TimeUnit;

import com.tterrag.k9.util.Threads;

import discord4j.core.object.entity.Message;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.obj.IRole;
import sx.blah.discord.util.RequestBuffer;
import sx.blah.discord.util.RequestBuffer.IRequest;

public enum EnderIOListener {

    INSTANCE;

    private static final long CHANNEL = 420827525846138882L;
    private static final long ROLE = 420827595551277056L;

    @EventSubscriber
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getChannelId() == CHANNEL) {
            User author = event.getMessage().getAuthor();
            IRole role = event.getGuild().getRoleByID(ROLE);
            if (event.getMessage().getContent().matches("(?i)join.*")) {
                Message response = RequestBuffer.request(() -> {
                    return event.getChannel().sendMessage(author.mention() + ", welcome to the EnderIO test server. For more information, see <#421420046032830464>.");
                }).get();
                new Thread(() -> {
                    Threads.sleep(TimeUnit.SECONDS.toMillis(10));
                    response.delete();
                    RequestBuffer.request(() -> author.addRole(role));
                    RequestBuffer.request(() -> event.getMessage().delete());
                }).start();
            }
            EnumSet<Permissions> perms = RequestBuffer.request((IRequest<EnumSet<Permissions>>) () -> author.getPermissionsForGuild(event.getGuild())).get();
            if (!perms.contains(Permissions.ADMINISTRATOR)) {
                RequestBuffer.request(() -> event.getMessage().delete());
            }
        }
    }
}
