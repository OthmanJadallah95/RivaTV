package com.gguard.tv;

import org.json.JSONException;
import org.json.JSONObject;

/** Plain data holders. */
public final class Models {
    private Models() {}

    public enum ContentType { LIVE, MOVIE, SERIES }

    public static final class Profile {
        public static final String XTREAM = "XTREAM";
        public static final String M3U = "M3U";

        public String id, name, type, server = "", username = "", password = "", m3uUrl = "";

        public boolean isXtream() { return XTREAM.equals(type); }

        JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("id", id); o.put("name", name); o.put("type", type);
            o.put("server", server); o.put("username", username);
            o.put("password", password); o.put("m3uUrl", m3uUrl);
            return o;
        }

        static Profile fromJson(JSONObject o) {
            Profile p = new Profile();
            p.id = o.optString("id"); p.name = o.optString("name");
            p.type = o.optString("type", XTREAM);
            p.server = o.optString("server"); p.username = o.optString("username");
            p.password = o.optString("password"); p.m3uUrl = o.optString("m3uUrl");
            return p;
        }
    }

    public static final class Category {
        public final String id, name;
        public Category(String id, String name) { this.id = id; this.name = name; }
    }

    /** A live channel, movie or series. [url] is set when the source gives a direct link (M3U, episodes). */
    public static final class StreamItem {
        public final String id, name, logo, categoryId, url, extension;
        public final ContentType type;
        // Set for series episodes so Continue Watching groups them under one series entry.
        public String seriesId, seriesName, seriesLogo;
        public int season, episodeNum;

        public StreamItem(String id, String name, String logo, String categoryId, ContentType type,
                          String url, String extension) {
            this.id = id; this.name = name; this.logo = logo; this.categoryId = categoryId;
            this.type = type; this.url = url; this.extension = extension;
        }
    }

    public static final class Episode {
        public final String id, title, extension, image, plot;
        public final int season, episodeNum;

        public Episode(String id, String title, int season, int episodeNum, String extension, String image, String plot) {
            this.id = id; this.title = title; this.season = season; this.episodeNum = episodeNum;
            this.extension = extension; this.image = image; this.plot = plot;
        }
    }

    public static final class AccountInfo {
        public final String status, maxConnections, activeCons;
        public final Long expDate;

        public AccountInfo(String status, Long expDate, String maxConnections, String activeCons) {
            this.status = status; this.expDate = expDate;
            this.maxConnections = maxConnections; this.activeCons = activeCons;
        }
    }

    public static final class EpgEntry {
        public final String title;
        public EpgEntry(String title) { this.title = title; }
    }

    public static final class AuthException extends Exception {
        public AuthException() { super("auth"); }
    }
}
