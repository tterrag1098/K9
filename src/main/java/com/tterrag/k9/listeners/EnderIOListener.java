package com.tterrag.k9.listeners;

import java.time.Duration;

import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Member;
import discord4j.rest.util.Permission;
import discord4j.rest.util.PermissionSet;
import discord4j.rest.util.Snowflake;
import reactor.core.publisher.Mono;

public enum EnderIOListener {

    INSTANCE;

    private static final Snowflake CHANNEL = Snowflake.of(420827525846138882L);
    private static final Snowflake ROLE = Snowflake.of(420827595551277056L);

    public void onMessage(MessageCreateEvent event) {
        if (event.getMessage().getChannelId().equals(CHANNEL)) {
            Member author = event.getMember().get();
            if (event.getMessage().getContent().get().matches("(?i)join.*")) {
                event.getMessage().getChannel()
                     .flatMap(c -> c.createMessage(spec -> spec.setContent(author.getMention() + ", welcome to the EnderIO test server. For more information, see <#421420046032830464>.")))
                     .delayElement(Duration.ofSeconds(10))
                     .flatMap(m -> m.delete())
                     .then(Mono.when(author.addRole(ROLE), event.getMessage().delete()))
                     .subscribe();
            }
            PermissionSet perms = author.getBasePermissions().block();
            if (!perms.contains(Permission.ADMINISTRATOR)) {
                event.getMessage().delete().subscribe();
            }
        }
    }
}
