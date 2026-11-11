package com.gguard.tv;

import android.content.Context;
import android.content.SharedPreferences;

import com.gguard.tv.Models.ContentType;
import com.gguard.tv.Models.Profile;
import com.gguard.tv.Models.StreamItem;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Remembers where playback stopped for each movie and episode, so the Continue Watching
 * list can resume exactly there. Only VOD (not live) is tracked. Kept small by dropping the
 * oldest entries past a cap and removing anything watched almost to the end.
 */
public final class ResumeStore {
    private static final int MAX = 60;
    private static final long MIN_POS = 10000;     // ignore the first 10 s
    private static final double DONE = 0.97;       // treat as finished near the end

    private static SharedPreferences prefs;

    private ResumeStore() {}

    public static synchronized void init(Context ctx) {
        if (prefs == null) prefs = ctx.getApplicationContext().getSharedPreferences("resume", Context.MODE_PRIVATE);
    }

    public static final class Entry {
        public final StreamItem item;
        public final long position, duration, updated;
        Entry(StreamItem item, long position, long duration, long updated) {
            this.item = item; this.position = position; this.duration = duration; this.updated = updated;
        }
        public int percent() { return duration > 0 ? (int) (position * 100 / duration) : 0; }
    }

    private static String key(StreamItem item) {
        Profile p = Session.profile;
        String pre = (p != null ? p.id : "") + "|";
        // Series episodes share one key per series → a single Continue Watching entry.
        if (item.seriesId != null && !item.seriesId.isEmpty()) return pre + "series|" + item.seriesId;
        return pre + item.type + "|" + item.id;
    }

    private static JSONArray load() {
        try { return new JSONArray(prefs.getString("list", "[]")); } catch (Exception e) { return new JSONArray(); }
    }

    private static void store(JSONArray a) { prefs.edit().putString("list", a.toString()).apply(); }

    /** Call periodically and when leaving a video. Updates, removes-if-finished, and re-orders newest first. */
    public static synchronized void save(StreamItem item, long position, long duration) {
        if (item == null || item.type == ContentType.LIVE) return;
        String k = key(item);
        JSONArray old = load();
        JSONArray out = new JSONArray();
        boolean finished = duration > 0 && position >= duration * DONE;
        if (position >= MIN_POS && !finished) {
            try {
                JSONObject o = new JSONObject();
                o.put("key", k);
                o.put("id", item.id); o.put("name", item.name); o.put("logo", item.logo);
                o.put("cat", item.categoryId); o.put("ctype", item.type.name());
                o.put("url", item.url); o.put("ext", item.extension);
                o.put("seriesId", item.seriesId); o.put("seriesName", item.seriesName);
                o.put("seriesLogo", item.seriesLogo); o.put("season", item.season); o.put("episodeNum", item.episodeNum);
                o.put("pos", position); o.put("dur", duration); o.put("ts", System.currentTimeMillis());
                out.put(o);
            } catch (Exception ignored) { }
        }
        for (int i = 0; i < old.length() && out.length() < MAX; i++) {
            JSONObject o = old.optJSONObject(i);
            if (o != null && !k.equals(o.optString("key"))) out.put(o);
        }
        store(out);
    }

    public static synchronized long position(StreamItem item) {
        if (item == null) return 0;
        String k = key(item);
        JSONArray a = load();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && k.equals(o.optString("key"))) {
                // Only resume if the saved entry is the same episode/movie.
                return item.id.equals(o.optString("id")) ? o.optLong("pos", 0) : 0;
            }
        }
        return 0;
    }

    public static synchronized void remove(StreamItem item) {
        String k = key(item);
        JSONArray old = load();
        JSONArray out = new JSONArray();
        for (int i = 0; i < old.length(); i++) {
            JSONObject o = old.optJSONObject(i);
            if (o != null && !k.equals(o.optString("key"))) out.put(o);
        }
        store(out);
    }

    /** Resume items for the current profile, newest first. */
    public static synchronized List<Entry> list() {
        Profile p = Session.profile;
        String prefix = (p != null ? p.id : "") + "|";
        JSONArray a = load();
        List<Entry> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null || !o.optString("key").startsWith(prefix)) continue;
            ContentType type;
            try { type = ContentType.valueOf(o.optString("ctype")); } catch (Exception e) { type = ContentType.MOVIE; }
            StreamItem item = new StreamItem(o.optString("id"), o.optString("name"),
                    nullable(o, "logo"), o.optString("cat"), type, nullable(o, "url"), nullable(o, "ext"));
            item.seriesId = nullable(o, "seriesId"); item.seriesName = nullable(o, "seriesName");
            item.seriesLogo = nullable(o, "seriesLogo"); item.season = o.optInt("season"); item.episodeNum = o.optInt("episodeNum");
            out.add(new Entry(item, o.optLong("pos", 0), o.optLong("dur", 0), o.optLong("ts", 0)));
        }
        return out;
    }

    private static String nullable(JSONObject o, String k) {
        return o.isNull(k) ? null : o.optString(k, null);
    }
}
