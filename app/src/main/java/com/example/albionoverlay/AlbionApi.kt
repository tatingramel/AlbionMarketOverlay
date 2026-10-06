package com.example.albionoverlay

import org.json.JSONArray
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class PriceRow(
    val itemId: String,
    val city: String,
    val quality: Int,
    val sellMin: Int,
    val sellMinDate: Long?,
    val buyMax: Int,
    val buyMaxDate: Long?
)

object AlbionApi {
    // Asia (East). Other servers: https://west.albion-online-data.com , https://europe.albion-online-data.com
    private const val BASE = "https://east.albion-online-data.com"

    // 1 = Normal quality. Change if you want another quality.
    private const val QUALITY = 1

    private const val TTL_MS = 3 * 60 * 1000L

    val CITIES = listOf(
        "Caerleon", "Bridgewatch", "Lymhurst", "Martlock",
        "Thetford", "Fort Sterling", "Brecilien", "Black Market"
    )

    private val cache = HashMap<String, Pair<Long, List<PriceRow>>>()

    @Throws(IOException::class)
    fun fetch(itemIds: List<String>, force: Boolean): List<PriceRow> {
        if (itemIds.isEmpty()) return emptyList()
        val key = itemIds.sorted().joinToString(",")
        val now = System.currentTimeMillis()
        if (!force) {
            synchronized(cache) {
                cache[key]?.let { if (now - it.first < TTL_MS) return it.second }
            }
        }

        val ids = itemIds.joinToString(",")
        val loc = URLEncoder.encode(CITIES.joinToString(","), "UTF-8").replace("+", "%20")
        val url = URL("$BASE/api/v2/stats/prices/$ids.json?locations=$loc&qualities=$QUALITY")

        val conn = url.openConnection() as HttpURLConnection
        // Do NOT set Accept-Encoding manually: Android's HttpURLConnection adds gzip
        // and decompresses transparently only when you leave the header alone.
        conn.connectTimeout = 8000
        conn.readTimeout = 10000
        try {
            val code = conn.responseCode
            if (code != 200) throw IOException("HTTP $code")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val arr = JSONArray(body)
            val rows = ArrayList<PriceRow>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                rows.add(
                    PriceRow(
                        itemId = o.optString("item_id"),
                        city = o.optString("city"),
                        quality = o.optInt("quality"),
                        sellMin = o.optInt("sell_price_min"),
                        sellMinDate = parseDate(o.optString("sell_price_min_date")),
                        buyMax = o.optInt("buy_price_max"),
                        buyMaxDate = parseDate(o.optString("buy_price_max_date"))
                    )
                )
            }
            synchronized(cache) { cache[key] = Pair(now, rows) }
            return rows
        } finally {
            conn.disconnect()
        }
    }

    // Assumes timestamps are UTC like "2026-10-06T12:34:56". "0001-..." means no data.
    private fun parseDate(s: String): Long? {
        if (s.length < 19 || s.startsWith("0001")) return null
        return try {
            val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            f.timeZone = TimeZone.getTimeZone("UTC")
            f.parse(s.substring(0, 19))?.time
        } catch (e: Exception) {
            null
        }
    }
}
