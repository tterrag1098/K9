package com.blamejared.mcbot.commands;

import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.util.EmbedBuilder;

import java.io.*;
import java.util.Scanner;

public class CommandMCP extends CommandBase {
    
    public CommandMCP() {
        super("!mcp");
    }
    
    @Override
    public void exectute(IMessage message) {
        if(message.getContent().split(" ").length > 1) {
            if(message.getContent().startsWith("!mcpf ")) {
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
            } else if(message.getContent().startsWith("!mcpm")) {
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
            }
        } else {
            message.getChannel().sendMessage(getUsage());
        }
        
    }
    
    @Override
    public String getUsage() {
        return "!mcpf for a field / !mcpm for a method";
    }
}
