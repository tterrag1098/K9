package com.blamejared.mcbot.commands;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;

import com.blamejared.mcbot.commands.api.Command;
import com.blamejared.mcbot.commands.api.CommandBase;
import com.blamejared.mcbot.commands.api.CommandException;
import com.blamejared.mcbot.commands.api.ICommand;

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
        switch (this.type) {
            case FIELD:
                try {
                    File f = new File("fields.csv");
                    if(!f.exists()) {
                        f.createNewFile();
                    }
                    String fieldName = message.getContent().split(" ")[1];
                    Scanner scan = new Scanner(f).useDelimiter("\n");
                    StringBuilder builder = new StringBuilder();
                    final EmbedBuilder embed = new EmbedBuilder();
                    final boolean[] found = {false};
                    scan.forEachRemaining(line -> {
                        System.out.println(line.split(",")[0]);
                        if(line.split(",")[0].equalsIgnoreCase(fieldName)) {
                            found[0] = true;
                            String[] info = line.split(",");
                            builder.append("SRG Name: " + info[0] + "\n");
                            builder.append("Forge Name: " + info[1] + "\n");
                            if(!info[3].trim().isEmpty()) {
                                builder.append("Desc: " + info[3] + "\n");
                            }
                            builder.append("Side: " + (info[2].trim().equals("0") ? " Client" : info[2].trim().equals("1") ? " Server" : " Both") + "\n");
                        } else if(line.split(",")[1].equalsIgnoreCase(fieldName)) {
                            found[0] = true;
                            String[] info = line.split(",");
                            builder.append("SRG Name: " + info[0] + "\n");
                            builder.append("Forge Name: " + info[1] + "\n");
                            if(!info[3].trim().isEmpty()) {
                                builder.append("Desc: " + info[3] + "\n");
                            }
                            builder.append("Side: " + (info[2].trim().equals("0") ? " Client" : info[2].trim().equals("1") ? " Server" : " Both") + "\n");
                        }
                    });
                    if(!found[0]) {
                        builder.append("No information found!");
                    }
                    System.out.println(builder.toString());
                    embed.ignoreNullEmptyFields();
                    embed.withDesc(builder.toString());
                    embed.withColor((int) (Math.random() * 0x1000000));
                    embed.withTitle("Information on field: " + fieldName);
                    message.getChannel().sendMessage(embed.build());
                    scan.close();
                } catch(IOException e) {
                    e.printStackTrace();
                }
                break;
            case METHOD:
                try {
                    File f = new File("methods.csv");
                    if(!f.exists()) {
                        f.createNewFile();
                    }
                    String fieldName = message.getContent().split(" ")[1];
                    Scanner scan = new Scanner(f).useDelimiter("\n");
                    StringBuilder builder = new StringBuilder();
                    final EmbedBuilder embed = new EmbedBuilder();
                    final boolean[] found = {false};
                    scan.forEachRemaining(line -> {
                        if(line.split(",")[0].equalsIgnoreCase(fieldName)) {
                            found[0] = true;
                            String[] info = line.split(",");
                            builder.append("SRG Name: " + info[0] + "\n");
                            builder.append("Forge Name: " + info[1] + "\n");
                            if(!info[3].trim().isEmpty()) {
                                builder.append("Desc: " + info[3] + "\n");
                            }
                            builder.append("Side: " + (info[2].trim().equals("0") ? " Client" : info[2].trim().equals("1") ? " Server" : " Both") + "\n");
                        } else  if(line.split(",")[1].equalsIgnoreCase(fieldName)) {
                            found[0] = true;
                            String[] info = line.split(",");
                            builder.append("SRG Name: " + info[0] + "\n");
                            builder.append("Forge Name: " + info[1] + "\n");
                            if(!info[3].trim().isEmpty()) {
                                builder.append("Desc: " + info[3] + "\n");
                            }
                            builder.append("Side: " + (info[2].trim().equals("0") ? " Client" : info[2].trim().equals("1") ? " Server" : " Both") + "\n");
                        }
                    });
                    if(!found[0]) {
                        builder.append("No information found!");
                    }
                    System.out.println(builder.toString());
                    embed.ignoreNullEmptyFields();
                    embed.withDesc(builder.toString());
                    embed.withColor((int) (Math.random() * 0x1000000));
                    embed.withTitle("Information on method: " + fieldName);
                    message.getChannel().sendMessage(embed.build());
                    scan.close();
                } catch(IOException e) {
                    e.printStackTrace();
                }
                break;
        }
    }
    
    @Override
    public String getUsage() {
        return "!mcpf for a field / !mcpm for a method";
    }
}
