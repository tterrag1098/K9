package com.tterrag.k9.listeners;

import java.util.EnumSet;

import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.obj.IRole;
import sx.blah.discord.handle.obj.IUser;
import sx.blah.discord.handle.obj.Permissions;
import sx.blah.discord.util.RequestBuffer;
import sx.blah.discord.util.RequestBuffer.IRequest;

public enum EnderIOListener {

    INSTANCE;

    private static final long CHANNEL = 420827525846138882L;
    private static final long ROLE = 420827595551277056L;

    @EventSubscriber
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getChannelId() == CHANNEL) {
            IUser author = event.getMessage().getAuthor();
            IRole role = event.getGuild().getRoleByID(ROLE);
            if (event.getMessage().getContent().matches("(?i)join.*")) {
                RequestBuffer.request(() -> author.addRole(role));
            } else {
                RequestBuffer.request(() -> author.removeRole(role));
            }
            EnumSet<Permissions> perms = RequestBuffer.request((IRequest<EnumSet<Permissions>>) () -> author.getPermissionsForGuild(event.getGuild())).get();
            if (!perms.contains(Permissions.ADMINISTRATOR)) {
                RequestBuffer.request(() -> event.getMessage().delete());
            }
        }
    }
}
