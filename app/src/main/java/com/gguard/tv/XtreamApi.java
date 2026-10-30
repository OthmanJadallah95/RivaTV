package com.gguard.tv;

import android.util.Base64;

import com.gguard.tv.Models.AccountInfo;
import com.gguard.tv.Models.AuthException;
import com.gguard.tv.Models.Category;
import com.gguard.tv.Models.ContentType;
import com.gguard.tv.Models.EpgEntry;
import com.gguard.tv.Models.Episode;
import com.gguard.tv.Models.StreamItem;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Client for the Xtream Codes "player_api.php" interface used by most IPTV providers. */
public final class XtreamApi {
    public final String base;
    private final String username, password;

    public XtreamApi(String server, String username, String password) {
        this.base = normalize(server);
        this.username = username;
        this.password = password;
    }

    public static String normalize(String server) {
        String s = server.trim();
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        String low = s.toLowerCase();
        if (!low.startsWith("http://") && !low.startsWith("https://")) s = "http://" + s;
        if (s.endsWith("/player_api.php")) s = s.substring(0, s.length() - "/player_api.php".length());
        if (s.endsWith("/get.php")) s = s.substring(0, s.length() - "/get.php".length());
        return s;
    }

    private static String enc(String s) {
        try { return URLEncoder.encode(s, "UTF-8"); } catch (UnsupportedEncodingException e) { return s; }
    }

    private String url(String action, String extra) {
        StringBuilder b = new StringBuilder(base).append("/player_api.php?username=").append(enc(username))
                .append("&password=").append(enc(password));
        if (action != null) b.append("&action=").append(action);
        if (extra != null) b.append(extra);
        return b.toString();
    }

    private static JSONObject obj(String text) throws IOException {
        try { return new JSONObject(text); } catch (JSONException e) { throw new IOException("Invalid server response"); }
    }

    private static JSONArray arr(String text) {
        try { return new JSONArray(text); } catch (JSONException e) { return new JSONArray(); }
    }

    static String str(JSONObject o, String key) {
        if (o == null || o.isNull(key)) return null;
        String s = o.optString(key);
        return (s.trim().isEmpty() || "null".equals(s)) ? null : s;
    }

    private static String or(String s, String def) { return s != null ? s : def; }

    private static Long toLong(String s) {
        if (s == null) return null;
        try { return Long.parseLong(s.trim()); } catch (NumberFormatException e) { return null; }
    }

    public AccountInfo login() throws IOException, AuthException {
        JSONObject root = obj(Http.get(url(null, null)));
        JSONObject ui = root.optJSONObject("user_info");
        if (ui == null) throw new IOException("Invalid server response");
        if (ui.optInt("auth", 0) != 1) throw new AuthException();
        return new AccountInfo(or(str(ui, "status"), "-"), toLong(str(ui, "exp_date")),
                or(str(ui, "max_connections"), "-"), or(str(ui, "active_cons"), "0"));
    }

    public List<Category> categories(ContentType type) throws IOException {
        String action = type == ContentType.LIVE ? "get_live_categories"
                : type == ContentType.MOVIE ? "get_vod_categories" : "get_series_categories";
        JSONArray a = arr(Http.get(url(action, null)));
        List<Category> out = new ArrayList<>(a.length());
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            out.add(new Category(o.optString("category_id"), or(str(o, "category_name"), "-")));
        }
        return out;
    }

    public List<StreamItem> streams(ContentType type) throws IOException {
        String action = type == ContentType.LIVE ? "get_live_streams"
                : type == ContentType.MOVIE ? "get_vod_streams" : "get_series";
        JSONArray a = arr(Http.get(url(action, null)));
        List<StreamItem> out = new ArrayList<>(a.length());
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            String name = or(str(o, "name"), "-");
            String cat = o.optString("category_id");
            switch (type) {
                case LIVE:
                    out.add(new StreamItem(o.optString("stream_id"), name, str(o, "stream_icon"), cat, type, null, null));
                    break;
                case MOVIE:
                    out.add(new StreamItem(o.optString("stream_id"), name, str(o, "stream_icon"), cat, type, null,
                            or(str(o, "container_extension"), "mp4")));
                    break;
                default:
                    out.add(new StreamItem(o.optString("series_id"), name, str(o, "cover"), cat, type, null, null));
            }
        }
        return out;
    }

    public List<Episode> seriesEpisodes(String seriesId) throws IOException {
        JSONObject root = obj(Http.get(url("get_series_info", "&series_id=" + enc(seriesId))));
        List<Episode> out = new ArrayList<>();
        Object eps = root.opt("episodes");
        if (eps instanceof JSONObject) {
            JSONObject map = (JSONObject) eps;
            Iterator<String> keys = map.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                JSONArray list = map.optJSONArray(k);
                if (list == null) continue;
                int season;
                try { season = Integer.parseInt(k.trim()); } catch (NumberFormatException e) { season = 1; }
                for (int i = 0; i < list.length(); i++) add(out, list.optJSONObject(i), season);
            }
        } else if (eps instanceof JSONArray) {
            JSONArray seasons = (JSONArray) eps;
            for (int s = 0; s < seasons.length(); s++) {
                JSONArray list = seasons.optJSONArray(s);
                if (list == null) continue;
                for (int i = 0; i < list.length(); i++) add(out, list.optJSONObject(i), s + 1);
            }
        }
        return out;
    }

    private static void add(List<Episode> out, JSONObject e, int seasonFallback) {
        if (e == null) return;
        JSONObject info = e.optJSONObject("info");
        out.add(new Episode(e.optString("id"), or(str(e, "title"), ""), e.optInt("season", seasonFallback),
                e.optInt("episode_num", out.size() + 1), or(str(e, "container_extension"), "mp4"),
                str(info, "movie_image"), str(info, "plot")));
    }

    public List<EpgEntry> shortEpg(String streamId) throws IOException {
        JSONObject root = obj(Http.get(url("get_short_epg", "&stream_id=" + enc(streamId) + "&limit=2")));
        JSONArray a = root.optJSONArray("epg_listings");
        List<EpgEntry> out = new ArrayList<>();
        if (a == null) return out;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null) out.add(new EpgEntry(decode(o.optString("title"))));
        }
        return out;
    }

    private static String decode(String s) {
        try { return new String(Base64.decode(s, Base64.DEFAULT), "UTF-8"); } catch (Exception e) { return s; }
    }

    public String streamUrl(StreamItem item, String liveExt) {
        switch (item.type) {
            case LIVE: return base + "/live/" + username + "/" + password + "/" + item.id + "." + liveExt;
            case MOVIE: return base + "/movie/" + username + "/" + password + "/" + item.id + "." + or(item.extension, "mp4");
            default: return base + "/series/" + username + "/" + password + "/" + item.id + "." + or(item.extension, "mp4");
        }
    }

    public String episodeUrl(Episode ep) {
        return base + "/series/" + username + "/" + password + "/" + ep.id + "." + ep.extension;
    }
}
