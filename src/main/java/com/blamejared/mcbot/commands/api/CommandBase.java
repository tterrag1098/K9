package com.blamejared.mcbot.commands.api;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.blamejared.mcbot.MCBot;
import com.blamejared.mcbot.util.NonNull;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import sx.blah.discord.api.internal.json.objects.EmbedObject;
import sx.blah.discord.handle.obj.IGuild;
import sx.blah.discord.handle.obj.IUser;

@RequiredArgsConstructor
@Getter
public abstract class CommandBase implements ICommand {

    private final @NonNull String name;
    @Accessors(fluent = true)
    private final boolean admin;

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + getName().hashCode();
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        
        CommandBase other = (CommandBase) obj;
        return getName().equals(other.getName());
    }
    
    private static final Pattern REGEX_MENTION = Pattern.compile("<@&?!?([0-9]+)>");
    
    public static String escapeMentions(IGuild guild, String message) {
        if (message == null) return null;
        
    	Matcher matcher = REGEX_MENTION.matcher(message);
    	while (matcher.find()) {
    	    long id = Long.parseLong(matcher.group(1));
    	    String name;
    	    if (matcher.group().contains("&")) {
    	        name = "the " + MCBot.instance.getRoleByID(id).getName();
    	    } else {
    	        IUser user = guild.getUserByID(id);
    	        name = matcher.group().contains("!") ? escapeMentions(guild, user.getNicknameForGuild(guild)) : user.getName();
    	    }

    		message = message.replace(matcher.group(), name);
        }
        return message.replace("@here", "everyone").replace("@everyone", "everyone");
    }
    
    public static EmbedObject escapeMentions(IGuild guild, EmbedObject embed) {
    	embed.title = escapeMentions(guild, embed.title);
    	embed.description = escapeMentions(guild, embed.description);
    	return embed;
    }
}
