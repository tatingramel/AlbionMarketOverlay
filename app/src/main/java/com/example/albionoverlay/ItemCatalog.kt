package com.example.albionoverlay

import android.content.Context
import java.util.Locale

object ItemCatalog {
    private val names = LinkedHashMap<String, String>()
    private val idRegex = Regex("^[A-Z0-9_@]+$")

    fun count() = names.size

    // Reads assets/items.txt (from ao-bin-dumps). Tolerant parser: splits each line on ':'
    // and takes the first part that looks like an item ID (UPPER_CASE with an underscore),
    // then treats whatever follows as the display name.
    fun load(ctx: Context) {
        if (names.isNotEmpty()) return
        try {
            ctx.assets.open("items.txt").bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val parts = line.split(":").map { it.trim() }
                    val idIdx = parts.indexOfFirst { it.contains("_") && idRegex.matches(it) }
                    if (idIdx >= 0) {
                        names[parts[idIdx]] = parts.drop(idIdx + 1).joinToString(":").trim()
                    }
                }
            }
        } catch (e: Exception) {
            // items.txt missing: search by name unavailable, exact IDs still work
        }
    }

    fun label(id: String): String {
        val n = names[id]
        return if (!n.isNullOrBlank()) "$n · $id" else id
    }

    fun search(q: String, limit: Int): List<String> {
        val s = q.trim()
        if (s.isEmpty()) return emptyList()
        val low = s.lowercase(Locale.ROOT)
        val typed = s.uppercase(Locale.ROOT)
        val out = ArrayList<String>()
        if (idRegex.matches(typed) && typed.contains("_") && (names.isEmpty() || names.containsKey(typed))) {
            out.add(typed)
        }
        for ((id, name) in names) {
            if (out.size >= limit) break
            if (id == typed) continue
            if (id.lowercase(Locale.ROOT).contains(low) || name.lowercase(Locale.ROOT).contains(low)) {
                out.add(id)
            }
        }
        return out
    }
}
