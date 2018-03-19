package com.tterrag.k9.commands;

import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Random;

import com.tterrag.k9.commands.api.Argument;
import com.tterrag.k9.commands.api.Command;
import com.tterrag.k9.commands.api.CommandBase;
import com.tterrag.k9.commands.api.CommandContext;
import com.tterrag.k9.commands.api.CommandException;
import com.tterrag.k9.commands.api.Flag;

import lombok.extern.slf4j.Slf4j;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.util.EmbedBuilder;
import sx.blah.discord.util.RateLimitException;
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
	
    private static final Argument<String> ARG_FILEURL = new WordArgument("fileurl", "The files full URL", true);
	
	private static final Flag FLAG_USE_OPTIONAL = new SimpleFlag('o', "optional", "Also gets the gradle files for Optional Files", false);
	private static final Flag FLAG_USE_URL = new SimpleFlag('u', "url", "Outputs the urls instead of the gradle", false);
	private static final Flag FLAG_INFO = new SimpleFlag('i', "info", "Generates Info as the command is being processed", false);

	
	
	public CommandCurseGradle() {
		super("cfgradle", false);
	}
	
	@Override
	public void process(CommandContext ctx) throws CommandException {
		final String fileUrl = ctx.getArg(ARG_FILEURL);
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

					waitMsg.edit(out.build());
				}
			} catch (CommandException e) {
	            RequestBuffer.request(() -> ctx.reply("Could not process command: " + e)); //Default 
			} finally {
		        ctx.getChannel().setTypingStatus(false);
		        log.debug("Took: " + (System.currentTimeMillis()-time));
			}
		}, "Curseforge result for: " + fileUrl.split("/")[4]);
		
//		thread.setDaemon(true);
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
		String urlRead = readURL(url, true);
		
		downloadLibraries(urlRead, "Required Library", "Dependencies", waitMsg, ctx, list);
		downloadLibraries(urlRead, "Include", "Dependencies", waitMsg, ctx, list);		
		if(ctx.hasFlag(FLAG_USE_OPTIONAL)) {
			downloadLibraries(urlRead, "Optional Library", "Optional Library", waitMsg, ctx, list);
		}

		String mavenArtifiactRaw = urlRead.split("<div class=\"info-data overflow-tip\">")[1].split("</div>")[0];
		mavenArtifiactRaw = mavenArtifiactRaw.substring(0, mavenArtifiactRaw.length() - 4);
		if(!mavenArtifiactRaw.endsWith("-dev")) {
			String[] devValues = getMavenValues(projectSlug, mavenArtifiactRaw + "-dev");
			try
			{
				readURL("https://minecraft.curseforge.com/api/maven/" + String.join("/", projectSlug, devValues[0], devValues[1], mavenArtifiactRaw + "-dev") + ".jar", false);
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
	 */
	
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
	private void downloadLibraries(String urlRead, String splitterText, String guiDisplay, IMessage waitMsg, CommandContext ctx, ArrayList<CurseResult> list) throws CommandException {
		if(urlRead.contains("<h5>" + splitterText + "</h5>")) {
			String[] libList = urlRead.split("<h5>" + splitterText + "</h5>")[1].split("<ul>")[1].split("</ul>")[0].split("<li class=\"project-tag\">");
			int times = 1;
			for(String lib : libList) {
				if(lib.split("<a href=\"").length > 1) {
					editWaitMessage(waitMsg, "Resolving " + guiDisplay + " (" + times++ + "/" + (libList.length - 1) + ") - `" + lib.split("<div class=\"project-tag-name overflow-tip\">")[1].split("<span>")[1].split("</span>")[0] + "`", ctx);
					addLatestToList("https://minecraft.curseforge.com" + lib.split("<a href=\"")[1].split("\">")[0], urlRead.split("<h4>Supported Minecraft")[1].split("<ul>")[1].split("</ul>")[0].split("<li>")[1].split("</li>")[0], waitMsg, ctx, list, 0);
				}
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
	 * @param ctx The Command Context
	 * @param list A list to add the result to
	 * @param page The files page to track. If you're calling this, the page should be 0 or 1. 
	 * @throws CommandException 
	 */
	private void addLatestToList(String projectURL, String MCVersion, IMessage waitMsg, CommandContext ctx, ArrayList<CurseResult> list, int page) throws CommandException {
		String urlRead = readURL(projectURL + "/files?page=" + page, true);
		if(urlRead.split("<span class=\"b-pagination-item s-active active\">").length > 1 && Integer.valueOf(urlRead.split("<span class=\"b-pagination-item s-active active\">")[1].split("</span>")[0]) < page) {
			return;
		}
		String[] urlReadLibs = urlRead.split("<tr class=\"project-file-list-item\">");
		for(int i = 1; i < urlReadLibs.length; i++) {
			if(urlReadLibs[i].split("<span class=\"version-label\">")[1].split("</span>")[0].equals(MCVersion)) {
				calculate("https://minecraft.curseforge.com" + urlReadLibs[i].split("<a class=\"overflow-tip twitch-link\" href=\"")[1].split("\"")[0], waitMsg, ctx, list);
				return;
			}
		}
		addLatestToList(projectURL, MCVersion, waitMsg, ctx, list, page++);
	}
	
	/**
	 * Used to get the data a URL holds.
	 * @param url The url to uses
	 * @param simulate Should the program <u>actually</u> download the file
	 * @return the data that the url points to. Usally a webpage
	 * @throws CommandException 
	 */
	private String readURL(String url, boolean simulate) throws CommandException {
		try {
			InputStream urlStream = new URL(url).openStream();
			String urlRead = "";
			int len = urlStream.read();
			if(simulate) {
				while(len != -1) {
					urlRead += (char)len;
					len = urlStream.read();
				}
			}
			return urlRead;
		} catch (IOException e) {
			throw new CommandException(e);
		}
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
		try {
			RequestBuffer.request(() -> waitMsg.edit(newText));
		} catch (RateLimitException e) {
			log.error("Unable to change " + waitMsg.getContent() + " to " + newText + e.getMessage());
		}
	}

	@Override
	public String getDescription() {
		return "Displays the correct gradle infomation for a file on the curseforge maven (https://minecraft.curseforge.com/api/maven). Takes into account dependencies";
	}

}
