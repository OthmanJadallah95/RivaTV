package com.gguard.tv.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

object ProfileStore {
    private lateinit var prefs: SharedPreferences

    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences("profiles", Context.MODE_PRIVATE)
    }

    fun all(): List<Profile> {
        val a = try { JSONArray(prefs.getString("list", "[]")) } catch (e: Exception) { JSONArray() }
        return (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            Profile(
                id = o.optString("id"),
                name = o.optString("name"),
                type = runCatching { ProfileType.valueOf(o.optString("type")) }.getOrDefault(ProfileType.XTREAM),
                server = o.optString("server"),
                username = o.optString("username"),
                password = o.optString("password"),
                m3uUrl = o.optString("m3uUrl"),
            )
        }
    }

    private fun saveAll(list: List<Profile>) {
        val a = JSONArray()
        list.forEach { p ->
            a.put(JSONObject().apply {
                put("id", p.id); put("name", p.name); put("type", p.type.name)
                put("server", p.server); put("username", p.username); put("password", p.password)
                put("m3uUrl", p.m3uUrl)
            })
        }
        prefs.edit().putString("list", a.toString()).apply()
    }

    fun add(p: Profile) = saveAll(all().filter { it.id != p.id } + p)

    fun remove(id: String) {
        saveAll(all().filter { it.id != id })
        if (lastId == id) lastId = null
    }

    var lastId: String?
        get() = prefs.getString("last", null)
        set(v) { prefs.edit().putString("last", v).apply() }
}
