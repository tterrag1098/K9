package com.blamejared.mcbot.commands.api;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import sx.blah.discord.api.internal.json.objects.EmbedObject;

import com.blamejared.mcbot.MCBot;
import com.blamejared.mcbot.util.Nonnull;

@RequiredArgsConstructor
@Getter
public abstract class CommandBase implements ICommand {

    private final @Nonnull String name;
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
    
    private static final Pattern REGEX_MENTION = Pattern.compile("<@([0-9]+)>");
    
    public static String escapeMentions(String message){
    	Matcher matcher = REGEX_MENTION.matcher(message);
    	while (matcher.find()) {
    		message = message.replace(matcher.group(), MCBot.instance.getUserByID(matcher.group(1)).getName());
        }
        return message;
    }
    
    public static EmbedObject escapeMentions(EmbedObject embed) {
    	embed.title = escapeMentions(embed.title);
    	embed.description = escapeMentions(embed.description);
    	return embed;
    }
}
