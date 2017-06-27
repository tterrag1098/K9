package com.blamejared.mcbot.commands;

import com.blamejared.mcbot.commands.api.*;

import org.apache.commons.io.IOUtils;
import org.jsoup.*;
import org.jsoup.nodes.Document;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.util.EmbedBuilder;

import java.awt.*;
import java.io.*;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.util.List;
import java.util.*;

@Command
public class CommandCurse extends CommandBase {
    
    public CommandCurse() {
        super("curse", false);
    }
    
    private final Random rand = new Random();
    
    //TODO make it work with multiple pages / people with 2 pages of mods (Example: tterrag1098)
    //TODO send 2 messages for people that have over 25 mods (Embed Field limit)
    @Override
    public void process(IMessage message, List<String> flags, List<String> args) throws CommandException {
        long time = System.currentTimeMillis();
        if(args.size() < 1) {
            throw new CommandException("Not enough arguments.");
        }
        try {
            File userFolder = Paths.get( "data", "curse", args.get(0)).toFile();
            if(!userFolder.exists()) {
                userFolder.mkdirs();
            }
            EmbedBuilder embed = new EmbedBuilder();
            embed.withTitle("Information on: " + args.get(0));
            rand.setSeed(message.getChannel().getMessageHistory().size() * message.getChannel().getName().hashCode());
            embed.withColor(Color.HSBtoRGB(rand.nextFloat(), 1, 1));
            embed.withAuthorName(message.getAuthor().getDisplayName(message.getGuild()) + " requested");
            embed.withAuthorIcon(message.getAuthor().getAvatarURL());
            Document doc = Jsoup.connect(String.format("https://mods.curse.com/members/%s/projects", args.get(0))).userAgent("Mozilla/5.0 (Windows NT 6.1; WOW64; rv:40.0) Gecko/20100101 Firefox/40.1").get();
    
            IMessage sentMessage = message.getChannel().sendMessage(embed.build());
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
                                if(at.getValue().startsWith(args.get(0))) {
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
                            if(at.getValue().startsWith(args.get(0))) {
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
            message.getChannel().sendMessage("User: " + args.get(0) + " does not exist.");
        }
        System.out.println("Took: " + (System.currentTimeMillis()-time));
    }
    
    public String getUsage() {
        return "<username>";
    }
    
}
