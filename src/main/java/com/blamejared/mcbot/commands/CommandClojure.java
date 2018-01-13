package com.blamejared.mcbot.commands;

import java.io.StringWriter;
import java.security.AccessControlException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.function.Function;

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
import lombok.val;
import sx.blah.discord.api.internal.DiscordUtils;
import sx.blah.discord.handle.obj.IGuild;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.handle.obj.IUser;
import sx.blah.discord.handle.obj.Permissions;

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

        // Make sure to load in clojail
        Clojure.var("clojure.core", "require").invoke(Clojure.read("[clojail core jvm testers]"));

        // Convenience declarations of used functions
        IFn read_string = Clojure.var("clojure.core", "read-string");
        IFn sandboxfn = Clojure.var("clojail.core", "sandbox");
        Var secure_tester = (Var) Clojure.var("clojail.testers", "secure-tester");
        
        // Load these to add new blacklisted resources
        IFn blacklist_objects = Clojure.var("clojail.testers", "blacklist-objects");
        IFn blacklist_packages = Clojure.var("clojail.testers", "blacklist-packages");

        // Create our tester with custom blacklist
        Object tester = Clojure.var("clojure.core/conj").invoke(secure_tester.getRawRoot(),
                blacklist_objects.invoke(PersistentVector.create((Object[]) BLACKLIST_CLASSES)),
                blacklist_packages.invoke(PersistentVector.create((Object[]) BLACKLIST_PACKAGES)));

        /* == Setting up Context == */

        // Defining all the context vars and the functions to bind them for a given CommandContext

        // A simple function that returns a map representing a user, given an IUser
        BiFunction<IGuild, IUser, IPersistentMap> getBinding = (g, u) -> new BindingBuilder()
                .bind("name", u.getName())
                .bind("nick", u.getDisplayName(g))
                .bind("id", u.getLongID())
                .bind("presence", new BindingBuilder()
                        .bind("playing", u.getPresence().getPlayingText().orElse(null))
                        .bind("status", u.getPresence().getStatus().toString())
                        .bind("streamurl", u.getPresence().getStreamingUrl().orElse(null))
                        .build())
                .bind("bot", u.isBot())
                .build();

        // Set up global context vars

        // Create an easily accessible map for the sending user
        addContextVar("author", ctx -> getBinding.apply(ctx.getGuild(), ctx.getAuthor()));

        // Add a lookup function for looking up an arbitrary user in the guild
        addContextVar("users", ctx -> new AFn() {

            public Object invoke(Object id) {
                IUser ret = ctx.getGuild().getUserByID(((Number)id).longValue());
                if (ret == null) {
                    throw new IllegalArgumentException("Could not find user for ID");
                }
                return getBinding.apply(ctx.getGuild(), ret);
            }
        });

        // Simple data bean representing the current channel
        addContextVar("channel", ctx -> 
            new BindingBuilder()
                .bind("name", ctx.getChannel().getName())
                .bind("id", ctx.getChannel().getLongID())
                .bind("topic", ctx.getChannel().getTopic())
                .build());

        // Simple data bean representing the current guild
        addContextVar("guild", ctx -> 
            new BindingBuilder()
                .bind("name", ctx.getGuild().getName())
                .bind("id", ctx.getGuild().getLongID())
                .bind("owner", ctx.getGuild().getOwner().getLongID())
                .bind("region", ctx.getGuild().getRegion().getName())
                .build());

        // Add the current message ID
        addContextVar("message", ctx -> ctx.getMessage().getLongID());

        // Provide a lookup function for ID->message
        addContextVar("messages", ctx -> new AFn() {

            public Object invoke(Object arg1) {
                IMessage msg =  ctx.getGuild().getChannels().stream()
                        .filter(c -> c.getModifiedPermissions(MCBot.instance.getOurUser()).contains(Permissions.READ_MESSAGES))
                        .map(c -> c.getMessageByID(((Number)arg1).longValue()))
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("No message found"));

                return new BindingBuilder()
                        .bind("content", msg.getContent())
                        .bind("fcontent", msg.getFormattedContent())
                        .bind("id", arg1)
                        .bind("author", msg.getAuthor().getLongID())
                        .bind("channel", msg.getChannel().getLongID())
                        .bind("timestamp", msg.getTimestamp())
                        .build();
            }
        });

        // A function for looking up quotes, given an ID, or pass no arguments to return a vector of valid quote IDs
        addContextVar("quotes", ctx -> new AFn() {

            public Object invoke() {
                return PersistentVector.create(((CommandQuote) CommandRegistrar.INSTANCE.findCommand("quote")).getData(ctx).keySet());
            }

            @Override
            public Object invoke(Object arg1) {
                Quote q = ((CommandQuote) CommandRegistrar.INSTANCE.findCommand("quote")).getData(ctx).get(((Number)arg1).intValue());
                if (q == null) {
                    throw new IllegalArgumentException("No quote for ID " + arg1);
                }
                return new BindingBuilder()
                        .bind("quote", q.getQuote())
                        .bind("quotee", q.getQuotee())
                        .bind("owner", q.getOwner())
                        .bind("weight", q.getWeight())
                        .bind("id", arg1)
                        .build();
            }
        });

        // A function for looking up tricks, given a name. Optionally pass "true" as second param to force global lookup
        addContextVar("tricks", ctx -> new AFn() {

            @Override
            public Object invoke(Object name) {
                return invoke(name, false);
            }

            @Override
            public Object invoke(Object name, Object global) {
                Trick t = ((CommandTrick) CommandRegistrar.INSTANCE.findCommand("trick")).getTrick(ctx, (String) name, (Boolean) global);
                // Return a function which allows invoking the trick
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
        });
        
        // Create a sandbox, 2000ms timeout, under domain mcbot.sandbox, and running the sandbox-init.clj script before execution
        this.sandbox = (IFn) sandboxfn.invoke(tester, 
                Clojure.read(":timeout"), 2000L,
                Clojure.read(":namespace"), Clojure.read("mcbot.sandbox"),
                Clojure.read(":refer-clojure"), false,
                Clojure.read(":init"), read_string.invoke(Joiner.on('\n').join(
                        IOUtils.readLines(MCBot.class.getResourceAsStream("/sandbox-init.clj"), Charsets.UTF_8))));
    }
    
    private final Map<String, Function<@NonNull CommandContext, Object>> contextVars = new LinkedHashMap<>();
    
    private void addContextVar(String name, Function<@NonNull CommandContext, Object> factory) {
        String var = "*" + name + "*";
        ((Var) Clojure.var("mcbot.sandbox", var)).setDynamic().bindRoot(new PersistentArrayMap(new Object[0]));
        contextVars.put(var, factory);
    }
    
    @Override
    public void process(CommandContext ctx) throws CommandException {
        ctx.replyBuffered("=> " + exec(ctx, ctx.getArg(ARG_EXPR)).toString());
    }
    
    public Object exec(CommandContext ctx, String code) throws CommandException {
        try {
            StringWriter sw = new StringWriter();
            
            Map<Object, Object> bindings = new HashMap<>();
            bindings.put(Clojure.var("clojure.core", "*out*"), sw);
            for (val e : contextVars.entrySet()) {
                bindings.put(Clojure.var("mcbot.sandbox", e.getKey()), e.getValue().apply(ctx));
            }
            
            Object res = sandbox.invoke(Clojure.read(code), PersistentArrayMap.create(bindings));

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
                + "Available context vars: " + Joiner.on(", ").join(contextVars.keySet().stream().map(s -> "`" + s + "`").iterator()) + "."
                + " Run `!clj [var]` to preview their contents.";
    }
}
