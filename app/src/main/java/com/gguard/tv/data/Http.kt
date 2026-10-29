package com.gguard.tv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.IOException
import java.util.concurrent.TimeUnit

object Http {
    const val USER_AGENT = "GGuardTV/1.0 (Linux; Android)"

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    private fun request(url: String) = Request.Builder()
        .url(url)
        .header("User-Agent", USER_AGENT)
        .build()

    suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        client.newCall(request(url)).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            r.body?.string() ?: ""
        }
    }

    /** Streams the body line by line so very large M3U files don't have to fit in memory as one string. */
    suspend fun <T> readLines(url: String, block: (BufferedReader) -> T): T = withContext(Dispatchers.IO) {
        client.newCall(request(url)).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            val body = r.body ?: throw IOException("Empty response")
            body.charStream().buffered().use(block)
        }
    }
}
