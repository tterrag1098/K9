package com.blamejared.mcbot.commands;

import java.awt.Color;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.Collections;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;

import com.blamejared.mcbot.commands.api.Argument;
import com.blamejared.mcbot.commands.api.Command;
import com.blamejared.mcbot.commands.api.CommandBase;
import com.blamejared.mcbot.commands.api.CommandContext;
import com.blamejared.mcbot.commands.api.CommandException;
import com.google.common.collect.Lists;

import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.util.EmbedBuilder;

@Command
public class CommandCurse extends CommandBase {
    
    private static final Argument<String> ARG_USERNAME = new WordArgument("username", "The curse username of the mod author.", true);
    
    public CommandCurse() {
        super("curse", false, Collections.emptyList(), Lists.newArrayList(ARG_USERNAME));
    }
    
    private final Random rand = new Random();
    
    //TODO make it work with multiple pages / people with 2 pages of mods (Example: tterrag1098)
    //TODO send 2 messages for people that have over 25 mods (Embed Field limit)
    @Override
    public void process(CommandContext ctx) throws CommandException {
        long time = System.currentTimeMillis();
        String user = ctx.getArg(ARG_USERNAME);

        try {

            EmbedBuilder embed = new EmbedBuilder();
            embed.withTitle("Information on: " + user);
            rand.setSeed(user.hashCode());
            embed.withColor(Color.HSBtoRGB(rand.nextFloat(), 1, 1));
            embed.withAuthorName(ctx.getMessage().getAuthor().getDisplayName(ctx.getGuild()) + " requested");
            embed.withAuthorIcon(ctx.getMessage().getAuthor().getAvatarURL());
            Document doc = Jsoup.connect(String.format("https://mods.curse.com/members/%s/projects", user)).userAgent("Mozilla/5.0 (Windows NT 6.1; WOW64; rv:40.0) Gecko/20100101 Firefox/40.1").get();
    
            IMessage sentMessage = ctx.reply(embed.build());
            Map<String, String> nameToURL = new TreeMap<>();
            doc.getElementsByTag("dt").forEach(ele -> {
                String mod = ele.html().split("<a href=\"")[1].split("\">")[0];
                nameToURL.put(ele.html().split("\">")[1].split("</a>")[0], mod);
            });

            doc.getElementsByTag("img").stream().filter(el -> el.hasAttr("alt")).findFirst().ifPresent(el -> {
                for (Attribute at : el.attributes().asList()) {
                    if (at.getValue().startsWith(user)) {
                        embed.withThumbnail(el.attr("src"));
                        break;
                    }
                }
            });

            sentMessage.edit(ctx.sanitize(embed.build()));
            final long[] totalDownloads = {0};
            nameToURL.forEach((key, val) -> {
                try {
                    Document mod = Jsoup.connect("https://mods.curse.com" + val).userAgent("Mozilla/5.0 (Windows NT 6.1; WOW64; rv:40.0) Gecko/20100101 Firefox/40.1").get();
                    long downloads = Long.parseLong(mod.getElementsByClass("downloads").get(0).html().split(" Total")[0].trim().replaceAll(",", ""));
                    embed.appendField(key, "URL: " + "[" + key + "](" + "http://mods.curse.com" + val.replaceAll(" ", "-") + ")\nDownloads: " + NumberFormat.getInstance().format(downloads), true);
                    totalDownloads[0] += downloads;
                } catch(IOException e) {
                    e.printStackTrace();
                }
            });
            embed.appendField("Total downloads", NumberFormat.getInstance().format(totalDownloads[0]), false);
            sentMessage.edit(embed.build());
        } catch(IOException e) {
            e.printStackTrace();
            ctx.reply("User: " + user + " does not exist.");
        }
        System.out.println("Took: " + (System.currentTimeMillis()-time));
    }
    
    public String getDescription() {
        return "Displays download counts for all of a modder's curse projects.";
    }
    
}
