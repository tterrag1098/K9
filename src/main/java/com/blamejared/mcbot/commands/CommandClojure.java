package com.blamejared.mcbot.commands;

import java.io.StringWriter;
import java.security.AccessControlException;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.apache.commons.io.IOUtils;

import com.blamejared.mcbot.MCBot;
import com.blamejared.mcbot.commands.CommandQuote.Quote;
import com.blamejared.mcbot.commands.api.Command;
import com.blamejared.mcbot.commands.api.CommandBase;
import com.blamejared.mcbot.commands.api.CommandContext;
import com.blamejared.mcbot.commands.api.CommandException;
import com.blamejared.mcbot.commands.api.CommandRegistrar;
import com.blamejared.mcbot.trick.Trick;
import com.blamejared.mcbot.util.NonNull;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.Maps;

import clojure.java.api.Clojure;
import clojure.lang.AFn;
import clojure.lang.IFn;
import clojure.lang.IPersistentMap;
import clojure.lang.PersistentArrayMap;
import clojure.lang.PersistentHashMap;
import clojure.lang.PersistentVector;
import clojure.lang.Var;
import lombok.SneakyThrows;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IGuild;
import sx.blah.discord.handle.obj.IUser;

@Command
public class CommandClojure extends CommandBase {
    
    private static class BindingBuilder {
        
        private Map<Object, Object> bindings = new HashMap<>();
        
        public BindingBuilder bind(String name, Object val) {
            this.bindings.put(Clojure.read(":" + name), val);
            return this;
        }
        
        public IPersistentMap build() {
            return PersistentHashMap.create(Maps.newHashMap(this.bindings));
        }
    }

    private static final SentenceArgument ARG_EXPR = new SentenceArgument("expression", "The clojure expression to evaluate.", true);
    
    private static final Class<?>[] BLACKLIST_CLASSES = {
            Thread.class
    };
    
    // Blacklist accessing discord functions
    private static final String[] BLACKLIST_PACKAGES = {
            MCBot.class.getPackage().getName(),
            "sx.blah.discord"
    };
    
    private final IFn sandbox;
    
    @SneakyThrows
    public CommandClojure() {
        super("clj", false);
        
        IFn require = Clojure.var("clojure.core", "require");
        require.invoke(Clojure.read("clojail.core"));
        require.invoke(Clojure.read("clojail.jvm"));
        require.invoke(Clojure.read("clojail.testers"));
        
        IFn read_string = Clojure.var("clojure.core", "read-string");

        IFn sandboxfn = Clojure.var("clojail.core", "sandbox");
        Var secure_tester = (Var) Clojure.var("clojail.testers", "secure-tester");
        IFn blacklist_objects = Clojure.var("clojail.testers", "blacklist-objects");
        IFn blacklist_packages = Clojure.var("clojail.testers", "blacklist-packages");
        
        // Set up global context vars
        addContextVar("author");
        addContextVar("channel");
        addContextVar("guild");
        addContextVar("quotes");
        addContextVar("tricks");
        
        Object tester = Clojure.var("clojure.core/conj").invoke(secure_tester.getRawRoot(),
                blacklist_objects.invoke(PersistentVector.create((Object[]) BLACKLIST_CLASSES)),
                blacklist_packages.invoke(PersistentVector.create((Object[]) BLACKLIST_PACKAGES)));
        this.sandbox = (IFn) sandboxfn.invoke(tester, 
                Clojure.read(":timeout"), 2000L,
                Clojure.read(":namespace"), Clojure.read("mcbot.sandbox"),
                Clojure.read(":refer-clojure"), false,
                Clojure.read(":init"), read_string.invoke(Joiner.on('\n').join(
                        IOUtils.readLines(MCBot.class.getResourceAsStream("/sandbox-init.clj"), Charsets.UTF_8))));
    }
    
    private final Set<String> contextVars = new LinkedHashSet<>();
    
    private void addContextVar(String name) {
        String var = "*" + name + "*";
        ((Var) Clojure.var("mcbot.sandbox", var)).setDynamic().bindRoot(new PersistentArrayMap(new Object[0]));
        contextVars.add(var);
    }
    
    @Override
    public void process(CommandContext ctx) throws CommandException {
        ctx.replyBuffered("=> " + exec(ctx, ctx.getArg(ARG_EXPR)).toString());
    }
    
    public Object exec(CommandContext ctx, String code) throws CommandException {
        try {
            StringWriter sw = new StringWriter();
            
            IUser user = ctx.getAuthor();
            IPersistentMap authorBindings = new BindingBuilder()
                    .bind("name", user.getName())
                    .bind("nick", user.getDisplayName(ctx.getGuild()))
                    .bind("id", user.getLongID())
                    .build();
            
            IChannel chan = ctx.getChannel();
            IPersistentMap channelBindings = new BindingBuilder()
                    .bind("name", chan.getName())
                    .bind("id", chan.getLongID())
                    .bind("topic", chan.getTopic())
                    .build();
            
            IGuild guild = ctx.getGuild();
            IPersistentMap guildBindings = new BindingBuilder()
                    .bind("name", guild.getName())
                    .bind("id", guild.getLongID())
                    .bind("owner", guild.getOwner().getLongID())
                    .bind("region", guild.getRegion().getName())
                    .build();

            IFn quoteBindings = new AFn() {

                @Override
                public Object invoke(Object arg1) {
                    Quote q = ((CommandQuote) CommandRegistrar.INSTANCE.findCommand("quote")).getData(ctx).get(((Long)arg1).intValue()); 
                    return new BindingBuilder()
                            .bind("quote", q.getQuote())
                            .bind("quotee", q.getQuotee())
                            .bind("owner", q.getOwner())
                            .bind("weight", q.getWeight())
                            .build();
                }
            };
            
            IFn trickBindings = new AFn() {
                
                @Override
                public Object invoke(Object name) {
                    return invoke(name, false);
                }
                
                @Override
                public Object invoke(Object name, Object global) {
                    Trick t = ((CommandTrick) CommandRegistrar.INSTANCE.findCommand("trick")).getTrick(ctx, (String) name, (Boolean) global);
                    return new AFn() {
                        
                        @Override
                        public Object invoke() {
                            return invoke(PersistentVector.create());
                        }
                        
                        @Override
                        public Object invoke(Object args) {
                            return t.process(ctx, (Object[]) Clojure.var("clojure.core", "to-array").invoke(args));
                        }
                    };
                }
            };
            
            Object res = sandbox.invoke(
                    Clojure.read(code), 
                    new PersistentArrayMap(new Object[] {
                            Clojure.var("clojure.core", "*out*"), sw,
                            Clojure.var("mcbot.sandbox", "*author*"), authorBindings,
                            Clojure.var("mcbot.sandbox", "*channel*"), channelBindings,
                            Clojure.var("mcbot.sandbox", "*guild*"), guildBindings,
                            Clojure.var("mcbot.sandbox", "*quotes*"), quoteBindings,
                            Clojure.var("mcbot.sandbox", "*tricks*"), trickBindings}));

            String output = sw.getBuffer().toString();
            return res == null ? output : res.toString();
        } catch (Exception e) {
            e.printStackTrace();
            final @NonNull Throwable cause;
            if (e instanceof ExecutionException) {
                cause = e.getCause();
            } else {
                cause = e;
            }
            // Can't catch TimeoutException because invoke() does not declare it as a possible checked exception
            if (cause instanceof TimeoutException) {
                throw new CommandException("That took too long to execute!");
            } else if (cause instanceof AccessControlException || cause instanceof SecurityException) {
                throw new CommandException("Sorry, you're not allowed to do that!");            
            }
            throw new CommandException(cause);
        }
    }
    
    @Override
    public String getDescription() {
        return "Evaluate some clojure code in a sandboxed REPL.\n\n"
                + "Available context vars: " + Joiner.on(", ").join(contextVars.stream().map(s -> "`" + s + "`").iterator()) + "."
                + " Run `!clj [var]` to preview their contents.";
    }
}
