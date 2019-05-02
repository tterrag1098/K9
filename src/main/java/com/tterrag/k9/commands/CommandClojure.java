package com.tterrag.k9.commands;

import java.io.StringWriter;
import java.security.AccessControlException;
import java.security.Security;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.math.NumberUtils;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.tterrag.k9.K9;
import com.tterrag.k9.commands.CommandQuote.Quote;
import com.tterrag.k9.commands.api.Command;
import com.tterrag.k9.commands.api.CommandBase;
import com.tterrag.k9.commands.api.CommandContext;
import com.tterrag.k9.commands.api.Flag;
import com.tterrag.k9.trick.Trick;
import com.tterrag.k9.util.BakedMessage;
import com.tterrag.k9.util.EmbedCreator;
import com.tterrag.k9.util.Monos;
import com.tterrag.k9.util.annotation.NonNull;

import clojure.java.api.Clojure;
import clojure.lang.AFn;
import clojure.lang.APersistentMap;
import clojure.lang.ArityException;
import clojure.lang.IFn;
import clojure.lang.IPersistentMap;
import clojure.lang.PersistentArrayMap;
import clojure.lang.PersistentHashMap;
import clojure.lang.PersistentVector;
import clojure.lang.Var;
import discord4j.core.object.entity.GuildChannel;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.MessageChannel;
import discord4j.core.object.entity.Role;
import discord4j.core.object.presence.Activity;
import discord4j.core.object.util.Permission;
import discord4j.core.object.util.Snowflake;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.val;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

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
    
    @Value
    private static class ExecutionResult {
        Object result;
        @Accessors(fluent = true)
        boolean deleteSelf;
        
        static ExecutionResult from(APersistentMap map) {
            return new ExecutionResult(map.get(Clojure.read(":res")), (Boolean) map.get(Clojure.read(":delete-self")));
        }
    }
    
    private static final Flag FLAG_NOFN = new SimpleFlag('l', "literal", "Used to force result to be interpreted literally, will not attempt to invoke as a function even if the code returns an IFn", false);
    
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
        Function<Member, Mono<IPersistentMap>> getBinding = m -> Mono.just(new BindingBuilder()
                    .bind("name", m.getUsername())
                    .bind("nick", m.getDisplayName())
                    .bind("id", m.getId().asLong())
                    .bind("bot", m.isBot())
                    .bind("avatar", m.getAvatarUrl())
                    .bind("joined", m.getJoinTime()))
                .flatMap(binding -> m.getPresence().map(p -> binding
                        .bind("presence", new BindingBuilder()
                            .bind("activity", p.getActivity().map(Object::toString).orElse(null))
                            .bind("status", p.getStatus().toString())
                            .bind("streamurl", p.getActivity().map(Activity::getStreamingUrl).orElse(null))
                            .build())))
                .flatMap(binding -> m.getRoles().collectList().map(roles -> binding
                        .bind("roles", PersistentVector.create(roles.stream()
                                .sorted(Comparator.comparing(Role::getRawPosition).reversed())
                                .map(Role::getId)
                                .map(Snowflake::asLong)
                                .toArray(Object[]::new)))))
                .map(BindingBuilder::build);

        // Set up global context vars

        // Create an easily accessible map for the sending user
        addContextVar("author", ctx -> ctx.getMember().flatMap(getBinding::apply));

        // Add a lookup function for looking up an arbitrary user in the guild
        addContextVar("users", ctx -> ctx.getGuild()
                .map(guild -> new AFn() {

            @Override
            public Object invoke(Object id) {
                return guild.getClient().getMemberById(guild.getId(), Snowflake.of(((Number)id).longValue()))
                        .flatMap(getBinding::apply)
                        .single()
                        .onErrorMap(NoSuchElementException.class, e -> new IllegalArgumentException("Could not find user for ID"))
                        .block();
            }
        }));
        
        addContextVar("roles", ctx -> ctx.getGuild().map(guild -> new AFn() {
            
            @Override
            public Object invoke(Object id) {
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
                        .bind("id", ret.getId().asLong())
                        .build();
            }
        }));

        // Simple data bean representing the current channel
        addContextVar("channel", ctx -> 
            ctx.getChannel().map(channel ->
                new BindingBuilder()
                    .bind("name", channel instanceof GuildChannel ? ((GuildChannel) channel).getName() : null)
                    .bind("id", channel.getId().asLong())
//                  .bind("topic", ctx.getChannel().ofType(GuildChannel.class).map(GuildChannel::)? null : ctx.getChannel().getTopic())
                    .build()
        ));

        // Simple data bean representing the current guild
        addContextVar("guild", ctx -> ctx.getGuild().map(guild -> {
            return guild == null ? null :
                new BindingBuilder()
                    .bind("name", guild.getName())
                    .bind("id", guild.getId().asLong())
                    .bind("owner", guild.getOwnerId())
                    .bind("region", guild.getRegionId())
                    .build();
        }));

        // Add the current message ID
        addContextVar("message", ctx -> Mono.just(ctx.getMessage().getId().asLong()));

        // Provide a lookup function for ID->message
        addContextVar("messages", ctx -> ctx.getGuild()
                .flatMapMany(g -> g.getChannels().ofType(MessageChannel.class))
                .switchIfEmpty(ctx.getChannel())
                .collectList()
                .map(channels -> new AFn() {

            @Override
            public Object invoke(Object arg1) {
                Message msg = channels.stream()
                        .filter(c -> !(c instanceof GuildChannel) || ((GuildChannel)c).getEffectivePermissions(ctx.getAuthorId().get()).block().contains(Permission.VIEW_CHANNEL))
                        .map(c -> c.getMessageById(Snowflake.of(((Number)arg1).longValue())).block())
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("No message found"));

                return new BindingBuilder()
                        .bind("content", msg.getContent())
                        .bind("id", arg1)
                        .bind("author", msg.getAuthor().get().getId())
                        .bind("channel", msg.getChannel().block().getId())
                        .bind("timestamp", msg.getTimestamp())
                        .build();
            }
        }));

        // A function for looking up quotes, given an ID, or pass no arguments to return a vector of valid quote IDs
        addContextVar("quotes", ctx -> K9.commands.findCommand(ctx, "quote")
                .flatMap(cmd -> ((CommandQuote)cmd).getData(ctx))
                .map(data -> 

            new AFn() {

                @Override
                public Object invoke() {
                    return PersistentVector.create(data.keySet());
                }
    
                @Override
                public Object invoke(Object arg1) {
                    Quote q = data.get(((Number)arg1).intValue());
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
            }
        ));

        // A function for looking up tricks, given a name. Optionally pass "true" as second param to force global lookup
        addContextVar("tricks", ctx -> ctx.getGuild().map(guild -> new AFn() {

            @Override
            public Object invoke(Object name) {
                return invoke(name, false);
            }

            @Override
            public Object invoke(Object name, Object global) {
                CommandTrick cmd = (CommandTrick) K9.commands.findCommand(guild, "trick").get();
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
        }));
        
        // Used only by us, to delete the invoking message after sandbox is finished
        addContextVar("delete-self", ctx -> Mono.just(ThreadLocal.withInitial(() -> false)));
        
        // Create a sandbox, 2000ms timeout, under domain k9.sandbox, and running the sandbox-init.clj script before execution
        this.sandbox = (IFn) sandboxfn.invoke(tester,
                Clojure.read(":timeout"), 2000L,
                Clojure.read(":namespace"), Clojure.read("k9.sandbox"),
                Clojure.read(":refer-clojure"), false,
                Clojure.read(":init"), read_string.invoke(Joiner.on('\n').join(
                        IOUtils.readLines(K9.class.getResourceAsStream("/sandbox-init.clj"), Charsets.UTF_8))));
    }
    
    private final Map<String, Function<@NonNull CommandContext, Mono<?>>> contextVars = new LinkedHashMap<>();
    
    private void addContextVar(String name, Function<@NonNull CommandContext, Mono<?>> factory) {
        String var = "*" + name + "*";
        ((Var) Clojure.var("k9.sandbox", var)).setDynamic().bindRoot(new PersistentArrayMap(new Object[0]));
        contextVars.put(var, factory);
    }
    
    @Override
    public Mono<?> process(CommandContext ctx) {
        return exec(ctx, ctx.getArg(ARG_EXPR))
                .map(msg -> msg.withContent("=> " + Strings.nullToEmpty(msg.getContent())))
                .transform(Monos.flatZipWith(ctx.getChannel(), BakedMessage::send));
    }
    
    private static final Pattern SANDBOX_METHOD_NAME = Pattern.compile("sandbox/eval\\d+/fn--\\d+");
        
    public Mono<BakedMessage> exec(CommandContext ctx, String code, Object... args) {
        StringWriter sw = new StringWriter();
        
        final Map<Object, Object> initial = new HashMap<>();
        initial.put(Clojure.var("clojure.core", "*out*"), sw);
        
        return Flux.fromIterable(contextVars.entrySet())
            .flatMap(e -> e.getValue().apply(ctx).map(v -> Tuples.of(Clojure.var("k9.sandbox", e.getKey()), v)))
            .collectMap(Tuple2::getT1, Tuple2::getT2, () -> initial)
            .map(bindings -> (APersistentMap) sandbox.invoke(Clojure.read("(exec " + code + " " + ctx.hasFlag(FLAG_NOFN) + " " + parseArgs(args) + ")"), PersistentArrayMap.create(bindings)))
            .onErrorMap(e -> {
                log.error("Clojure error trace: ", e);
                if (e instanceof ExecutionException) {
                    Throwable cause = e.getCause();
                     if (cause != null) {
                         return cause;
                     }
                }
                return e;
            })
            .onErrorResume(TimeoutException.class, $ -> ctx.error("That took too long to execute!"))
            .onErrorResume(e -> e instanceof AccessControlException || e instanceof SecurityException, $ -> ctx.error("Sorry, you're not allowed to do that!"))
            .onErrorResume(ArityException.class, ae -> {
                if (SANDBOX_METHOD_NAME.matcher(ae.name).matches()) {
                    return ctx.error("Incorrect number of arguments (" + ae.actual  + ")");
                }
                return ctx.error(ae);
            })
            .onErrorResume(ctx::error)
            .map(ExecutionResult::from)
            .flatMap(execResult -> {
                Object res = execResult.getResult();
                BakedMessage msg = new BakedMessage();
                if (res instanceof EmbedCreator.Builder) {
                    msg = msg.withEmbed((EmbedCreator.Builder) res);
                } else {
                    if (res == null) {
                        res = sw.getBuffer();
                    }
                    msg = msg.withContent(res.toString());
                }
                if (execResult.deleteSelf()) {
                    return ctx.getMessage().delete()
                            .thenReturn(msg)
                            .zipWith(ctx.getDisplayName(), (m, name) -> m.withContent((m.getContent() == null ? "\n" : m.getContent() + "\n") + "Sent by: " + name));
                }
                return Mono.just(msg);
            });
    }
    
    private static String parseArgs(Object... args) {
        return Arrays.stream(args)
                .map(CommandClojure::asLiteral)
                .collect(Collectors.joining(" "));
    }
    
    private static String asLiteral(Object arg) {
        if (arg instanceof String) {
            if (!NumberUtils.isNumber((String) arg)) {
                return "\"" + arg + "\"";
            }
        }
        if (arg != null) {
            return arg.toString();
        } else {
            return "nil";
        }
    }
    
    @Override
    public String getDescription() {
        return "Evaluate some clojure code in a sandboxed REPL.\n\n"
                + "Available context vars: " + Joiner.on(", ").join(contextVars.keySet().stream().map(s -> "`" + s + "`").iterator()) + "."
                + " Run `!clj [var]` to preview their contents.";
    }
}
