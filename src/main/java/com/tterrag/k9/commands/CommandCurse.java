package com.tterrag.k9.commands;

import java.awt.Color;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.text.NumberFormat;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import com.google.common.base.Joiner;
import com.tterrag.k9.commands.api.Argument;
import com.tterrag.k9.commands.api.Command;
import com.tterrag.k9.commands.api.CommandBase;
import com.tterrag.k9.commands.api.CommandContext;
import com.tterrag.k9.commands.api.CommandContext.TypingStatus;
import com.tterrag.k9.commands.api.CommandException;
import com.tterrag.k9.commands.api.Flag;
import com.tterrag.k9.util.BakedMessage;
import com.tterrag.k9.util.DefaultNonNull;
import com.tterrag.k9.util.EmbedCreator;
import com.tterrag.k9.util.NonNull;
import com.tterrag.k9.util.NullHelper;
import com.tterrag.k9.util.Nullable;
import com.tterrag.k9.util.PaginatedMessageFactory;
import com.tterrag.k9.util.Threads;

import discord4j.core.object.entity.Message;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Command
public class CommandCurse extends CommandBase {
    
    @Value
    @DefaultNonNull
    private static final class ModInfo implements Comparable<ModInfo> {
        String name;
        String shortDesc;
        String URL;
        String[] tags;
        long downloads;
        String role;
        SortStrategy sort;

        @Override
        public int compareTo(@Nullable ModInfo o) {
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
    
    private static final Flag FLAG_MINI = new SimpleFlag('m', "mini", "Only produces the first page, for fast (but cursory) results.", false);
    private static final Flag FLAG_SORT = new SimpleFlag('s', "sort", "Controls the sorting of mods. Possible values: a[lphabetical], d[ownloads]", true);
    
    public CommandCurse() {
        super("cf", false);
    }
    
    private final Random rand = new Random();

    @Override
    public Mono<?> process(CommandContext ctx) throws CommandException {
        long time = System.currentTimeMillis();
        String user = ctx.getArg(ARG_USERNAME);
        
        rand.setSeed(user.hashCode());
        int color = Color.HSBtoRGB(rand.nextFloat(), 1, 1);
        
        String authorName = ctx.getMessage().getAuthor().flatMap(u -> ctx.getGuild().flatMap(g -> u.asMember(g.getId()))).block().getDisplayName() + " requested";
        String authorIcon = ctx.getMessage().getAuthor().block().getAvatarUrl();
        
        Message waitMsg = ctx.hasFlag(FLAG_MINI) ? null : ctx.reply("Please wait, this may take a while...").block();

        PaginatedMessageFactory.Builder msgbuilder = PaginatedMessageFactory.INSTANCE.builder(ctx.getChannel().block());
        Mono<?> res;

        try(TypingStatus typing = ctx.setTyping()) {

            Document doc;
            try {
                doc = getDocumentSafely(String.format("https://minecraft.curseforge.com/members/%s/projects", user));
            } catch (HttpStatusException e) {
                if (e.getStatusCode() / 100 == 4) {
                    throw new CommandException("User " + user + " does not exist.");
                }
                throw e;
            }
            
            String username = doc.getElementsByClass("username").first().text();
            String avatar = doc.getElementsByClass("avatar").first().child(0).child(0).attr("src");

            String title = "Information on: " + username;

            @NonNull
            SortStrategy sort = NullHelper.notnullJ(Optional.ofNullable(ctx.getFlag(FLAG_SORT)).map(s -> {
                for (SortStrategy strat : SortStrategy.values()) {
                    if ((s.length() == 1 && Character.toUpperCase(s.charAt(0)) == strat.name().charAt(0)) || strat.name().equalsIgnoreCase(s)) {
                        return strat;
                    }
                }
                return null;
            }).orElse(SortStrategy.ALPHABETICAL), "Optional#orElse");

            Set<ModInfo> mods = new TreeSet<>();
            Element nextPageButton = null;
            // Always run first page
            do {
                // After first page
                if (nextPageButton != null) {
                    doc = getDocumentSafely("https://minecraft.curseforge.com" + nextPageButton.attr("href"));
                }

                // Get the projects ul, iterate over li.details
                doc.getElementById("projects").children().stream().map(e -> e.child(1)).forEach(ele -> {
                    ctx.setTyping(); // make sure this stays active

                    // Grab the actual <a> for the mod
                    Element name = ele.child(0).child(0).child(0);
                    
                    // Mod name is the text, mod URL is the link target
                    @NonNull
                    String mod = NullHelper.notnullL(name.text(), "Element#text");
                    @NonNull
                    String url = NullHelper.notnullL(name.attr("href"), "Element#attr");
                    
                    Element categories = ele.child(2).child(0);
                    
                    // Navigate up to <dl> and grab second child, which is the <dd> with tags, get all <a> tags from them
                    @NonNull
                    String[] tags = NullHelper.notnullJ(categories.children().stream().map(e -> e.child(0).attr("title")).toArray(String[]::new), "Stream#toArray");
                    
                    @NonNull
                    String shortDesc = NullHelper.notnullL(ele.child(3).text(), "Element#text");
                    
                    if (ctx.hasFlag(FLAG_MINI)) {
                        mods.add(new ModInfo(mod, shortDesc, url, tags, 0, "Unknown", sort));
                    } else {
                        try {
                            Document modpage = getDocumentSafely("https://minecraft.curseforge.com" + url);

                            long downloads = Long.parseLong(modpage.select("ul.cf-details.project-details").first().child(3).child(1).text().replace(",", "").trim());
                            url = "http://minecraft.curseforge.com" + url.replaceAll(" ", "-");
                            
                            @NonNull
                            String role = NullHelper.notnullJ(modpage.select("li.user-tag-large .info-wrapper p").stream()
                                    .filter(e -> e.child(0).text().equals(user))
                                    .findFirst()
                                    .map(e -> e.child(1).text())
                                    .orElse("Unknown"), "Optional#orElse");

                            mods.add(new ModInfo(mod, shortDesc, url, tags, downloads, role, sort));
                        } catch (IOException e) {
                            log.error("Could not load mod data: ", e);
                            mods.add(new ModInfo(mod, shortDesc, url, tags, 0, "Unknown", sort));
                        }
                    }
                });

                // Try to find the next page button
                nextPageButton = doc.select(".b-pagination-item a").last();
                if (nextPageButton != null && !nextPageButton.attr("rel").equals("next")) {
                    nextPageButton = null;
                }

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
            
            EmbedCreator.Builder mainpg = EmbedCreator.builder()
                .title(title)
                .color(color)
                .authorName(authorName)
                .authorIcon(authorIcon)
                .authorUrl("https://minecraft.curseforge.com/members/" + user)
                .thumbnail(avatar)
                .timestamp(Instant.now())
                .footerText("Info provided by CurseForge");
            
            if (!ctx.hasFlag(FLAG_MINI)) {
                mainpg.field("Total downloads", NumberFormat.getIntegerInstance().format(totalDownloads) + " (" + formatPercent(((double) totalDownloads / globalDownloads)) + ")", false)
                      .description("Main page");
            }
                
            mainpg.field("Project count", Integer.toString(mods.size()), false);

            if (ctx.hasFlag(FLAG_MINI)) {
                
                StringBuilder top3 = new StringBuilder();
                mods.stream().limit(3).forEach(mod -> top3.append("[").append(mod.getName()).append("](").append(mod.getURL()).append(")").append('\n'));
                
                mainpg.field("First 3", top3.toString(), false);
                
                res = ctx.reply(mainpg.build());
            } else {
                StringBuilder top3 = new StringBuilder();
                mods.stream().sorted(SortStrategy.DOWNLOADS).limit(3)
                        .forEach(mod -> top3.append("[").append(mod.getName()).append("](").append(mod.getURL()).append(")").append(": ")
                                            .append(NumberFormat.getIntegerInstance().format(mod.getDownloads())).append('\n'));
                
                mainpg.field("Top 3", top3.toString(), false);
                
                msgbuilder.addPage(new BakedMessage().withEmbed(mainpg));
                
                final int modsPerPage = 5;
                final int pages = ((mods.size() - 1) / modsPerPage) + 1;
                for (int i = 0; i < pages; i++) {
                    final EmbedCreator.Builder page = EmbedCreator.builder()
                            .title(title)
                            .description("Mods page " + (i + 1) + "/" + pages)
                            .color(color)
                            .authorName(authorName)
                            .authorIcon(authorIcon)
                            .authorUrl("https://minecraft.curseforge.com/members/" + user)
                            .timestamp(Instant.now())
                            .thumbnail(avatar);
                    
                    mods.stream().skip(modsPerPage * i).limit(modsPerPage).forEach(mod -> {
                        StringBuilder desc = new StringBuilder();
    
                        desc.append("[" + mod.getShortDesc() + "](" + mod.getURL() + ")\n");
                        
                        desc.append("Tags: ").append(Joiner.on(" | ").join(mod.getTags())).append("\n");
    
                        desc.append("Downloads: ")
                                .append(NumberFormat.getIntegerInstance().format(mod.getDownloads()))
                                .append(" (").append(formatPercent((double) mod.getDownloads() / totalDownloads)).append(" of total)");
                        
                        page.field(mod.getName() + " | " + mod.getRole() + "", desc.toString(), false);
                    });
                    
                    msgbuilder.addPage(new BakedMessage().withEmbed(page));
                }
    
                if (waitMsg != null) {
                    waitMsg.delete().subscribe();
                }
                res = msgbuilder.setParent(ctx.getMessage()).setProtected(false).build().send();
            }

        } catch (IOException e) {
            throw new CommandException(e);
        } finally {
            if (waitMsg != null) { 
                waitMsg.delete();
            }
        }
        
        log.debug("Took: " + (System.currentTimeMillis()-time));
        return res;
    }
    
    private Document getDocumentSafely(String url) throws IOException {
        Document ret = null;
        while (ret == null) {
            try {
                ret = Jsoup.connect(url).userAgent("Mozilla/5.0 (Windows NT 6.1; WOW64; rv:40.0) Gecko/20100101 Firefox/40.1").get();
            } catch (SocketTimeoutException e) {
                log.info("Caught timeout loading URL: " + url);
                log.info("Retrying in 5 seconds...");
                Threads.sleep(5000);
            } catch (IOException e) {
                throw e;
            }
        }
        return ret;
    }
    
    private final String formatPercent(double pct) {
        NumberFormat pctFmt = NumberFormat.getPercentInstance();
        pctFmt.setMaximumFractionDigits(pct >= 0.1 ? 0 : pct >= 0.01 ? 1 : 4);
        return pctFmt.format(pct);
    }
    
    @Override
    public String getDescription() {
        return "Displays download counts for all of a modder's curse projects.";
    }
    
}
