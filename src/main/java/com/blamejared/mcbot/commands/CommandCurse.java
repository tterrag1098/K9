package com.blamejared.mcbot.commands;

import java.awt.Color;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Optional;
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
import com.blamejared.mcbot.commands.api.Flag;
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
        SortStrategy sort;

        @Override
        public int compareTo(@SuppressWarnings("null") ModInfo o) {
            return sort.compare(this, o);
        }
    }
    
    private enum SortStrategy implements Comparator<ModInfo> {
        ALPHABETICAL {
            
            @Override
            public int compare(ModInfo o1, ModInfo o2) {
                return o1.getName().compareTo(o2.getName());
            }
        },
        
        DOWNLOADS {
            @Override
            public int compare(ModInfo o1, ModInfo o2) {
                return Long.compare(o2.getDownloads(), o1.getDownloads());
            }
        }
    }

    private static final Argument<String> ARG_USERNAME = new WordArgument("username", "The curse username of the mod author.", true);
    
    private static final Flag FLAG_MINI = new SimpleFlag("m", "Only produces the first page, for fast (but cursory) results.", false) {
        public String longFormName() { return "mini"; }
    };
    
    private static final Flag FLAG_SORT = new SimpleFlag("s", "Controls the sorting of mods. Possible values: a[lphabetical], d[ownloads]", true) {
        public String longFormName() { return "sort"; }
    };
    
    public CommandCurse() {
        super("curse", false, Lists.newArrayList(FLAG_MINI, FLAG_SORT), Lists.newArrayList(ARG_USERNAME));
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
        
        IMessage waitMsg = ctx.hasFlag(FLAG_MINI) ? null : ctx.reply("Please wait, this may take a while...");
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
            
            SortStrategy sort = Optional.ofNullable(ctx.getFlag(FLAG_SORT)).map(s -> {
                for (SortStrategy strat : SortStrategy.values()) {
                    if ((s.length() == 1 && Character.toUpperCase(s.charAt(0)) == strat.name().charAt(0)) || strat.name().equalsIgnoreCase(s)) {
                        return strat;
                    }
                }
                return null;
            }).orElse(SortStrategy.ALPHABETICAL);

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
                    
                    if (ctx.hasFlag(FLAG_MINI)) {
                        mods.add(new ModInfo(mod, url, tags, 0, 0, null, sort));
                    } else {
                        try {
                            Document modpage = getDocumentSafely("https://mods.curse.com" + url);

                            long mdownloads = Long.parseLong(modpage.getElementsByClass("average-downloads").first().text().replaceAll("(Monthly Downloads|,)", "").trim());
                            long downloads = Long.parseLong(modpage.getElementsByClass("downloads").first().text().replaceAll("(Total Downloads|,)", "").trim());
                            url = "http://mods.curse.com" + url.replaceAll(" ", "-");

                            mods.add(new ModInfo(mod, url, tags, mdownloads, downloads, modpage, sort));
                        } catch (IOException e) {
                            e.printStackTrace();
                            mods.add(new ModInfo(mod, url, tags, 0, 0, null, sort));
                        }
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
            long globalDownloads = 0;
            
            if (!ctx.hasFlag(FLAG_MINI)) {
                globalDownloads = getDocumentSafely("https://minecraft.curseforge.com/projects").getElementsByClass("category-info").stream()
                        .filter(e -> e.child(0).child(0).text().equals("Mods"))
                        .findFirst()
                        .map(e -> e.getElementsByTag("p").first().text())
                        .map(s -> s.substring(s.lastIndexOf("more than"), s.lastIndexOf("downloads"))) // trim out the relevant part of the string
                        .map(s -> s.replaceAll("(more than|,)", "").trim()) // delete excess characters
                        .map(Long::parseLong)
                        .orElseThrow(() -> new CommandException("Could not load global downloads"));
            }
            
            long totalDownloads = mods.stream().mapToLong(ModInfo::getDownloads).sum();
            
            EmbedBuilder mainpg = new EmbedBuilder()
                .withTitle(title)
                .withColor(color)
                .withAuthorName(authorName)
                .withAuthorIcon(authorIcon)
                .withUrl("https://mods.curse.com/members/" + user)
                .withThumbnail(avatar)
                .withTimestamp(LocalDateTime.now())
                .withFooterText("Info provided by Curse/CurseForge");
            
            if (!ctx.hasFlag(FLAG_MINI)) {
                mainpg.appendField("Total downloads", NumberFormat.getIntegerInstance().format(totalDownloads) + " (" + formatPercent(((double) totalDownloads / globalDownloads)) + ")", false)
                        .withDesc("Main page");
            }
                
            mainpg.appendField("Project count", Integer.toString(mods.size()), false);

            
            if (ctx.hasFlag(FLAG_MINI)) {
                
                StringBuilder top3 = new StringBuilder();
                mods.stream().limit(3).forEach(mod -> top3.append("[").append(mod.getName()).append("](").append(mod.getURL()).append(")").append('\n'));
                
                mainpg.appendField("First 3", top3.toString(), false);
                
                ctx.reply(mainpg.build());
            } else {
                StringBuilder top3 = new StringBuilder();
                mods.stream().sorted(SortStrategy.DOWNLOADS).limit(3)
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
                msgbuilder.setParent(ctx.getMessage()).setProtected(false).build().send();
            }

        } catch (IOException e) {
            throw new CommandException(e);
        } finally {
            if (waitMsg != null) waitMsg.delete();
            ctx.getChannel().setTypingStatus(false);
        }
        
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
