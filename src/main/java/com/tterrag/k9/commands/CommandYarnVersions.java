package com.tterrag.k9.commands;

import java.util.Map;
import java.util.Map.Entry;

import com.tterrag.k9.commands.api.Argument;
import com.tterrag.k9.commands.api.Command;
import com.tterrag.k9.commands.api.CommandBase;
import com.tterrag.k9.commands.api.CommandContext;
import com.tterrag.k9.commands.api.CommandException;
import com.tterrag.k9.mappings.yarn.YarnDownloader;
import com.tterrag.k9.util.EmbedCreator;
import com.tterrag.k9.util.EmbedCreator.EmbedField;

import gnu.trove.list.array.TIntArrayList;

@Command
public class CommandYarnVersions extends CommandBase {

    private static final Argument<String> ARG_VERSION = CommandMappings.ARG_VERSION;
    
    public CommandYarnVersions() {
        super("yv", false);
    }

    @Override
    public void process(CommandContext ctx) throws CommandException {
        String version = ctx.getArg(ARG_VERSION);
        EmbedCreator.Builder builder = EmbedCreator.builder();
        Map<String, TIntArrayList> versions = YarnDownloader.INSTANCE.getIndexedVersions();
        for (Entry<String, TIntArrayList> e : versions.entrySet()) {
            if (version == null || e.getKey().equals(version)) {
                TIntArrayList mappings = e.getValue();
                StringBuilder body = new StringBuilder();
                if (mappings != null) {
                    body.append(mappings.get(mappings.size() - 1));
                }
                builder.field(new EmbedField("MC " + e.getKey(), body.toString(), false));
            }
        }
        ctx.replyFinal(builder.build());
    }

    @Override
    public String getDescription() {
        return "Lists the latest mappings versions.";
    }

}
