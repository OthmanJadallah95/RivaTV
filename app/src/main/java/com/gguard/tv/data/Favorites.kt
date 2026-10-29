package com.gguard.tv.data

import android.content.Context
import android.content.SharedPreferences

object Favorites {
    private lateinit var prefs: SharedPreferences
    private var cache: MutableSet<String> = mutableSetOf()

    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences("favorites", Context.MODE_PRIVATE)
        cache = prefs.getStringSet("keys", emptySet())!!.toMutableSet()
    }

    private fun key(item: StreamItem) = "${Session.profile?.id}|${item.type}|${item.id}"

    fun isFav(item: StreamItem) = key(item) in cache

    /** Returns true when the item is now a favorite. */
    fun toggle(item: StreamItem): Boolean {
        val k = key(item)
        val added = if (k in cache) { cache.remove(k); false } else { cache.add(k); true }
        prefs.edit().putStringSet("keys", HashSet(cache)).apply()
        return added
    }
}
