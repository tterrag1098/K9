<img src="https://i.imgur.com/xjPT4WB.png" width="100px" height="100px"></img>
# K9 [![Build Status](https://ci.tterrag.com/buildStatus/icon?job=K9)](https://ci.tterrag.com/job/K9)

A Discord bot with some useful commands.

It uses [Discord4J](https://discord4j.com/), an awesome Java library for the Discord API.

## About

K9 was originally created for a server about Minecraft modding, and its first command was to look up MCP mappings. It still has this feature, but there is now much more!

- Custom pings for arbitrary regex (!ping)
	- Ever wanted to be pinged for something other than your username? Want to make sure to catch every time someone says your name, because you're a creepy bastard with nothing better to do? Then this is the command for you!
- A command to manage "information" channels
	- Allows any administrator to update the content of an information channel from an outside source.
- A Clojure REPL emulator (!clj)
	- If you know what this means, then you're probably a nerd.
- The ability to create custom commands (called "tricks") which can process user input and execute code (!trick)
    - I don't have a funny quip for this one, but trust me, it's neat!
- Look up CurseForge projects and download counts for any username (!cf)
    - You're so vain, I bet you'll run this command on yourself.
- Lets you keep track of the best quotes from your server, and even battle them together head-to-head! (!quote)
    - Because everything is funnier out of context.
- The ability to change the command prefix
    - What do you mean other bots already use an exclamation mark?
- Create drama (!drama)
- Slap people (!slap)
- More to come!

It also features a complete help system, so figuring out how to use a command is as simple as `!help [command]`.

## How do I get K9 in my server already?

Well, if you're lucky enough to know me, just ask. Currently the instance of K9 I run is private, so only I can invite it to servers I personally know the owners of. However, K9 is open source and the build server I use is public, so it's easy to set up your own instance! See the next section for a how-to.

## So you want to run your own K9

Great! Before doing anything, make sure you have the latest version of Java 8 installed on the machine that will be running the bot.

1. Download the bot from [Jenkins](https://ci.tterrag.com/job/K9/). Make sure to grab the -all jar, it includes all the libraries needed to run K9 inside it.
2. Create a [Discord App](https://discordapp.com/developers/applications/me). Give it an appropriate name, and make sure to click the "Create a Bot User" button. After that, make sure to copy the bot token, you'll need it for the next step!
3. That's all the setup! Make sure the bot jar is inside a clean directory (it will create some folders for storing data), and then you can run it with the syntax: `java -jar [jar name] -a [bot token]`. You might want to do this inside a `screen` or similar.

And that's all! You should see a lot of console output, and then the bot will be running! To invite it to a server, use the "Generate OAuth2 URL" button on the app page. What permissions you give the bot is up to you, but it does not (currently) need any more than these:

![](https://i.imgur.com/JINS5mk.png)

## I know this sounds impossible, but I think K9 can be better. How can I help?

See the [CONTRIBUTING.md](https://github.com/tterrag1098/K9/blob/master/CONTRIBUTING.md) file.
