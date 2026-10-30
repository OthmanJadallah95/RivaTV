package com.gguard.tv;

import com.gguard.tv.Models.ContentType;
import com.gguard.tv.Models.StreamItem;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses extended M3U playlists (#EXTINF with tvg-logo / group-title attributes). */
public final class M3uParser {
    private static final Pattern ATTR = Pattern.compile("([\\w-]+)=\"([^\"]*)\"");
    public static final String NO_GROUP = "—";

    private M3uParser() {}

    public static List<StreamItem> parse(BufferedReader reader) throws IOException {
        List<StreamItem> out = new ArrayList<>();
        String name = null, logo = null, group = null;
        int counter = 0;
        String raw;
        while ((raw = reader.readLine()) != null) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            String upper = line.length() > 8 ? line.substring(0, 8).toUpperCase() : line.toUpperCase();
            if (upper.startsWith("#EXTINF")) {
                Map<String, String> attrs = new HashMap<>();
                Matcher m = ATTR.matcher(line);
                while (m.find()) attrs.put(m.group(1).toLowerCase(), m.group(2));
                name = extractName(line);
                if (name.isEmpty() && attrs.get("tvg-name") != null) name = attrs.get("tvg-name");
                logo = blankToNull(attrs.get("tvg-logo"));
                group = blankToNull(attrs.get("group-title"));
            } else if (upper.startsWith("#EXTGRP:")) {
                group = blankToNull(line.substring(8).trim());
            } else if (!line.startsWith("#")) {
                counter++;
                String n = (name == null || name.trim().isEmpty()) ? "#" + counter : name;
                out.add(new StreamItem(String.valueOf(counter), n, logo, group != null ? group : NO_GROUP,
                        guessType(line), line, null));
                name = null; logo = null; group = null;
            }
        }
        return out;
    }

    private static String blankToNull(String s) { return (s == null || s.trim().isEmpty()) ? null : s; }

    /** The display name follows the first comma outside a quoted attribute value. */
    private static String extractName(String line) {
        boolean inQuote = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') inQuote = !inQuote;
            else if (c == ',' && !inQuote) return line.substring(i + 1).trim();
        }
        return "";
    }

    private static ContentType guessType(String url) {
        String u = url.toLowerCase();
        if (u.contains("/series/")) return ContentType.SERIES;
        if (u.contains("/movie/")) return ContentType.MOVIE;
        int q = u.indexOf('?');
        String path = q >= 0 ? u.substring(0, q) : u;
        if (path.endsWith(".mp4") || path.endsWith(".mkv") || path.endsWith(".avi")) return ContentType.MOVIE;
        return ContentType.LIVE;
    }
}
