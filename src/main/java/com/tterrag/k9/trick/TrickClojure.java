package com.tterrag.k9.trick;

import com.tterrag.k9.commands.CommandClojure;
import com.tterrag.k9.commands.api.CommandContext;
import com.tterrag.k9.commands.api.CommandException;
import com.tterrag.k9.util.BakedMessage;

import lombok.RequiredArgsConstructor;
import sx.blah.discord.api.internal.json.objects.EmbedObject;

@RequiredArgsConstructor
public class TrickClojure implements Trick {
    
    private final CommandClojure clj;
    private final String code;
    
    @Override
    public BakedMessage process(CommandContext ctx, Object... args) {
        try {
            Object ret = clj.exec(ctx, String.format(code, args));
            if (ret instanceof EmbedObject) {
                return new BakedMessage().withEmbed((EmbedObject) ret);
            } else {
                return new BakedMessage().withContent(ret.toString());
            }
        } catch (CommandException e) {
            return new BakedMessage().withContent("Error evaluating trick: " + e.getLocalizedMessage());
        }
    }
}
