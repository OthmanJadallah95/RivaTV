package com.gguard.tv;

import android.content.Context;
import android.content.SharedPreferences;

import com.gguard.tv.Models.Profile;
import com.gguard.tv.Models.StreamItem;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Saved playlists, favorites and settings. */
public final class Store {
    private static SharedPreferences profiles, favorites, settings;
    private static Set<String> favSet = new HashSet<>();

    private Store() {}

    public static synchronized void init(Context ctx) {
        if (profiles != null) return;
        Context a = ctx.getApplicationContext();
        profiles = a.getSharedPreferences("profiles", Context.MODE_PRIVATE);
        favorites = a.getSharedPreferences("favorites", Context.MODE_PRIVATE);
        settings = a.getSharedPreferences("settings", Context.MODE_PRIVATE);
        favSet = new HashSet<>(favorites.getStringSet("keys", new HashSet<String>()));
    }

    public static List<Profile> profiles() {
        List<Profile> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(profiles.getString("list", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o != null) out.add(Profile.fromJson(o));
            }
        } catch (Exception ignored) { }
        return out;
    }

    private static void saveAll(List<Profile> list) {
        JSONArray a = new JSONArray();
        try {
            for (Profile p : list) a.put(p.toJson());
        } catch (Exception ignored) { }
        profiles.edit().putString("list", a.toString()).apply();
    }

    public static void addProfile(Profile p) {
        List<Profile> list = profiles();
        for (int i = list.size() - 1; i >= 0; i--) if (list.get(i).id.equals(p.id)) list.remove(i);
        list.add(p);
        saveAll(list);
    }

    public static void removeProfile(String id) {
        List<Profile> list = profiles();
        for (int i = list.size() - 1; i >= 0; i--) if (list.get(i).id.equals(id)) list.remove(i);
        saveAll(list);
        if (id.equals(lastId())) setLastId(null);
    }

    public static String lastId() { return profiles.getString("last", null); }

    public static void setLastId(String id) { profiles.edit().putString("last", id).apply(); }

    private static String favKey(StreamItem item) {
        Profile p = Session.profile;
        return (p != null ? p.id : "") + "|" + item.type + "|" + item.id;
    }

    public static boolean isFav(StreamItem item) { return favSet.contains(favKey(item)); }

    /** Returns true when the item is now a favorite. */
    public static boolean toggleFav(StreamItem item) {
        String k = favKey(item);
        boolean added;
        if (favSet.contains(k)) { favSet.remove(k); added = false; } else { favSet.add(k); added = true; }
        favorites.edit().putStringSet("keys", new HashSet<>(favSet)).apply();
        return added;
    }

    /** "vlc" (default) or "system". */
    public static String player() { return settings.getString("player", "vlc"); }

    public static void setPlayer(String p) { settings.edit().putString("player", p).apply(); }

    /** How much of the stream to buffer before/while playing, in ms (VLC player). */
    public static int bufferMs() { return settings.getInt("buffer_ms", 5000); }

    public static void setBufferMs(int ms) { settings.edit().putInt("buffer_ms", ms).apply(); }

    /** Speed readout: 0 = off, -1 = always, otherwise every N seconds (see SpeedMeter.MODES). */
    public static int speedMode() { return settings.getInt("speed_mode", 60); }

    public static void setSpeedMode(int m) { settings.edit().putInt("speed_mode", m).apply(); }

    public static String liveExt() { return settings.getString("live_ext", "m3u8"); }

    public static void setLiveExt(String ext) { settings.edit().putString("live_ext", ext).apply(); }
}
