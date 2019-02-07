package com.tterrag.k9.commands;

import java.io.StringWriter;
import java.security.AccessControlException;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
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
import com.tterrag.k9.util.EmbedCreator;
import com.tterrag.k9.util.NonNull;

import clojure.java.api.Clojure;
import clojure.lang.AFn;
import clojure.lang.IFn;
import clojure.lang.IPersistentMap;
import clojure.lang.PersistentArrayMap;
import clojure.lang.PersistentHashMap;
import clojure.lang.PersistentVector;
import clojure.lang.Var;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.GuildChannel;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.MessageChannel;
import discord4j.core.object.entity.Role;
import discord4j.core.object.presence.Activity;
import discord4j.core.object.util.Permission;
import discord4j.core.object.util.Snowflake;
import lombok.SneakyThrows;
import lombok.val;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Command
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
        Function<Member, IPersistentMap> getBinding = m -> new BindingBuilder()
                .bind("name", m.getUsername())
                .bind("nick", m.getDisplayName())
                .bind("id", m.getId())
                .bind("presence", new BindingBuilder()
                        .bind("activity", m.getPresence().block().getActivity().map(Object::toString).orElse(null))
                        .bind("status", m.getPresence().block().getStatus().toString())
                        .bind("streamurl", m.getPresence().block().getActivity().map(Activity::getStreamingUrl).orElse(null))
                        .build())
                .bind("bot", m.isBot())
                .bind("roles", 
                        PersistentVector.create(m.getRoles().collectList().block().stream()
                                .sorted(Comparator.comparing(Role::getRawPosition).reversed())
                                .map(Role::getId)
                                .toArray(Object[]::new)))
                .bind("avatar", m.getAvatarUrl())
                .bind("joined", m.getJoinTime())
                .build();

        // Set up global context vars

        // Create an easily accessible map for the sending user
        addContextVar("author", ctx -> getBinding.apply(ctx.getMember().block()));

        // Add a lookup function for looking up an arbitrary user in the guild
        addContextVar("users", ctx -> new AFn() {

            @Override
            public Object invoke(Object id) {
                return ctx.getGuild()
                        .flatMap(g -> g.getClient().getMemberById(g.getId(), Snowflake.of(((Number)id).longValue())))
                        .map(getBinding::apply)
                        .single()
                        .onErrorMap(NoSuchElementException.class, e -> new IllegalArgumentException("Could not find user for ID"))
                        .block();
            }
        });
        
        addContextVar("roles", ctx -> new AFn() {
            
            @Override
            public Object invoke(Object id) {
                Guild guild = ctx.getGuild().block();
                Role ret = null;
                if (guild != null) {
                    ret = guild.getRoleById(Snowflake.of(((Number)id).longValue())).block();
                }
                if (ret == null) {
                    throw new IllegalArgumentException("Could not find role for ID");
                }
                return new BindingBuilder()
                        .bind("name", ret.getName())
                        .bind("color", PersistentVector.create(ret.getColor().getRed(), ret.getColor().getGreen(), ret.getColor().getBlue()))
                        .bind("id", ret.getId())
                        .build();
            }
        });

        // Simple data bean representing the current channel
        addContextVar("channel", ctx -> 
            ctx.getChannel().map(channel ->
                new BindingBuilder()
                    .bind("name", channel instanceof GuildChannel ? ((GuildChannel) channel).getName() : null)
                    .bind("id", channel.getId())
//                  .bind("topic", ctx.getChannel().ofType(GuildChannel.class).map(GuildChannel::)? null : ctx.getChannel().getTopic())
                    .build()
        ));

        // Simple data bean representing the current guild
        addContextVar("guild", ctx -> {
            Guild guild = ctx.getGuild().block();
            return guild == null ? null :
                new BindingBuilder()
                    .bind("name", guild.getName())
                    .bind("id", guild.getId())
                    .bind("owner", guild.getOwnerId())
                    .bind("region", guild.getRegionId())
                    .build();
        });

        // Add the current message ID
        addContextVar("message", ctx -> ctx.getMessage().getId());

        // Provide a lookup function for ID->message
        addContextVar("messages", ctx -> new AFn() {

            @Override
            public Object invoke(Object arg1) {
                Guild guild = ctx.getGuild().block();
                List<MessageChannel> channels;
                if (guild == null) {
                    channels = Collections.singletonList((MessageChannel) ctx.getChannel().block());
                } else {
                    channels = guild.getChannels().ofType(MessageChannel.class).collectList().block();
                }
                Message msg = channels.stream()
                        .filter(c -> !(c instanceof GuildChannel) || ((GuildChannel)c).getEffectivePermissions(ctx.getClient().getSelfId().get()).block().contains(Permission.VIEW_CHANNEL))
                        .map(c -> c.getMessageById(Snowflake.of(((Number)arg1).longValue())).block())
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("No message found"));

                return new BindingBuilder()
                        .bind("content", msg.getContent())
                        .bind("id", arg1)
                        .bind("author", msg.getAuthor().block().getId())
                        .bind("channel", msg.getChannel().block().getId())
                        .bind("timestamp", msg.getTimestamp())
                        .build();
            }
        });

        // A function for looking up quotes, given an ID, or pass no arguments to return a vector of valid quote IDs
        addContextVar("quotes", ctx -> {
            CommandQuote cmd = (CommandQuote) ctx.getGuild().flatMap(guild -> K9.commands.findCommand(guild, "quote")).block();

            return new AFn() {

                @Override
                public Object invoke() {
                    if (cmd == null) {
                        return null;
                    }
                    return PersistentVector.create(cmd.getData(ctx).block().keySet());
                }
    
                @Override
                public Object invoke(Object arg1) {
                    if (cmd == null) {
                        return null;
                    }
                    Quote q = cmd.getData(ctx).block().get(((Number)arg1).intValue());
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
                CommandTrick cmd = (CommandTrick) ctx.getGuild().flatMap(guild -> K9.commands.findCommand(guild, "trick")).block();
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
    public Mono<?> process(CommandContext ctx) {
        return Mono.fromCallable(() -> exec(ctx, ctx.getArg(ARG_EXPR)))
                .map(msg -> msg.withContent("=> " + Strings.nullToEmpty(msg.getContent())))
                .zipWith(ctx.getChannel(), BakedMessage::send);
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
                    ctx.getMessage().delete().subscribe();
                    binding.bindRoot(null);
                }
            }
            
            BakedMessage msg = new BakedMessage();
            if (res instanceof EmbedCreator.Builder) {
                msg = msg.withEmbed((EmbedCreator.Builder) res);
            } else {
                if (res == null) {
                    res = sw.getBuffer();
                }
                msg = msg.withContent(res.toString());
            }

            if (delete) {
                msg = msg.withContent("Sent by: " + ctx.getAuthor().flatMap(u -> ctx.getGuild().flatMap(g -> u.asMember(g.getId()))).block().getDisplayName() + (msg.getContent() == null ? "" : "\n" + msg.getContent()));
            }
            return msg;
            
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
