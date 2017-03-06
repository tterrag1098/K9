package com.blamejared.mcbot.commands;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Scanner;
import java.util.stream.Collectors;

import com.blamejared.mcbot.commands.api.Command;
import com.blamejared.mcbot.commands.api.CommandBase;
import com.blamejared.mcbot.commands.api.CommandException;
import com.blamejared.mcbot.commands.api.ICommand;
import com.blamejared.mcbot.srg.ISrgMapping;
import com.blamejared.mcbot.srg.SrgDownloader;
import com.blamejared.mcbot.srg.ISrgMapping.MappingType;

import lombok.RequiredArgsConstructor;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.util.EmbedBuilder;

@Command
public class CommandMCP extends CommandBase {
    
    @RequiredArgsConstructor
    enum LookupType {
        FIELD('f'),
        METHOD('m'),
        CLASS('c'),
        ;
        
        private final char key;
    }
    
    private final LookupType type;
    private final Random rand = new Random();
    
    public CommandMCP() {
        this(null);
    }
    
    private CommandMCP(LookupType type) {
        super("mcp" + (type == null ? "" : type.key), false);
        this.type = type;
    }
    
    @Override
    public boolean isTransient() {
        return type == null;
    }
    
    @Override
    public Iterable<ICommand> getChildren() {
        if (isTransient()) {
            return Arrays.stream(LookupType.values()).map(CommandMCP::new).collect(Collectors.toList());
        }
        return super.getChildren();
    }
    
    @Override
    public void process(IMessage message, List<String> flags, List<String> args) throws CommandException {
        if (type == LookupType.CLASS) {
            ISrgMapping classmapping = SrgDownloader.INSTANCE.lookup(MappingType.CLASS, args.get(0), "1.11");
            if (classmapping != null) {
                message.getChannel().sendMessage(classmapping.toString());
            } else {
                message.getChannel().sendMessage("No class found.");
            }
            return;
        }

        File f = this.type == LookupType.FIELD ? new File("fields.csv") : new File("methods.csv"); 
        try {
            if(!f.exists()) {
                f.createNewFile();
            }
            String fieldName = message.getContent().split(" ")[1];
            Scanner scan = new Scanner(f);
            scan.useDelimiter("\n");
            StringBuilder builder = new StringBuilder();
            final EmbedBuilder embed = new EmbedBuilder();
            final boolean[] found = {false};
            scan.forEachRemaining(line -> {
                System.out.println(line);
                String[] info = line.split(",", -1);
                if(info[0].equalsIgnoreCase(fieldName) || line.split(",")[1].equalsIgnoreCase(fieldName)) {
                    if (found[0]) {
                        builder.append("\n");
                    }
                    found[0] = true;
                    builder.append("SRG Name: " + info[0] + "\n");
                    builder.append("Forge Name: " + info[1] + "\n");
                    if(!info[3].trim().isEmpty()) {
                        builder.append("Desc: " + info[3] + "\n");
                    }
                    builder.append("Side: " + (info[2].trim().equals("0") ? " Client" : info[2].trim().equals("1") ? " Server" : " Both") + "\n");
                }
            });
            scan.close();
            if(!found[0]) {
                builder.append("No information found!");
            }
            System.out.println(builder.toString());
            embed.ignoreNullEmptyFields();
            embed.withDesc(builder.toString());
            rand.setSeed(builder.toString().hashCode());
            embed.withColor(Color.HSBtoRGB(rand.nextFloat(), 1, 1));
            embed.withTitle("Information on " + type + ": " + fieldName);
            message.getChannel().sendMessage(embed.build());
            scan.close();
        } catch(IOException e) {
            throw new CommandException(e);
        }
    }
    
    @Override
    public String getUsage() {
        return "!mcpf for a field / !mcpm for a method";
    }
}
