package com.blamejared.mcbot.commands;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;

import org.jruby.RubyIO;
import org.jruby.embed.ScriptingContainer;

import com.blamejared.mcbot.MCBot;
import com.blamejared.mcbot.commands.api.Command;
import com.blamejared.mcbot.commands.api.CommandBase;
import com.blamejared.mcbot.commands.api.CommandContext;
import com.blamejared.mcbot.commands.api.CommandException;
import com.blamejared.mcbot.util.Nullable;

import sx.blah.discord.api.internal.json.objects.EmbedObject;
import sx.blah.discord.util.EmbedBuilder;

@Command
public class CommandDrama extends CommandBase {
    
    private final ScriptingContainer sc = new ScriptingContainer();
    private final @Nullable Object draminator; 

    public CommandDrama() {
        super("drama", false);
        InputStream script = MCBot.class.getResourceAsStream("/mcdrama/draminate.rb");
        if (script != null) {
            draminator = sc.runScriptlet(new InputStreamReader(script), "draminate.rb");
        } else {
            draminator = null;
        }
    }
    
    @Override
    public void process(CommandContext ctx) throws CommandException {
        if (draminator != null) {
            sc.callMethod(draminator, "set_file_fetcher", new Object() {
                @SuppressWarnings("unused")
                public RubyIO open(String path) {
                    return new RubyIO(sc.getProvider().getRuntime(), MCBot.class.getResourceAsStream("/mcdrama/" + path));
                }
            });
            BigInteger seed = (BigInteger) sc.callMethod(sc.get("Random"), "new_seed");
            String version = (String) sc.callMethod(draminator, "current_version");
            sc.callMethod(sc.get("Random"), "srand", seed);
            String drama = (String) sc.callMethod(draminator, "draminate");
            drama = drama.replaceAll("(\\r\\n|\\r|\\n)", "");
            
            EmbedObject reply = new EmbedBuilder()
                    .withTitle(ctx.getAuthor().getDisplayName(ctx.getGuild()) + " started some drama!")
                    .withUrl("https://ftb-drama.herokuapp.com/" + version + "/" + seed.toString(36))
                    .withDesc(drama)
                    .build();
            ctx.replyBuffered(reply);
        } else {
            ctx.replyBuffered("Sorry, the drama command is not set up properly. Contact your bot admin!");
        }
    }
    
    @Override
    public String getDescription() {
        return "Creates some drama.";
    }
}
