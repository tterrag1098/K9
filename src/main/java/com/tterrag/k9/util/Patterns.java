package com.tterrag.k9.util;

import java.util.regex.Pattern;

public class Patterns {
    
    public static final Pattern FLAGS = Pattern.compile("^(--?)(\\w+)(?:[=\\s](?:\"(.*?)\"|(\\S+)))?");

    public static final Pattern ARG_SPLITTER = Pattern.compile("(\"(?<quoted>.+?)(?<![^\\\\]\\\\)\")|(?<unquoted>\\S+)", Pattern.DOTALL);
    public static final Pattern CODEBLOCK = Pattern.compile("```(\\w*)(.*)```", Pattern.DOTALL);

    public static final Pattern IN_QUOTES = Pattern.compile("\".*\"");

    public static final Pattern INCREMENT_DECREMENT = Pattern.compile("^(\\S+)(\\+\\+|--)$");
    
    public static final Pattern DISCORD_MENTION = Pattern.compile("<@&?!?([0-9]+)>");
    public static final Pattern DISCORD_CHANNEL = Pattern.compile("<#([0-9]+)>");

    public static final Pattern MATCH_ALL = Pattern.compile(".+$", Pattern.DOTALL);
    public static final Pattern MATCH_WORD = Pattern.compile("\\S+");
    public static final Pattern MATCH_INT = Pattern.compile("[-+]?\\d+\\b");
    public static final Pattern MATCH_DOUBLE = Pattern.compile("[-+]?\\d+(\\.\\d+)?\\b");

    public static final Pattern IRC_CHANNEL = Pattern.compile("#?(\\w+)");
    
    public static final Pattern REGEX_PATTERN = Pattern.compile("\\/(.*?)\\/");
    
    public static final Pattern MAPPINGS_FILENAME = Pattern.compile("mcp_(?:stable|snapshot)-([0-9]+)-\\S+\\.zip");
    public static final Pattern SRG_PATTERN = Pattern.compile("^(CL|FD|MD):\\s(.+)$");
    public static final Pattern SRG_PARAM = Pattern.compile("(?:p_)?(\\d+)_(\\d)_?");
    public static final Pattern NOTCH_PARAM = Pattern.compile("[a-z$]+");

}
