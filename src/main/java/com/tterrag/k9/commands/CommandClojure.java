package com.tterrag.k9.commands;

import java.io.StringWriter;
import java.security.AccessControlException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.apache.commons.io.IOUtils;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.tterrag.k9.K9;
import com.tterrag.k9.commands.CommandQuote.Quote;
import com.tterrag.k9.commands.api.Command;
import com.tterrag.k9.commands.api.CommandBase;
import com.tterrag.k9.commands.api.CommandContext;
import com.tterrag.k9.commands.api.CommandException;
import com.tterrag.k9.commands.api.CommandRegistrar;
import com.tterrag.k9.trick.Trick;
import com.tterrag.k9.util.BakedMessage;
import com.tterrag.k9.util.NonNull;

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
import lombok.extern.slf4j.Slf4j;
import sx.blah.discord.api.internal.json.objects.EmbedObject;
import sx.blah.discord.handle.obj.Channel;
import sx.blah.discord.handle.obj.Guild;
import sx.blah.discord.handle.obj.Message;
import sx.blah.discord.handle.obj.User;
import sx.blah.discord.handle.obj.Permissions;
import sx.blah.discord.util.EmbedBuilder;
import sx.blah.discord.util.RequestBuffer;


@Slf4j
public class CommandClojure extends CommandBase {
    
    private static class BindingBuilder {
        
        private final Map<Object, Object> bindings = new HashMap<>();
        
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
            K9.class.getPackage().getName(),
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

        // A simple function that returns a map representing a user, given an User
        BiFunction<Guild, User, IPersistentMap> getBinding = (g, u) -> new BindingBuilder()
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

            @Override
            public Object invoke(Object id) {
                Guild guild = ctx.getGuild();
                User ret = null;
                if (guild != null) {
                    ret = guild.getUserByID(((Number)id).longValue());
                }
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
                .bind("topic", ctx.getChannel().isPrivate() ? null : ctx.getChannel().getTopic())
                .build());

        // Simple data bean representing the current guild
        addContextVar("guild", ctx -> {
            Guild guild = ctx.getGuild();
            return guild == null ? null :
                new BindingBuilder()
                    .bind("name", guild.getName())
                    .bind("id", guild.getLongID())
                    .bind("owner", guild.getOwner().getLongID())
                    .bind("region", guild.getRegion().getName())
                    .build();
        });

        // Add the current message ID
        addContextVar("message", ctx -> ctx.getMessage().getLongID());

        // Provide a lookup function for ID->message
        addContextVar("messages", ctx -> new AFn() {

            @Override
            public Object invoke(Object arg1) {
                Guild guild = ctx.getGuild();
                List<Channel> channels;
                if (guild == null) {
                    channels = Collections.singletonList(ctx.getChannel());
                } else {
                    channels = guild.getChannels();
                }
                Message msg = channels.stream()
                        .filter(c -> c.getModifiedPermissions(K9.instance.getOurUser()).contains(Permissions.READ_MESSAGES))
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
        addContextVar("quotes", ctx -> {
            CommandQuote cmd = (CommandQuote) CommandRegistrar.INSTANCE.findCommand(ctx.getGuild(), "quote");

            return new AFn() {

                @Override
                public Object invoke() {
                    if (cmd == null) {
                        return null;
                    }
                    return PersistentVector.create(cmd.getData(ctx).keySet());
                }
    
                @Override
                public Object invoke(Object arg1) {
                    if (cmd == null) {
                        return null;
                    }
                    Quote q = cmd.getData(ctx).get(((Number)arg1).intValue());
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
            };
        });

        // A function for looking up tricks, given a name. Optionally pass "true" as second param to force global lookup
        addContextVar("tricks", ctx -> new AFn() {

            @Override
            public Object invoke(Object name) {
                return invoke(name, false);
            }

            @Override
            public Object invoke(Object name, Object global) {
                CommandTrick cmd = (CommandTrick) CommandRegistrar.INSTANCE.findCommand(ctx.getGuild(), "trick");
                if (cmd == null) {
                    return null;
                }
                Trick t = cmd.getTrick(ctx, (String) name, (Boolean) global);
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
        
        // Used only by us, to delete the invoking message after sandbox is finished
        addContextVar("delete-self", ctx -> false);
        
        // Create a sandbox, 2000ms timeout, under domain k9.sandbox, and running the sandbox-init.clj script before execution
        this.sandbox = (IFn) sandboxfn.invoke(tester,
                Clojure.read(":timeout"), 2000L,
                Clojure.read(":namespace"), Clojure.read("k9.sandbox"),
                Clojure.read(":refer-clojure"), false,
                Clojure.read(":init"), read_string.invoke(Joiner.on('\n').join(
                        IOUtils.readLines(K9.class.getResourceAsStream("/sandbox-init.clj"), Charsets.UTF_8))));
    }
    
    private final Map<String, Function<@NonNull CommandContext, Object>> contextVars = new LinkedHashMap<>();
    
    private void addContextVar(String name, Function<@NonNull CommandContext, Object> factory) {
        String var = "*" + name + "*";
        ((Var) Clojure.var("k9.sandbox", var)).setDynamic().bindRoot(new PersistentArrayMap(new Object[0]));
        contextVars.put(var, factory);
    }
    
    @Override
    public void process(CommandContext ctx) throws CommandException {
        BakedMessage ret = exec(ctx, ctx.getArg(ARG_EXPR));
        ret = ret.withContent("=> " + Strings.nullToEmpty(ret.getContent()));
        ret.sendBuffered(ctx.getChannel());
    }
    
    public BakedMessage exec(CommandContext ctx, String code) throws CommandException {
        try {
            StringWriter sw = new StringWriter();
            
            Map<Object, Object> bindings = new HashMap<>();
            bindings.put(Clojure.var("clojure.core", "*out*"), sw);
            for (val e : contextVars.entrySet()) {
                bindings.put(Clojure.var("k9.sandbox", e.getKey()), e.getValue().apply(ctx));
            }
            
            Object res;
            boolean delete;
            // Make sure we only modify *delete-self* on one thread at a time
            synchronized (sandbox) {
                res = sandbox.invoke(Clojure.read(code), PersistentArrayMap.create(bindings));

                Var binding = (Var) Clojure.var("k9.sandbox/*delete-self*");
                delete = binding.get() == Boolean.TRUE;

                if (delete) {
                    RequestBuffer.request(ctx.getMessage()::delete);
                    binding.bindRoot(null);
                }
            }
        
            if (res instanceof EmbedBuilder) {
                res = ((EmbedBuilder) res).build();
            }
            
            BakedMessage ret = new BakedMessage();
            if (res instanceof EmbedObject) {
                ret = ret.withEmbed((EmbedObject) res);
            } else if (res != null) {
                ret = ret.withContent(res.toString());
            } else {
                res = sw.getBuffer().toString();
            }
            
            if (ret.getContent() != null) {
                ret = ret.withContent(ret.getContent());
            }
            
            if (delete) {
                ret = ret.withContent("Sent by: " + ctx.getAuthor().getDisplayName(ctx.getGuild()) + "\n" + ret.getContent());
            }
            return ret;
            
        } catch (Exception e) {
            log.error("Clojure error trace: ", e);
            final Throwable cause;
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
            } else if (cause != null) {
                throw new CommandException(cause);
            }
            throw new CommandException("Unknown");
        }
    }
    
    @Override
    public String getDescription() {
        return "Evaluate some clojure code in a sandboxed REPL.\n\n"
                + "Available context vars: " + Joiner.on(", ").join(contextVars.keySet().stream().map(s -> "`" + s + "`").iterator()) + "."
                + " Run `!clj [var]` to preview their contents.";
    }
}
