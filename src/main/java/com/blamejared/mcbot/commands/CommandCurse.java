package com.blamejared.mcbot.commands;

import java.awt.Color;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.HttpStatusException;
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
import com.blamejared.mcbot.util.Nullable;
import com.blamejared.mcbot.util.PaginatedMessageFactory;
import com.blamejared.mcbot.util.Threads;
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
        long mdownloads, downloads;
        @Nullable Document modpage;

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

    @Override
    public void process(CommandContext ctx) throws CommandException {
        long time = System.currentTimeMillis();
        String user = ctx.getArg(ARG_USERNAME);
        
        rand.setSeed(user.hashCode());
        int color = Color.HSBtoRGB(rand.nextFloat(), 1, 1);
        
        String authorName = ctx.getMessage().getAuthor().getDisplayName(ctx.getGuild()) + " requested";
        String authorIcon = ctx.getMessage().getAuthor().getAvatarURL();
        
        IMessage waitMsg = ctx.reply("Please wait, this may take a while...");
        ctx.getChannel().setTypingStatus(true);

        PaginatedMessageFactory.Builder msgbuilder = PaginatedMessageFactory.INSTANCE.builder(ctx.getChannel());

        try {

            Document doc;
            try {
                doc = getDocumentSafely(String.format("https://mods.curse.com/members/%s/projects", user));
            } catch (HttpStatusException e) {
                if (e.getStatusCode() / 100 == 4) {
                    throw new CommandException("User " + user + " does not exist.");
                }
                throw e;
            }
            
            String username = doc.getElementsByClass("username").first().text();
            String avatar = doc.getElementsByClass("avatar").first().child(0).child(0).attr("src");

            String title = "Information on: " + username;

            Set<ModInfo> mods = new TreeSet<>();
            Element nextPageButton = null;
            // Always run first page
            do {
                // After first page
                if (nextPageButton != null) {
                    doc = getDocumentSafely("https://mods.curse.com" + nextPageButton.child(0).attr("href"));
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
                    @SuppressWarnings("null")
                    @NonNull
                    String[] tags = ele.parent().parent().child(1).getElementsByTag("a").stream().map(e -> e.text()).toArray(String[]::new);

                    try {
                        Document modpage = getDocumentSafely("https://mods.curse.com" + url);

                        long mdownloads = Long.parseLong(modpage.getElementsByClass("average-downloads").first().text().replaceAll("(Monthly Downloads|,)", "").trim());
                        long downloads = Long.parseLong(modpage.getElementsByClass("downloads").first().text().replaceAll("(Total Downloads|,)", "").trim());
                        url = "http://mods.curse.com" + url.replaceAll(" ", "-");

                        mods.add(new ModInfo(mod, url, tags, mdownloads, downloads, modpage));
                    } catch (IOException e) {
                        e.printStackTrace();
                        mods.add(new ModInfo(mod, url, tags, 0, 0, null));
                    }
                });

                // Try to find the next page button
                nextPageButton = doc.select(".b-pagination-item.next-page").first();

            // If it's present, process it
            } while (nextPageButton != null);
            
            if (mods.isEmpty()) {
                throw new CommandException("User does not have any visible projects.");
            }

            // Load main curseforge page and get the total mod download count
            long globalDownloads = getDocumentSafely("https://minecraft.curseforge.com/projects").getElementsByClass("category-info").stream()
                    .filter(e -> e.child(0).child(0).text().equals("Mods"))
                    .findFirst()
                    .map(e -> e.getElementsByTag("p").first().text())
                    .map(s -> s.substring(s.lastIndexOf("more than"), s.lastIndexOf("downloads"))) // trim out the relevant part of the string
                    .map(s -> s.replaceAll("(more than|,)", "").trim()) // delete excess characters
                    .map(Long::parseLong)
                    .orElseThrow(() -> new CommandException("Could not load global downloads"));
            
            long totalDownloads = mods.stream().mapToLong(ModInfo::getDownloads).sum();
            
            EmbedBuilder mainpg = new EmbedBuilder()
                .withTitle(title)
                .withDesc("Main page")
                .withColor(color)
                .withAuthorName(authorName)
                .withAuthorIcon(authorIcon)
                .withUrl("https://mods.curse.com/members/" + user)
                .withThumbnail(avatar)
                .withTimestamp(LocalDateTime.now())
                .withFooterText("Info provided by Curse/CurseForge")
                .appendField("Total downloads", NumberFormat.getIntegerInstance().format(totalDownloads) + " (" + formatPercent(((double) totalDownloads / globalDownloads)) + ")", false)
                .appendField("Project count", Integer.toString(mods.size()), false);
            
            StringBuilder top3 = new StringBuilder();
            mods.stream().sorted((m1, m2) -> Long.compare(m2.getDownloads(), m1.getDownloads())).limit(3)
                    .forEach(mod -> top3.append("[").append(mod.getName()).append("](").append(mod.getURL()).append(")").append(": ")
                                        .append(NumberFormat.getIntegerInstance().format(mod.getDownloads())).append('\n'));
            
            mainpg.appendField("Top 3", top3.toString(), false);
                
            msgbuilder.addPage(new BakedMessage().withEmbed(mainpg.build()));
            
            final int modsPerPage = 5;
            final int pages = (mods.size() / modsPerPage) + 1;
            for (int i = 0; i < pages; i++) {
                final EmbedBuilder page = new EmbedBuilder()
                        .withTitle(title)
                        .withDesc("Mods page " + (i + 1) + "/" + pages)
                        .withColor(color)
                        .withAuthorName(authorName)
                        .withAuthorIcon(authorIcon)
                        .withUrl("https://mods.curse.com/members/" + user)
                        .withTimestamp(LocalDateTime.now())
                        .withThumbnail(avatar);
                
                mods.stream().skip(modsPerPage * i).limit(modsPerPage).forEach(mod -> {
                    StringBuilder desc = new StringBuilder();

                    desc.append("[Link](" + mod.getURL() + ")\n");
                    
                    desc.append("Tags: ").append(Joiner.on(" | ").join(mod.getTags())).append("\n");

                    desc.append("Downloads: ")
                            .append(DecimalFormat.getIntegerInstance().format(mod.getDownloads()))
                            .append(" (").append(formatPercent((double) mod.getDownloads() / totalDownloads)).append(" of total) | ")
                            .append(shortNum(mod.getMdownloads())).append("/month\n");
                    
                    String role = mod.getModpage() == null ? "Error!" : mod.getModpage().getElementsByClass("authors").first().children().stream()
                                          .filter(el -> StringUtils.containsIgnoreCase(el.children().text(), username))
                                          .findFirst()
                                          .map(Element::ownText)
                                          .map(s -> s.trim().substring(0, s.indexOf(':')))
                                          .orElse("Unknown");
                    
                    page.appendField(mod.getName() + " | " + role + "", desc.toString(), false);
                });
                
                msgbuilder.addPage(new BakedMessage().withEmbed(page.build()));
            }

            waitMsg.delete();

        } catch (IOException e) {
            throw new CommandException(e);
        } finally {
            waitMsg.delete();
            ctx.getChannel().setTypingStatus(false);
        }
        
        msgbuilder.setParent(ctx.getMessage()).setProtected(false).build().send();

        
        System.out.println("Took: " + (System.currentTimeMillis()-time));
    }
    
    private String shortNum(long num) {
        NumberFormat fmt = DecimalFormat.getIntegerInstance();
        if (num < 1_000) { 
            return fmt.format(num);
        } else if (num < 1_000_000) {
            return fmt.format(num / 1_000) + "k";
        } else if (num < 1_000_000_000) {
            return fmt.format(num / 1_000_000) + "M";
        }
        return fmt.format(num / 1_000_000_000) + "B";
    }
    
    private Document getDocumentSafely(String url) throws IOException {
        Document ret = null;
        while (ret == null) {
            try {
                ret = Jsoup.connect(url).userAgent("Mozilla/5.0 (Windows NT 6.1; WOW64; rv:40.0) Gecko/20100101 Firefox/40.1").get();
            } catch (SocketTimeoutException e) {
                System.out.println("Caught timeout loading URL: " + url);
                System.out.println("Retrying in 5 seconds...");
                Threads.sleep(5000);
            } catch (IOException e) {
                throw e;
            }
        }
        return ret;
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
