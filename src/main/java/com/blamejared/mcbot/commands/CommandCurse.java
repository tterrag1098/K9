package com.blamejared.mcbot.commands;

import java.awt.Color;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;
import java.util.TreeMap;

import org.apache.commons.io.IOUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import com.blamejared.mcbot.commands.api.Command;
import com.blamejared.mcbot.commands.api.CommandBase;
import com.blamejared.mcbot.commands.api.CommandContext;
import com.blamejared.mcbot.commands.api.CommandException;

import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.util.EmbedBuilder;

@Command
public class CommandCurse extends CommandBase {
    
    public CommandCurse() {
        super("curse", false);
    }
    
    private final Random rand = new Random();
    
    //TODO make it work with multiple pages / people with 2 pages of mods (Example: tterrag1098)
    //TODO send 2 messages for people that have over 25 mods (Embed Field limit)
    @Override
    public void process(CommandContext ctx) throws CommandException {
        long time = System.currentTimeMillis();
        if(ctx.getArgs().size() < 1) {
            throw new CommandException("Not enough arguments.");
        }
        try {
            File userFolder = Paths.get( "data", "curse", ctx.getArg(0)).toFile();
            if(!userFolder.exists()) {
                userFolder.mkdirs();
            }
            EmbedBuilder embed = new EmbedBuilder();
            embed.withTitle("Information on: " + ctx.getArg(0));
            rand.setSeed(ctx.getMessage().getChannel().getMessageHistory().size() * ctx.getMessage().getChannel().getName().hashCode());
            embed.withColor(Color.HSBtoRGB(rand.nextFloat(), 1, 1));
            embed.withAuthorName(ctx.getMessage().getAuthor().getDisplayName(ctx.getMessage().getGuild()) + " requested");
            embed.withAuthorIcon(ctx.getMessage().getAuthor().getAvatarURL());
            Document doc = Jsoup.connect(String.format("https://mods.curse.com/members/%s/projects", ctx.getArg(0))).userAgent("Mozilla/5.0 (Windows NT 6.1; WOW64; rv:40.0) Gecko/20100101 Firefox/40.1").get();
    
            IMessage sentMessage = ctx.getMessage().getChannel().sendMessage(embed.build());
            Map<String, String> nameToURL = new TreeMap<>();
            doc.getElementsByTag("dt").forEach(ele -> {
                String mod = ele.html().split("<a href=\"")[1].split("\">")[0];
                nameToURL.put(ele.html().split("\">")[1].split("</a>")[0], mod);
            });
            if(userFolder.listFiles((dir, name) -> name.equals("avatar.txt")) != null) {
                File avatar = new File(userFolder,"avatar.txt");
                if(!avatar.exists()){
                    avatar.createNewFile();
                }
                Scanner scanner = new Scanner(avatar);
                if(scanner.hasNextLine()) {
                    String url = scanner.nextLine();
                    embed.withThumbnail(url);
                }else{
                    doc.getElementsByTag("img").forEach(el -> {
                        if(el.hasAttr("alt")) {
                            el.attributes().forEach(at -> {
                                if(at.getValue().startsWith(ctx.getArg(0))) {
                                    embed.withThumbnail(el.attr("src"));
                                    PrintWriter writer = null;
                                    try {
                                        writer = new PrintWriter(avatar);
                                        writer.println(el.attr("src"));
                                    } catch(FileNotFoundException e) {
                                        e.printStackTrace();
                                        IOUtils.closeQuietly(writer);
                                    }
                                }
                            });
                        }
                    });
                }
                scanner.close();
            } else {
                doc.getElementsByTag("img").forEach(el -> {
                    if(el.hasAttr("alt")) {
                        el.attributes().forEach(at -> {
                            if(at.getValue().startsWith(ctx.getArg(0))) {
                                embed.withThumbnail(el.attr("src"));
                                PrintWriter writer = null;
                                try {
                                    writer = new PrintWriter(new File(userFolder,"avatar.txt"));
                                    writer.println(el.attr("src"));
                                } catch(FileNotFoundException e) {
                                    e.printStackTrace();
                                } finally {
                                    IOUtils.closeQuietly(writer);
                                }
                            }
                        });
                    }
                });
            }
            
            sentMessage.edit(embed.build());
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
            ctx.getMessage().getChannel().sendMessage("User: " + ctx.getArg(0) + " does not exist.");
        }
        System.out.println("Took: " + (System.currentTimeMillis()-time));
    }
    
    public String getUsage() {
        return "<username>";
    }
    
}
