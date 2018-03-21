package com.tterrag.k9.commands;

import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Random;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.tterrag.k9.commands.api.Argument;
import com.tterrag.k9.commands.api.Command;
import com.tterrag.k9.commands.api.CommandBase;
import com.tterrag.k9.commands.api.CommandContext;
import com.tterrag.k9.commands.api.CommandException;
import com.tterrag.k9.commands.api.Flag;
import com.tterrag.k9.util.Threads;

import lombok.extern.slf4j.Slf4j;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.util.EmbedBuilder;
import sx.blah.discord.util.RequestBuffer;

/**
 * Command to figure out curseforges gradle and dependencies.
 * @author Wyn Price
 *
 */
@Command
@Slf4j
public class CommandCurseGradle extends CommandBase {
    
    private class CurseResult 
    {
        private final String URL;
        private final String gradle;
        
        public CurseResult(String URL, String gradle) 
        {
            this.URL = URL;
            this.gradle = gradle;
        }
        
        public String getGradle() {
            return gradle;
        }
        
        public String getURL() {
            return URL;
        }
        
        @Override
        public String toString() {
            return "URL:" + URL + "\nGRADLE: " + gradle + "\n";
        }
    }
    
    private static final Argument<String> ARG_FILEURL = new WordArgument("fileurl", "The URL of the CurseForge project to generate a dependency list for.", true);
    
    private static final Flag FLAG_USE_OPTIONAL = new SimpleFlag('o', "optional", "Append optional project dependencies to the output list.", false);
    private static final Flag FLAG_USE_URL = new SimpleFlag('u', "url", "Output dependency URLs directly, with no Gradle structure.", false);
    private static final Flag FLAG_INFO = new SimpleFlag('i', "info", "Gives info as the command is being processed", false);

    
    
    public CommandCurseGradle() {
        super("cfgradle", false);
    }
    
    @Override
    public void process(CommandContext ctx) throws CommandException {
        final String fileUrl = ctx.getArg(ARG_FILEURL);
        if(fileUrl.equalsIgnoreCase("maven")) {
            ctx.reply("Add this to your `build.gradle`\n```gradle\nmaven {\n    name = \"curseforge maven\"\n    url \"https://minecraft.curseforge.com/api/maven\"\n}\n```");
            return;
        }
        String threadURL = fileUrl;
        if(fileUrl.split("/").length == 7) {
        	threadURL = fileUrl.split("/")[4];
        }
        Thread thread = new Thread(() -> {
            long time = System.currentTimeMillis();
            ctx.getChannel().setTypingStatus(true);
            IMessage waitMsg = ctx.reply("Please wait, this may take a while...");            
            try {
                ArrayList<CurseResult> resultList = calculate(fileUrl, waitMsg, ctx, new ArrayList<>());
                if(!resultList.isEmpty()) {
                    String urlOutput = "";
                    String forgeGradleOutput = "";
                    for(CurseResult result : resultList) {
                        urlOutput += result.getURL() + "\n";
                        forgeGradleOutput += "deobfCompile \"" + result.getGradle() + "\"\n";
                    }
                    EmbedBuilder out = new EmbedBuilder()
                            .withTitle("Gradle for " + fileUrl.split("/")[4])
                            .withColor(Color.HSBtoRGB(new Random(fileUrl.split("/")[6].hashCode()).nextFloat(), 1, 1))
                            .withTimestamp(LocalDateTime.now())
                            .withFooterText("Info provided by CurseForge");
                    
                    if(ctx.hasFlag(FLAG_USE_URL)) {
                        out.appendField("URL", urlOutput, false);
                    } else {
                        out.appendField("Gradle", forgeGradleOutput, false);
                    }
                    
                    RequestBuffer.request(() -> waitMsg.edit(out.build()));
                }
            } catch (CommandException e) {
                RequestBuffer.request(() -> ctx.reply("Could not process command: " + e)); //Default 
            } finally {
                ctx.getChannel().setTypingStatus(false);
                log.debug("Took: " + (System.currentTimeMillis()-time));
            }
        }, "Curseforge result for: " + threadURL);
        
        thread.start();
        
    }
    
    /**
     * Used to calculate the gradle and downloads from a url
     * @param url The URL
     * @param waitMsg The message to use for infomation
     * @param ctx The Command Context
     * @param list The list of current
     * @return {@code list}
     * @throws CommandException 
     */
    public ArrayList<CurseResult> calculate(String url, IMessage waitMsg, CommandContext ctx, ArrayList<CurseResult> list) throws CommandException
    {
        for(CurseResult result : list) {
            if(result.getGradle().split(":")[0].equalsIgnoreCase(url.split("/")[4])) {
                return list;
            }
        }
        String[] splitUrl = url.split("/");
        if(splitUrl.length != 7 || !splitUrl[0].equals("https:") || !splitUrl[2].equals("minecraft.curseforge.com") || !splitUrl[3].equals("projects") || !splitUrl[5].equals("files") || !splitUrl[6].matches("\\d+")) {
            if(url.length() > 40) {
                url = url.substring(0, 20) + "..." + url.substring(url.length() - 20, url.length());
            }
            editWaitMessage(waitMsg, "Invalid URL: " + url + "\nFormat: `https://minecraft.curseforge.com/projects/examplemod/files/12345`", ctx);
            return list;
        }
        
        editWaitMessage(waitMsg, "Resolving File - `" + splitUrl[4] + "`", ctx);
        
        String projectSlug = splitUrl[4];
        Document urlRead = getDocument(url);
        downloadLibraries(urlRead, "Required Library", "Dependencies", waitMsg, ctx, list);
        downloadLibraries(urlRead, "Include", "Dependencies", waitMsg, ctx, list);        
        if(ctx.hasFlag(FLAG_USE_OPTIONAL)) {
            downloadLibraries(urlRead, "Optional Library", "Optional Library", waitMsg, ctx, list);
        }

        String mavenArtifiactRaw = urlRead.select("div.info-data").get(0).html();
        mavenArtifiactRaw = mavenArtifiactRaw.substring(0, mavenArtifiactRaw.length() - 4);
        if(!mavenArtifiactRaw.endsWith("-dev")) {
            String[] devValues = getMavenValues(projectSlug, mavenArtifiactRaw + "-dev");
            try
            {
                getDocument("https://minecraft.curseforge.com/api/maven/" + String.join("/", projectSlug, devValues[0], devValues[1], mavenArtifiactRaw + "-dev") + ".jar");
                editWaitMessage(waitMsg, "Resolving File - `" + splitUrl[4] + "` - Dev Version", ctx);
                mavenArtifiactRaw += "-dev";
            }
            catch (Exception e) 
            {
                //Dont worry if exception is thrown here, this is just to check if the dev version exists. If it dosnt then just continue with the normal version
            }
        }
        
        String[] values = getMavenValues(projectSlug, mavenArtifiactRaw);
        list.add(new CurseResult(
                "https://minecraft.curseforge.com/api/maven/" + String.join("/", projectSlug, values[0], values[1], mavenArtifiactRaw) + ".jar",
                projectSlug + ":" + String.join(":", values[0], values[1]) + (values[2].isEmpty() ? "" : ":") + values[2]));
        return list;
    }
    
    /**
     * Gets the Related Project files and downloades them
     * @param urlRead The read URL
     * @param splitterText the Name of the project type (Required Library, Embedded Library, stuff like that). <b>THIS MUST BE WHAT IS DISPLAYED ON CURSEFORGE</b>
     * @param guiDisplay The text to display to the GUI
     * @param waitMsg The message to use for infomation
     * @param ctx The Command Context
     * @param list The list of results
     * @throws CommandException 
     */
    private void downloadLibraries(Document urlRead, String splitterText, String guiDisplay, IMessage waitMsg, CommandContext ctx, ArrayList<CurseResult> list) throws CommandException {
    	Elements outerElements = urlRead.select("h5:containsOwn(" + splitterText + ")");
    	if(!outerElements.isEmpty()) {
    		Elements elementList = urlRead.select("h5:containsOwn(" + splitterText + ") + ul").select("a");
    		int times = 1;
    		String mcVersion = urlRead.select("h4:containsOwn(Supported Minecraft) + ul").select("li").html().split("\n")[0];
    		for(Element element : elementList) {
    			addLatestToList("https://minecraft.curseforge.com" + element.attr("href"), mcVersion, waitMsg, guiDisplay + " (" + times++ + "/" + elementList.size()  + ") - `" + element.select("div.project-tag-name").select("span").html() + "`", ctx, list, 0);
    		}
    	}
    }
    
    /**
     * Gets the maven values from the file name and projectSlug
     * <br>{
     * <br>&nbsp&nbsp&nbspmavenArtifiact,
     * <br>&nbsp&nbsp&nbspversion,
     * <br>&nbsp&nbsp&nbspmavenClassifier
     * <br>}
     */
    private String[] getMavenValues(String projectSlug, String fileName) {
        String[] splitArtifiact = fileName.split("-");
        String version = splitArtifiact[splitArtifiact.length - 2];
        String[] splitArtifiactNonVersion = new String[splitArtifiact.length - 2];
        for(int i = 0; i < splitArtifiact.length; i++) {
            if(i < splitArtifiact.length - 2) {
                splitArtifiactNonVersion[i] = splitArtifiact[i];
            }
        }
                
        String mavenArtifiact = String.join("-", splitArtifiactNonVersion);
        if(splitArtifiact.length == 2) {
            mavenArtifiact = splitArtifiact[0];
            version = splitArtifiact[1];
        }
        
        String mavenClassifier = "";
        for(String artifact : fileName.split("-")) {
            if(!artifact.equals(version) && !mavenArtifiact.contains(artifact)) {
                mavenClassifier += artifact + ":";
            }
        }
        if(mavenClassifier.length() > 0) {
            mavenClassifier = mavenClassifier.substring(0, mavenClassifier.length() - 1);
        }
        return new String[] {mavenArtifiact, version, mavenClassifier};
    }

    /**
     * Used to get the latest file from a curseforge project, of a particular minecraft version, then add it to the {@code list}
     * @param projectURL The projects url page. This should be the homepage, for example {@link https://minecraft.curseforge.com/projects/secretroomsmod}
     * @param MCVersion The minecraft version to use to get the latest version
     * @param waitMsg The message to use for infomation
     * @param guiMessage The message used for the GUI
     * @param ctx The Command Context
     * @param list A list to add the result to
     * @param page The files page to track. If you're calling this, the page should be 0 or 1. 
     * @throws CommandException 
     */
    private void addLatestToList(String projectURL, String MCVersion, IMessage waitMsg, String guiMessage, CommandContext ctx, ArrayList<CurseResult> list, int page) throws CommandException {
        if(page == 0) {
            page = 1;
        }
        if(projectURL.split("/")[4].matches("\\d+")) {
            System.out.println("transforming " + projectURL);
            try {
                URLConnection con = new URL(projectURL).openConnection(); //Used to convert the project id to project slug
                con.connect();
                InputStream is = con.getInputStream();
                projectURL = con.getURL().toString();
                is.close();
            } catch (IOException e) {
                new CommandException(e);
            }
        }
        
        
        editWaitMessage(waitMsg, guiMessage + ". Page " + page, ctx);
        
        Document urlRead = getDocument(projectURL + "/files?page=" + page);
        Elements pageElement = urlRead.select("span.b-pagination-item").select("span.s-active");
        if(!pageElement.isEmpty()) {
            try {
                if(Integer.valueOf(pageElement.html()) < page) {
                    return;
                }
            } catch (NumberFormatException e) {
                log.error("Enable to parse value of {} to an int. Returning on page {}", pageElement.html(), page);
            }
        }

        Elements libElements = urlRead.select("tr.project-file-list-item");

        for(Element element : libElements) {
            boolean isCorrectVersion = MCVersion.equalsIgnoreCase(element.select("span.version-label").html()); 
            if(!isCorrectVersion) {
                for(String version : element.select("span.additional-versions").attr("title").replace("<div>", "").split("</div>")) { //Get the list of extra hidden versions
                    if(MCVersion.equalsIgnoreCase(version)) {
                        isCorrectVersion = true;
                        break;
                    }
                }

            }
            if(isCorrectVersion) {
                calculate("https://minecraft.curseforge.com" + element.select("a.twitch-link").attr("href"), waitMsg, ctx, list);
                return;
            }
        }
        
        addLatestToList(projectURL, MCVersion, waitMsg, guiMessage, ctx, list, page+=1);
    }
    
    private Document getDocument(String url) throws CommandException {
        Document ret = null;
        while (ret == null) {
            try {
                ret = Jsoup.connect(url).userAgent("Mozilla/5.0 (Windows NT 6.1; WOW64; rv:40.0) Gecko/20100101 Firefox/40.1").get();
            } catch (SocketTimeoutException e) {
                log.info("Caught timeout loading URL: " + url);
                log.info("Retrying in 5 seconds...");
                Threads.sleep(5000);
            } catch (IOException e) {
                throw new CommandException(e);
            }
        }
        return ret;
    }
    
    /**
     * Attempt to edit the wait message, if "-i" has been set
     * @param waitMsg The wait message
     * @param newText New text to put
     * @param ctx the context for the message
     */
    private void editWaitMessage(IMessage waitMsg, String newText, CommandContext ctx) {
        if(!ctx.hasFlag(FLAG_INFO)) {
            return;
        }
        RequestBuffer.request(() -> waitMsg.edit(newText));

    }
    @Override
    public String getDescription() {
        return "Generates a list of all Gradle dependencies for the given CurseForge project.";
    }

}
