package com.gguard.tv.data

import android.util.Base64
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

/** Client for the Xtream Codes "player_api.php" interface used by most IPTV providers. */
class XtreamApi(server: String, private val username: String, private val password: String) {

    val base: String = normalize(server)

    companion object {
        fun normalize(server: String): String {
            var s = server.trim().trimEnd('/')
            if (!s.startsWith("http://", true) && !s.startsWith("https://", true)) s = "http://$s"
            if (s.endsWith("/player_api.php")) s = s.removeSuffix("/player_api.php")
            if (s.endsWith("/get.php")) s = s.removeSuffix("/get.php")
            return s
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun url(action: String?, extra: String = ""): String = buildString {
        append(base).append("/player_api.php?username=").append(enc(username))
        append("&password=").append(enc(password))
        if (action != null) append("&action=").append(action)
        append(extra)
    }

    private fun obj(text: String): JSONObject = try {
        JSONObject(text)
    } catch (e: JSONException) {
        throw IOException("Invalid server response")
    }

    private fun arr(text: String): JSONArray = try {
        JSONArray(text)
    } catch (e: JSONException) {
        JSONArray()
    }

    private fun JSONObject.str(key: String): String? {
        if (isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() && it != "null" }
    }

    suspend fun login(): AccountInfo {
        val root = obj(Http.get(url(null)))
        val ui = root.optJSONObject("user_info") ?: throw IOException("Invalid server response")
        if (ui.optInt("auth", 0) != 1) throw AuthException()
        return AccountInfo(
            status = ui.str("status") ?: "-",
            expDate = ui.str("exp_date")?.toLongOrNull(),
            maxConnections = ui.str("max_connections") ?: "-",
            activeCons = ui.str("active_cons") ?: "0",
        )
    }

    suspend fun categories(type: ContentType): List<Category> {
        val action = when (type) {
            ContentType.LIVE -> "get_live_categories"
            ContentType.MOVIE -> "get_vod_categories"
            ContentType.SERIES -> "get_series_categories"
        }
        val a = arr(Http.get(url(action)))
        val out = ArrayList<Category>(a.length())
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            out += Category(o.optString("category_id"), o.str("category_name") ?: "-")
        }
        return out
    }

    suspend fun streams(type: ContentType): List<StreamItem> {
        val action = when (type) {
            ContentType.LIVE -> "get_live_streams"
            ContentType.MOVIE -> "get_vod_streams"
            ContentType.SERIES -> "get_series"
        }
        val a = arr(Http.get(url(action)))
        val out = ArrayList<StreamItem>(a.length())
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            out += when (type) {
                ContentType.LIVE -> StreamItem(
                    id = o.optString("stream_id"),
                    name = o.str("name") ?: "-",
                    logo = o.str("stream_icon"),
                    categoryId = o.optString("category_id"),
                    type = type,
                )
                ContentType.MOVIE -> StreamItem(
                    id = o.optString("stream_id"),
                    name = o.str("name") ?: "-",
                    logo = o.str("stream_icon"),
                    categoryId = o.optString("category_id"),
                    type = type,
                    extension = o.str("container_extension") ?: "mp4",
                )
                ContentType.SERIES -> StreamItem(
                    id = o.optString("series_id"),
                    name = o.str("name") ?: "-",
                    logo = o.str("cover"),
                    categoryId = o.optString("category_id"),
                    type = type,
                )
            }
        }
        return out
    }

    suspend fun seriesEpisodes(seriesId: String): List<Episode> {
        val root = obj(Http.get(url("get_series_info", "&series_id=${enc(seriesId)}")))
        val out = ArrayList<Episode>()
        fun add(e: JSONObject, seasonFallback: Int) {
            val info = e.optJSONObject("info")
            out += Episode(
                id = e.optString("id"),
                title = e.str("title") ?: "",
                season = e.optInt("season", seasonFallback),
                episodeNum = e.optInt("episode_num", out.size + 1),
                extension = e.str("container_extension") ?: "mp4",
                image = info?.str("movie_image"),
                plot = info?.str("plot"),
            )
        }
        when (val eps = root.opt("episodes")) {
            is JSONObject -> {
                val keys = eps.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val list = eps.optJSONArray(k) ?: continue
                    for (i in 0 until list.length()) list.optJSONObject(i)?.let { add(it, k.toIntOrNull() ?: 1) }
                }
            }
            is JSONArray -> {
                for (s in 0 until eps.length()) {
                    val list = eps.optJSONArray(s) ?: continue
                    for (i in 0 until list.length()) list.optJSONObject(i)?.let { add(it, s + 1) }
                }
            }
        }
        return out
    }

    suspend fun shortEpg(streamId: String): List<EpgEntry> {
        val root = obj(Http.get(url("get_short_epg", "&stream_id=${enc(streamId)}&limit=2")))
        val a = root.optJSONArray("epg_listings") ?: return emptyList()
        val out = ArrayList<EpgEntry>()
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            out += EpgEntry(
                title = decode(o.optString("title")),
                start = o.optString("start_timestamp").toLongOrNull() ?: 0L,
                end = o.optString("stop_timestamp").toLongOrNull() ?: 0L,
            )
        }
        return out
    }

    private fun decode(s: String): String = try {
        String(Base64.decode(s, Base64.DEFAULT), Charsets.UTF_8)
    } catch (e: IllegalArgumentException) {
        s
    }

    fun streamUrl(item: StreamItem, liveExt: String): String = when (item.type) {
        ContentType.LIVE -> "$base/live/$username/$password/${item.id}.$liveExt"
        ContentType.MOVIE -> "$base/movie/$username/$password/${item.id}.${item.extension ?: "mp4"}"
        ContentType.SERIES -> "$base/series/$username/$password/${item.id}.${item.extension ?: "mp4"}"
    }

    fun episodeUrl(ep: Episode): String = "$base/series/$username/$password/${ep.id}.${ep.extension}"
}
