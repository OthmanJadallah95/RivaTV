package com.gguard.tv.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Holds the open playlist and caches what was loaded from it. */
object Session {
    const val ALL = "__all"
    const val FAV = "__fav"

    private lateinit var prefs: SharedPreferences

    var profile: Profile? = null
        private set
    var api: XtreamApi? = null
        private set
    var account: AccountInfo? = null

    /** Items the player can step through (channel list, or a season's episodes). */
    var playQueue: List<StreamItem> = emptyList()

    private val categoryCache = HashMap<ContentType, List<Category>>()
    private val itemCache = HashMap<ContentType, List<StreamItem>>()
    private val episodeCache = HashMap<String, List<Episode>>()
    private var m3uCache: List<StreamItem>? = null
    private val mutex = Mutex()

    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
    }

    val isXtream: Boolean get() = api != null

    fun open(p: Profile) {
        profile = p
        api = if (p.type == ProfileType.XTREAM) XtreamApi(p.server, p.username, p.password) else null
        account = null
        clearCache()
    }

    fun clearCache() {
        categoryCache.clear(); itemCache.clear(); episodeCache.clear(); m3uCache = null
    }

    var liveExt: String
        get() = prefs.getString("live_ext", "ts") ?: "ts"
        set(v) { prefs.edit().putString("live_ext", v).apply() }

    private suspend fun m3uItems(): List<StreamItem> {
        m3uCache?.let { return it }
        val url = profile?.m3uUrl ?: return emptyList()
        return Http.readLines(url) { M3uParser.parse(it) }.also { m3uCache = it }
    }

    suspend fun categories(type: ContentType): List<Category> = mutex.withLock {
        categoryCache[type]?.let { return@withLock it }
        val list = api?.categories(type)
            ?: m3uItems().asSequence().filter { it.type == type }.map { it.categoryId }.distinct()
                .map { Category(it, it) }.toList()
        categoryCache[type] = list
        list
    }

    suspend fun items(type: ContentType): List<StreamItem> = mutex.withLock {
        itemCache[type]?.let { return@withLock it }
        val list = api?.streams(type) ?: m3uItems().filter { it.type == type }
        itemCache[type] = list
        list
    }

    suspend fun episodes(seriesId: String): List<Episode> {
        episodeCache[seriesId]?.let { return it }
        val a = api ?: return emptyList()
        return a.seriesEpisodes(seriesId).also { episodeCache[seriesId] = it }
    }

    suspend fun shortEpg(streamId: String): List<EpgEntry> = api?.shortEpg(streamId) ?: emptyList()

    fun streamUrl(item: StreamItem): String? = item.url ?: api?.streamUrl(item, liveExt)

    fun episodeUrl(ep: Episode): String? = api?.episodeUrl(ep)
}
