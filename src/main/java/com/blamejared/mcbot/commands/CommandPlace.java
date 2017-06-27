package com.blamejared.mcbot.commands;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.imageio.ImageIO;

import com.blamejared.mcbot.commands.api.Command;
import com.blamejared.mcbot.commands.api.CommandBase;
import com.blamejared.mcbot.commands.api.CommandException;

import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.util.EmbedBuilder;

@Command
public class CommandPlace extends CommandBase {
    
    private Map<String, Rectangle> locations = new HashMap<>();
    public CommandPlace() {
        super("place", false);
        locations.put("clay", new Rectangle(493,698,20,20));
    }
    
    private static final int[] colors =
        { 0xFFFFFF, 0xE4E4E4, 0x888888, 0x222222, 0xffa7d1, 0xe50000, 0xe59500, 0xa06a42, 0xe5d900, 0x94e044, 0x02be01, 0x00d3dd, 0x0083c7, 0x0000ea, 0xcf6ee4, 0x820080 };

    
    
    @Override
    public void process(IMessage message, List<String> flags, List<String> args) throws CommandException {
        Rectangle area = new Rectangle(0, 0, 1000, 1000);
        int scale = 1;

        if(flags.contains("ls")){
            EmbedBuilder builder = new EmbedBuilder();
            builder.withTitle("References:");
            StringBuilder str = new StringBuilder();
            locations.keySet().forEach(key-> str.append(key).append("\n"));
            builder.withDescription(str.toString());
            builder.withColor(message.getAuthor().hashCode());
            message.getChannel().sendMessage(builder.build());
            return;
        }
        if(flags.contains("random")){
            Random rand = new Random();
            int minX = rand.nextInt(999);
            int minY = rand.nextInt(999);
            int maxX = rand.nextInt(999);
            int maxY = rand.nextInt(999);
            scale = 8;
            area = new Rectangle(Math.min(minX, maxX), Math.min(minY, maxY), Math.max(minX, maxX)-Math.min(minX, maxX), Math.max(minY, maxY)-Math.min(minY, maxY));
        }
        if (args.size() > 0) {
            if(args.size() == 1 && locations.containsKey(args.get(0))){
                area = locations.get(args.get(0));
                scale = 8;
            } else {
                if (args.size() != 4 && args.size() != 2) {
                    throw new CommandException("Must be exactly 2 or 4 dimension arguments");
                }
                try {
                    area.x = Integer.parseInt(args.get(0));
                    area.y = Integer.parseInt(args.get(1));
                    if (args.size() == 4) {
                        area.width = Integer.parseInt(args.get(2)) - area.x + 1;
                        area.height = Integer.parseInt(args.get(3)) - area.y + 1;
                    } else {
                        area.x -= 50;
                        area.y -= 50;
                        area.width = 101;
                        area.height = 101;
                    }

                    if (area.x + area.width > 1000 || area.y + area.height > 1000) {
                        throw new CommandException("Invalid dimensions");
                    }
                } catch (NumberFormatException e) {
                    throw new CommandException(e);
                }
            }
        }

        for (String s : flags) {
            if (s.startsWith("scale=")) {
                scale = Integer.parseInt(s.substring(s.lastIndexOf('=') + 1));
            } else if(s.startsWith("add=")){
                locations.put(s.split("add=")[1], area);
                message.getChannel().sendMessage("Added reference: " + s.split("add=")[1]);
            } else if(s.startsWith("remove=")){
                if(locations.remove(s.split("remove=")[1]) !=null) {
                    message.getChannel().sendMessage("Removed reference: " + s.split("remove=")[1]);
                    return;
                }
                else {
                    message.getChannel().sendMessage("No reference found to remove.");
                    return;
                }
            }
        }

        try {
            message.getChannel().setTypingStatus(true);
            
            InputStream stream = new URL("https://www.reddit.com/api/place/board-bitmap").openStream();
            byte[] data = new byte[500004];
            int read = 0;
            while (read < 500004) {
                read += stream.read(data, read, Math.min(500004 - read, 1000));
            }
            
            byte[] imagebytes = new byte[500000];
            System.arraycopy(data, 4, imagebytes, 0, 500000);
            data = imagebytes; // dereference original array

            BufferedImage image = new BufferedImage(1000, 1000, BufferedImage.TYPE_INT_RGB);
            boolean inverted = flags.contains("invert");
            for (int i = 0; i < data.length; i++) {
                int x = (i * 2) % 1000;
                int y = (i * 2) / 1000;
                int b = data[i] & 0xFF;
                int c1 = b >>> 4;
                int c2 = b & 0xF;
                if(!inverted) {
                    image.setRGB(x, y, colors[c1]);
                    image.setRGB(x + 1, y, colors[c2]);
                }else{
                    image.setRGB(x, y, 255-colors[c1]);
                    image.setRGB(x + 1, y, 255-colors[c2]);
                }
            }
            
            if (area.width < 1000 || area.height < 1000) {
                image = image.getSubimage(area.x, area.y, area.width, area.height);
            }
            if (scale != 1) {
                BufferedImage resized = new BufferedImage(area.width * scale, area.height * scale, image.getType());
                Graphics2D g = resized.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                g.drawImage(image, 0, 0, resized.getWidth(), resized.getHeight(), 0, 0, area.width, area.height, null);
                g.dispose();
                image = resized;
            }
            
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", bos);
            
            message.getChannel().sendFile(String.format("/r/place image for area: %d,%d -> %d,%d: <https://www.reddit.com/place?webview=true#x=%d&y=%d>",
                    area.x, area.y, area.x + area.width - 1, area.y + area.height - 1, area.x + ((area.width - 1) / 2), area.y + ((area.height - 1) / 2)),
                    new ByteArrayInputStream(bos.toByteArray()), "place.png");
            
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            message.getChannel().setTypingStatus(false);
        }
    }

    @Override
    public String getUsage() {
        return "[-scale=X] [<x1> <y1> <x2> <y2>]";
    }
}
