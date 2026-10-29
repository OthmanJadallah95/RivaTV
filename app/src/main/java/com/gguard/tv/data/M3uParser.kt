package com.gguard.tv.data

import java.io.BufferedReader

/** Parses extended M3U playlists (#EXTINF with tvg-logo / group-title attributes). */
object M3uParser {
    private val attrRegex = Regex("""([\w-]+)="([^"]*)"""")
    const val NO_GROUP = "—"

    fun parse(reader: BufferedReader): List<StreamItem> {
        val out = ArrayList<StreamItem>()
        var name: String? = null
        var logo: String? = null
        var group: String? = null
        var counter = 0

        while (true) {
            val raw = reader.readLine() ?: break
            val line = raw.trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    val attrs = attrRegex.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    name = extractName(line).ifBlank { attrs["tvg-name"] ?: "" }
                    logo = attrs["tvg-logo"]?.takeIf { it.isNotBlank() }
                    group = attrs["group-title"]?.takeIf { it.isNotBlank() }
                }
                line.startsWith("#EXTGRP:", ignoreCase = true) -> {
                    group = line.substringAfter(':').trim().takeIf { it.isNotBlank() }
                }
                line.startsWith("#") -> Unit
                else -> {
                    counter++
                    out += StreamItem(
                        id = counter.toString(),
                        name = name?.takeIf { it.isNotBlank() } ?: "#$counter",
                        logo = logo,
                        categoryId = group ?: NO_GROUP,
                        type = guessType(line),
                        url = line,
                    )
                    name = null; logo = null; group = null
                }
            }
        }
        return out
    }

    /** The display name follows the first comma that is outside a quoted attribute value. */
    private fun extractName(line: String): String {
        var inQuote = false
        for (i in line.indices) {
            val c = line[i]
            if (c == '"') inQuote = !inQuote
            else if (c == ',' && !inQuote) return line.substring(i + 1).trim()
        }
        return ""
    }

    private fun guessType(url: String): ContentType {
        val u = url.lowercase()
        return when {
            "/series/" in u -> ContentType.SERIES
            "/movie/" in u -> ContentType.MOVIE
            u.substringBefore('?').let { it.endsWith(".mp4") || it.endsWith(".mkv") || it.endsWith(".avi") } -> ContentType.MOVIE
            else -> ContentType.LIVE
        }
    }
}
