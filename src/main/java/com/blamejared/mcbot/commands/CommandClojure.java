package com.blamejared.mcbot.commands;

import java.io.StringWriter;
import java.security.AccessControlException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import com.blamejared.mcbot.commands.api.Command;
import com.blamejared.mcbot.commands.api.CommandBase;
import com.blamejared.mcbot.commands.api.CommandContext;
import com.blamejared.mcbot.commands.api.CommandException;

import clojure.java.api.Clojure;
import clojure.lang.IFn;
import clojure.lang.PersistentArrayMap;
import clojure.lang.Var;

@Command
public class CommandClojure extends CommandBase {

    private static final SentenceArgument ARG_EXPR = new SentenceArgument("expression", "The clojure expression to evaluate.", true);
    
    private final IFn sandbox;
    
    public CommandClojure() {
        super("clj", false);
        
        IFn require = Clojure.var("clojure.core", "require");
        require.invoke(Clojure.read("clojail.core"));
        require.invoke(Clojure.read("clojail.jvm"));
        require.invoke(Clojure.read("clojail.testers"));

        IFn sandboxfn = Clojure.var("clojail.core", "sandbox");
        Var tester = (Var) Clojure.var("clojail.testers", "secure-tester");
        this.sandbox = (IFn) sandboxfn.invoke(tester.getRawRoot(), Clojure.read(":timeout"), 2000L);
    }
    
    @Override
    public void process(CommandContext ctx) throws CommandException {
        try {
            StringWriter sw = new StringWriter();
            String code = ctx.getArg(ARG_EXPR);
            // Blacklist access to any of ours or D4J's code
            if (code.contains("com.blamejared.mcbot") || code.contains("sx.blah.discord")) { 
                throw new AccessControlException("Discord functions.");
            }
            Object res = sandbox.invoke(Clojure.read(ctx.getArg(ARG_EXPR)), new PersistentArrayMap(new Object[] {Clojure.var("clojure.core", "*out*"), sw}));
            String output = sw.getBuffer().toString();
            ctx.reply(res == null ? output : res.toString());
        } catch (Exception e) {
            // Can't catch TimeoutException because invoke() does not declare it as a possible checked exception
            if (e instanceof TimeoutException) {
                throw new CommandException("That took too long to execute!");
            } else if (e instanceof AccessControlException || (e instanceof ExecutionException && e.getCause() instanceof AccessControlException)) {
                throw new CommandException("Sorry, you're not allowed to do that!");            
            }
            throw new CommandException(e);
        }
    }
    
    @Override
    public String getDescription() {
        return "Evaluate some clojure code in a sandboxed REPL.";
    }
}
