package com.gguard.tv;

import com.gguard.tv.Models.Category;
import com.gguard.tv.Models.ContentType;
import com.gguard.tv.Models.EpgEntry;
import com.gguard.tv.Models.Episode;
import com.gguard.tv.Models.AccountInfo;
import com.gguard.tv.Models.Profile;
import com.gguard.tv.Models.StreamItem;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Holds the open playlist and caches what was loaded from it. Loading methods run on background threads. */
public final class Session {
    public static final String ALL = "__all";
    public static final String FAV = "__fav";

    public static volatile Profile profile;
    public static volatile XtreamApi api;
    public static volatile AccountInfo account;
    /** Items the player steps through (channel list, or a season's episodes). */
    public static List<StreamItem> playQueue = Collections.emptyList();

    private static final Map<ContentType, List<Category>> categoryCache = new EnumMap<>(ContentType.class);
    private static final Map<ContentType, List<StreamItem>> itemCache = new EnumMap<>(ContentType.class);
    private static final Map<String, List<Episode>> episodeCache = new HashMap<>();
    private static List<StreamItem> m3uCache;

    private Session() {}

    public static synchronized void open(Profile p) {
        profile = p;
        api = p.isXtream() ? new XtreamApi(p.server, p.username, p.password) : null;
        account = null;
        clearCache();
    }

    public static synchronized void clearCache() {
        categoryCache.clear();
        itemCache.clear();
        episodeCache.clear();
        m3uCache = null;
    }

    public static boolean isXtream() { return api != null; }

    private static synchronized List<StreamItem> m3uItems() throws IOException {
        if (m3uCache != null) return m3uCache;
        Profile p = profile;
        if (p == null) return Collections.emptyList();
        HttpURLConnection c = Http.open(p.m3uUrl);
        try (BufferedReader r = Http.reader(c)) {
            m3uCache = M3uParser.parse(r);
        } finally {
            c.disconnect();
        }
        return m3uCache;
    }

    public static synchronized List<Category> categories(ContentType type) throws IOException {
        List<Category> cached = categoryCache.get(type);
        if (cached != null) return cached;
        List<Category> list;
        if (api != null) {
            list = api.categories(type);
        } else {
            Set<String> groups = new LinkedHashSet<>();
            for (StreamItem i : m3uItems()) if (i.type == type) groups.add(i.categoryId);
            list = new ArrayList<>();
            for (String g : groups) list.add(new Category(g, g));
        }
        categoryCache.put(type, list);
        return list;
    }

    public static synchronized List<StreamItem> items(ContentType type) throws IOException {
        List<StreamItem> cached = itemCache.get(type);
        if (cached != null) return cached;
        List<StreamItem> list;
        if (api != null) {
            list = api.streams(type);
        } else {
            list = new ArrayList<>();
            for (StreamItem i : m3uItems()) if (i.type == type) list.add(i);
        }
        itemCache.put(type, list);
        return list;
    }

    public static synchronized List<Episode> episodes(String seriesId) throws IOException {
        List<Episode> cached = episodeCache.get(seriesId);
        if (cached != null) return cached;
        if (api == null) return Collections.emptyList();
        List<Episode> list = api.seriesEpisodes(seriesId);
        episodeCache.put(seriesId, list);
        return list;
    }

    public static List<EpgEntry> shortEpg(String streamId) throws IOException {
        XtreamApi a = api;
        return a != null ? a.shortEpg(streamId) : Collections.<EpgEntry>emptyList();
    }

    public static String streamUrl(StreamItem item) {
        if (item.url != null) return item.url;
        XtreamApi a = api;
        return a != null ? a.streamUrl(item, Store.liveExt()) : null;
    }

    public static String episodeUrl(Episode ep) {
        XtreamApi a = api;
        return a != null ? a.episodeUrl(ep) : null;
    }
}
