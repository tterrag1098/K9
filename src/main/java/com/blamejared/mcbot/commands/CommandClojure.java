package com.blamejared.mcbot.commands;

import java.io.StringWriter;
import java.security.AccessControlException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import com.blamejared.mcbot.MCBot;
import com.blamejared.mcbot.commands.api.Command;
import com.blamejared.mcbot.commands.api.CommandBase;
import com.blamejared.mcbot.commands.api.CommandContext;
import com.blamejared.mcbot.commands.api.CommandException;

import clojure.java.api.Clojure;
import clojure.lang.IFn;
import clojure.lang.PersistentArrayMap;
import clojure.lang.PersistentVector;
import clojure.lang.Var;

@Command
public class CommandClojure extends CommandBase {

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
    
    public CommandClojure() {
        super("clj", false);
        
        IFn require = Clojure.var("clojure.core", "require");
        require.invoke(Clojure.read("clojail.core"));
        require.invoke(Clojure.read("clojail.jvm"));
        require.invoke(Clojure.read("clojail.testers"));

        IFn sandboxfn = Clojure.var("clojail.core", "sandbox");
        Var secure_tester = (Var) Clojure.var("clojail.testers", "secure-tester");
        IFn blacklist_objects = Clojure.var("clojail.testers", "blacklist-objects");
        IFn blacklist_packages = Clojure.var("clojail.testers", "blacklist-packages");
        Object tester = Clojure.var("clojure.core/conj").invoke(secure_tester.getRawRoot(),
                blacklist_objects.invoke(PersistentVector.create((Object[]) BLACKLIST_CLASSES)),
                blacklist_packages.invoke(PersistentVector.create((Object[]) BLACKLIST_PACKAGES)));
        this.sandbox = (IFn) sandboxfn.invoke(tester, Clojure.read(":timeout"), 2000L);
    }
    
    @Override
    public void process(CommandContext ctx) throws CommandException {
        ctx.replyBuffered(exec(ctx.getArg(ARG_EXPR)).toString());
    }
    
    public Object exec(String code) throws CommandException {
        try {
            StringWriter sw = new StringWriter();
            Object res = sandbox.invoke(Clojure.read(code), new PersistentArrayMap(new Object[] {Clojure.var("clojure.core", "*out*"), sw}));
            String output = sw.getBuffer().toString();
            return res == null ? output : res.toString();
        } catch (Exception e) {
            // Can't catch TimeoutException because invoke() does not declare it as a possible checked exception
            if (e instanceof TimeoutException) {
                throw new CommandException("That took too long to execute!");
            } else if (e instanceof AccessControlException || e instanceof SecurityException || (e instanceof ExecutionException && e.getCause() instanceof AccessControlException)) {
                throw new CommandException("Sorry, you're not allowed to do that!");            
            } else if (e instanceof ExecutionException) {
                throw new CommandException(e.getCause());
            }
            throw new CommandException(e);
        }
    }
    
    @Override
    public String getDescription() {
        return "Evaluate some clojure code in a sandboxed REPL.";
    }
}
