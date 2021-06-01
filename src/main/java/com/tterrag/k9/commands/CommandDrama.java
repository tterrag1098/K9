package com.tterrag.k9.commands;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;

import org.apache.commons.io.IOUtils;
import org.jruby.RubyIO;
import org.jruby.embed.ScriptingContainer;

import com.google.common.base.Charsets;
import com.tterrag.k9.K9;
import com.tterrag.k9.commands.api.Command;
import com.tterrag.k9.commands.api.CommandBase;
import com.tterrag.k9.commands.api.CommandContext;
import com.tterrag.k9.util.EmbedCreator;
import com.tterrag.k9.util.annotation.Nullable;

import discord4j.common.util.Snowflake;
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
        InputStream dramaversion = K9.class.getResourceAsStream("/mcdrama/.dramaversion");
        String version = null;
        if (dramaversion != null) {
            try {
                version = IOUtils.readLines(dramaversion, Charsets.UTF_8).get(0);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (version == null) {
            Process proc;
            try {
                proc = Runtime.getRuntime().exec("git submodule --quiet foreach git rev-parse --short HEAD");
                proc.waitFor();
                version = IOUtils.readLines(proc.getInputStream(), Charsets.UTF_8).get(0);
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }
        if (version != null) {
            sc.callMethod(draminator, "set_current_version", version);
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
    public String getDescription(@Nullable Snowflake guildId) {
        return "Creates some drama.";
    }
}
