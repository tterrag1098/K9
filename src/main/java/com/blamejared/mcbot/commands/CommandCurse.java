package com.blamejared.mcbot.commands;

import java.awt.Color;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Collections;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import com.blamejared.mcbot.commands.api.Argument;
import com.blamejared.mcbot.commands.api.Command;
import com.blamejared.mcbot.commands.api.CommandBase;
import com.blamejared.mcbot.commands.api.CommandContext;
import com.blamejared.mcbot.commands.api.CommandException;
import com.blamejared.mcbot.util.BakedMessage;
import com.blamejared.mcbot.util.DefaultNonNull;
import com.blamejared.mcbot.util.NonNull;
import com.blamejared.mcbot.util.PaginatedMessageFactory;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

import lombok.Value;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.util.EmbedBuilder;

@Command
public class CommandCurse extends CommandBase {
    
    @Value
    @DefaultNonNull
    private static final class ModInfo implements Comparable<ModInfo> {
        String name;
        String URL;
        String[] tags;
        long downloads;
        Document modpage;

        @Override
        public int compareTo(@SuppressWarnings("null") ModInfo o) {
            return getName().compareTo(o.getName());
        }
    }

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
        
        String title = "Information on: " + user;
        
        rand.setSeed(user.hashCode());
        int color = Color.HSBtoRGB(rand.nextFloat(), 1, 1);
        
        String authorName = ctx.getMessage().getAuthor().getDisplayName(ctx.getGuild()) + " requested";
        String authorIcon = ctx.getMessage().getAuthor().getAvatarURL();
        
        IMessage waitMsg = ctx.reply("Please wait, this may take a while...");
        ctx.getChannel().setTypingStatus(true);

        try {
            
            final String ua = "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:40.0) Gecko/20100101 Firefox/40.1";

            Document doc = Jsoup.connect(String.format("https://mods.curse.com/members/%s/projects", user)).userAgent(ua).get();
            
            String avatar = doc.getElementsByClass("avatar").first().child(0).child(0).attr("src");

            Set<ModInfo> mods = new TreeSet<>();
            Element nextPageButton = null;
            // Always run first page
            do {
                // After first page
                if (nextPageButton != null) {
                    doc = Jsoup.connect("https://mods.curse.com" + nextPageButton.child(0).attr("href")).userAgent(ua).get();
                }

                // Get all detail titles, map to their only child (<a> tag)
                doc.getElementsByTag("dt").stream().map(ele -> ele.child(0)).forEach(ele -> {
                    ctx.getChannel().setTypingStatus(true); // make sure this stays active

                    // Mod name is the text, mod URL is the link target
                    @SuppressWarnings("null") 
                    @NonNull 
                    String mod = ele.text();
                    @SuppressWarnings("null") 
                    @NonNull 
                    String url = ele.attr("href");
                    
                    // Navigate up to <dl> and grab second child, which is the <dd> with tags, get all <a> tags from them
                    String[] tags = ele.parent().parent().child(1).getElementsByTag("a").stream().map(e -> e.text()).toArray(String[]::new);

                    try {
                        // TODO catch timeouts, sleep, and try again
                        Document modpage = Jsoup.connect("https://mods.curse.com" + url).userAgent(ua).get();

                        long downloads = Long.parseLong(modpage.getElementsByClass("downloads").get(0).html().split(" Total")[0].trim().replaceAll(",", ""));
                        url = "http://mods.curse.com" + url.replaceAll(" ", "-");

                        mods.add(new ModInfo(mod, url, tags, downloads, modpage));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });

                // Try to find the next page button
                nextPageButton = doc.select(".b-pagination-item.next-page").first();

            // If it's present, process it
            } while (nextPageButton != null);

            // Load main curseforge page and get the total mod download count
            long globalDownloads = Jsoup.connect("https://minecraft.curseforge.com/projects").userAgent(ua).get().getElementsByClass("category-info").stream()
                    .filter(e -> e.child(0).child(0).text().equals("Mods"))
                    .findFirst()
                    .map(e -> e.getElementsByTag("p").first().text())
                    .map(s -> s.substring(s.lastIndexOf("more than"), s.lastIndexOf("downloads"))) // trim out the relevant part of the string
                    .map(s -> s.replaceAll("(more than|,)", "").trim()) // delete excess characters
                    .map(Long::parseLong)
                    .orElseThrow(() -> new CommandException("Could not load global downloads"));
            
            long totalDownloads = mods.stream().mapToLong(ModInfo::getDownloads).sum();
            
            PaginatedMessageFactory.Builder msgbuilder = PaginatedMessageFactory.INSTANCE.builder(ctx.getChannel());
            
            EmbedBuilder mainpg = new EmbedBuilder()
                .withTitle(title)
                .withDesc("Main page")
                .withColor(color)
                .withAuthorName(authorName)
                .withAuthorIcon(authorIcon)
                .withThumbnail(avatar)
                .appendField("Total downloads", NumberFormat.getIntegerInstance().format(totalDownloads) + " (" + formatPercent(((double) totalDownloads / globalDownloads)) + ")", false)
                .appendField("Project count", Integer.toString(mods.size()), false);
            
            StringBuilder top3 = new StringBuilder();
            mods.stream().sorted((m1, m2) -> Long.compare(m2.getDownloads(), m1.getDownloads())).limit(3)
                    .forEach(mod -> top3.append("[").append(mod.getName()).append("](").append(mod.getURL()).append(")").append(": ")
                                        .append(NumberFormat.getIntegerInstance().format(mod.getDownloads())).append('\n'));
            
            mainpg.appendField("Top 3", top3.toString(), false);
                
            msgbuilder.addPage(new BakedMessage().withEmbed(mainpg.build()));
            
            final int modsPerPage = 10;
            final int pages = (mods.size() / modsPerPage) + 1;
            for (int i = 0; i < pages; i++) {
                final EmbedBuilder page = new EmbedBuilder()
                        .withTitle(title)
                        .withDesc("Mods page " + (i + 1) + "/" + pages)
                        .withColor(color)
                        .withAuthorName(authorName)
                        .withAuthorIcon(authorIcon)
                        .withThumbnail(avatar);
                
                mods.stream().skip(modsPerPage * i).limit(modsPerPage).forEach(mod -> {
                    StringBuilder desc = new StringBuilder();

                    desc.append("[Link](" + mod.getURL() + ")\n");
                    
                    desc.append("Tags: ").append(Joiner.on(" | ").join(mod.getTags())).append("\n");

                    desc.append("Downloads: ")
                            .append(DecimalFormat.getIntegerInstance().format(mod.getDownloads()))
                            .append(" (").append(formatPercent((double) mod.getDownloads() / totalDownloads)).append(" of total)\n");
                    
                    String role = mod.getModpage().getElementsByClass("authors").first().children().stream()
                                          .filter(el -> el.children().text().contains(user))
                                          .findFirst()
                                          .map(Element::ownText)
                                          .map(s -> s.trim().substring(0, s.indexOf(':')))
                                          .orElse("Unknown");
                    
                    page.appendField(mod.getName() + " | " + role + "", desc.toString(), false);
                });
                
                msgbuilder.addPage(new BakedMessage().withEmbed(page.build()));
            }
            
            ctx.getChannel().setTypingStatus(false);

            waitMsg.delete();
            msgbuilder.setParent(ctx.getMessage()).setProtected(false).build().send();
            
        } catch(IOException e) {
            e.printStackTrace();
            waitMsg.delete();
            // TODO this makes no sense for most exceptions
            ctx.reply("User: " + user + " does not exist.");
        }
        System.out.println("Took: " + (System.currentTimeMillis()-time));
    }
    
    private final String formatPercent(double pct) {
        NumberFormat pctFmt = DecimalFormat.getPercentInstance();
        pctFmt.setMaximumFractionDigits(pct >= 0.1 ? 0 : pct >= 0.01 ? 1 : 4);
        return pctFmt.format(pct);
    }
    
    public String getDescription() {
        return "Displays download counts for all of a modder's curse projects.";
    }
    
}
