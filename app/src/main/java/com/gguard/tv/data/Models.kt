package com.gguard.tv.data

enum class ContentType { LIVE, MOVIE, SERIES }

enum class ProfileType { XTREAM, M3U }

data class Profile(
    val id: String,
    val name: String,
    val type: ProfileType,
    val server: String = "",
    val username: String = "",
    val password: String = "",
    val m3uUrl: String = "",
)

data class Category(val id: String, val name: String)

/**
 * One playable or browsable entry: a live channel, a movie, or a series.
 * [url] is set when the source already gives a direct link (M3U); for Xtream
 * accounts the link is built from the id when it is played.
 */
data class StreamItem(
    val id: String,
    val name: String,
    val logo: String?,
    val categoryId: String,
    val type: ContentType,
    val url: String? = null,
    val extension: String? = null,
)

data class Episode(
    val id: String,
    val title: String,
    val season: Int,
    val episodeNum: Int,
    val extension: String,
    val image: String?,
    val plot: String?,
)

data class AccountInfo(
    val status: String,
    val expDate: Long?,
    val maxConnections: String,
    val activeCons: String,
)

data class EpgEntry(val title: String, val start: Long, val end: Long)

class AuthException : Exception("auth")
