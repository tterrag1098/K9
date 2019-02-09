package com.tterrag.k9.commands;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;

import org.jruby.RubyIO;
import org.jruby.embed.ScriptingContainer;

import com.tterrag.k9.K9;
import com.tterrag.k9.commands.api.Command;
import com.tterrag.k9.commands.api.CommandBase;
import com.tterrag.k9.commands.api.CommandContext;
import com.tterrag.k9.commands.api.CommandException;
import com.tterrag.k9.util.EmbedCreator;
import com.tterrag.k9.util.annotation.Nullable;

import discord4j.core.object.entity.Member;
import discord4j.core.spec.EmbedCreateSpec;
import reactor.core.publisher.Mono;

@Command
public class CommandDrama extends CommandBase {
    
    private final ScriptingContainer sc = new ScriptingContainer();
    private final @Nullable Object draminator;

    public CommandDrama() {
        super("drama", false);
        InputStream script = K9.class.getResourceAsStream("/mcdrama/draminate.rb");
        if (script != null) {
            draminator = sc.runScriptlet(new InputStreamReader(script), "draminate.rb");
        } else {
            draminator = null;
        }
    }
    
    @Override
    public Mono<?> process(CommandContext ctx) {
        if (draminator != null) {
            sc.callMethod(draminator, "set_file_fetcher", new Object() {
                @SuppressWarnings("unused")
                public RubyIO open(String path) {
                    return new RubyIO(sc.getProvider().getRuntime(), K9.class.getResourceAsStream("/mcdrama/" + path));
                }
            });
            BigInteger seed = (BigInteger) sc.callMethod(sc.get("Random"), "new_seed");
            String version = (String) sc.callMethod(draminator, "current_version");
            sc.callMethod(sc.get("Random"), "srand", seed);
            String drama = ((String) sc.callMethod(draminator, "draminate")).replaceAll("(\\r\\n|\\r|\\n)", "");
            
            return ctx.getMember()
                .map(m -> m.getDisplayName())
                .switchIfEmpty(Mono.justOrEmpty(ctx.getAuthor().map(a -> a.getUsername())))
                .map(name -> 
                    EmbedCreator.builder()
                        .title(name + " started some drama!")
                        .url("https://ftb-drama.herokuapp.com/" + version + "/" + seed.toString(36))
                        .description(drama)
                        .build())
                .flatMap(ctx::reply);
        } else {
            return ctx.error("Sorry, the drama command is not set up properly. Contact your bot admin!");
        }
    }
    
    @Override
    public String getDescription() {
        return "Creates some drama.";
    }
}
