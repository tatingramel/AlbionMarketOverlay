package com.example.albionoverlay

import android.content.Context
import org.json.JSONArray

object WatchlistStore {
    private const val PREFS = "albion_overlay"
    private const val KEY = "watch"

    fun all(ctx: Context): List<String> {
        val s = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        val a = JSONArray(s)
        return List(a.length()) { a.getString(it) }
    }

    fun has(ctx: Context, id: String) = all(ctx).contains(id)

    fun add(ctx: Context, id: String) {
        val l = all(ctx)
        if (!l.contains(id)) save(ctx, l + id)
    }

    fun remove(ctx: Context, id: String) = save(ctx, all(ctx) - id)

    private fun save(ctx: Context, list: List<String>) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, JSONArray(list).toString()).apply()
    }
}
