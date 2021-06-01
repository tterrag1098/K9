package com.tterrag.k9.commands;

import java.util.Set;

import com.google.common.collect.Sets;
import com.google.gson.reflect.TypeToken;
import com.tterrag.k9.commands.CommandControl.ControlData;
import com.tterrag.k9.commands.api.Argument;
import com.tterrag.k9.commands.api.CommandContext;
import com.tterrag.k9.commands.api.CommandPersisted;
import com.tterrag.k9.commands.api.Flag;
import com.tterrag.k9.util.Requirements;
import com.tterrag.k9.util.Requirements.RequiredType;
import com.tterrag.k9.util.annotation.Nullable;

import discord4j.common.util.Snowflake;
import discord4j.rest.util.Permission;
import lombok.Data;
import lombok.experimental.Accessors;
import reactor.core.publisher.Mono;

// This is created manually by CommandRegistrar, so no @Command
public class CommandControl extends CommandPersisted<ControlData> {

    @Data
    public static class ControlData {
        final Set<String> commandBlacklist = Sets.newConcurrentHashSet();
        
        @Accessors(fluent = true)
        boolean showTrickWarning = false;
        String trickWarningText = "Warning: This trick is unofficial and does not represent the opinions of the server administrators.";
        String trickOfficialText = "This message has been approved by the server administrators.";
    }

    private static final Flag FLAG_BLACKLIST = new SimpleFlag('b', "blacklist", "Blacklist something (a command, etc.)", false);
    private static final Flag FLAG_WHITELIST = new SimpleFlag('w', "whitelist", "Whitelist something (a command, etc.)", false);
    
    private static final Argument<String> ARG_TYPE = new WordArgument("type", "The type of objects being configured, either `commands` or `tricks`.", true);
    private static final Argument<String> ARG_OBJECT = new SentenceArgument("object", "The object to configure.\n\n"
            + "For commands, this is a command name.\n\n"
            + "For tricks, it is one of the following options:\n"
            + "  - showWarning <true/false>\n"
            + "  - warningText <text>\n"
            + "  - officialText <text>\n", true);

    public CommandControl() {
        super("ctrl", false, ControlData::new);
    }
    
    @Override
    protected TypeToken<ControlData> getDataType() {
        return TypeToken.get(ControlData.class);
    }

    @Override
    public Mono<?> process(CommandContext ctx) {
        if (!ctx.getGuildId().isPresent()) {
            return ctx.error("Control is not available in DMs.");
        }
        String type = ctx.getArg(ARG_TYPE);
        switch (type) {
            case "commands":
                if (ctx.hasFlag(FLAG_WHITELIST) && ctx.hasFlag(FLAG_BLACKLIST)) {
                    return ctx.error("Illegal flag combination: Cannot whitelist and blacklist");
                }
                if (ctx.hasFlag(FLAG_WHITELIST)) {
                    return Mono.justOrEmpty(getData(ctx))
                            .doOnNext(data -> data.getCommandBlacklist().remove(ctx.getArg(ARG_OBJECT)))
                            .then(ctx.reply("Whitelisted command."));
                } else if (ctx.hasFlag(FLAG_BLACKLIST)) {
                    return Mono.justOrEmpty(getData(ctx))
                            .doOnNext(data -> data.getCommandBlacklist().add(ctx.getArg(ARG_OBJECT)))
                            .then(ctx.reply("Blacklisted command."));
                }
                break;
            case "tricks":
                String action = ctx.getArg(ARG_OBJECT);
                String field = action.substring(0, action.indexOf(' '));
                String value = action.substring(action.indexOf(' ') + 1);
                return Mono.justOrEmpty(getData(ctx))
                    .doOnNext(data -> {
                        switch (field) {
                            case "showWarning":
                                data.showTrickWarning(Boolean.valueOf(value));
                                break;
                            case "warningText":
                                data.setTrickWarningText(value);
                                break;
                            case "officialText":
                                data.setTrickOfficialText(value);
                                break;
                            default:
                                throw new IllegalArgumentException("Unknown field: " + field);
                        }
                    })
                    .then(ctx.reply("Updated value"));
            default: return ctx.error("Invalid object type: " + type);
        }
        return ctx.error("No action given.");
    }
    
    @Override
    public String getDescription(@Nullable Snowflake guildId) {
        return "Control various aspects about the bot within this guild.";
    }
    
    @Override
    public Requirements requirements() {
        return Requirements.builder().with(Permission.MANAGE_GUILD, RequiredType.ALL_OF).build();
    }
}
