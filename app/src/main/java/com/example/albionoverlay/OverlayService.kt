package com.example.albionoverlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale
import java.util.concurrent.Executors

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var imm: InputMethodManager
    private var root: LinearLayout? = null

    private lateinit var body: ScrollView
    private lateinit var search: EditText
    private lateinit var suggestions: LinearLayout
    private lateinit var title: TextView
    private lateinit var result: TextView
    private lateinit var watchBtn: TextView
    private lateinit var watchBox: LinearLayout

    private val exec = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())
    private var current: String? = null
    private var expanded = true

    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    // Prices older than this are flagged with "!" in the table. Adjust to taste.
    private val STALE_MINUTES = 60L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        ItemCatalog.load(this)
        buildOverlay()
        renderWatchlist()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_NOT_STICKY

    override fun onDestroy() {
        root?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        exec.shutdownNow()
        super.onDestroy()
    }

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("overlay", "Price overlay", NotificationManager.IMPORTANCE_LOW)
        )
        val n = Notification.Builder(this, "overlay")
            .setContentTitle("Albion price overlay running")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
    }

    // ---------- UI helpers ----------

    private fun dp(v: Int) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).toInt()

    private fun tv(label: String, sp: Float = 13f, color: Int = Color.WHITE, onClick: (() -> Unit)? = null) =
        TextView(this).apply {
            text = label
            textSize = sp
            setTextColor(color)
            if (onClick != null) {
                setPadding(dp(6), dp(6), dp(6), dp(6))
                setOnClickListener { onClick() }
            }
        }

    private fun btn(label: String, onClick: () -> Unit) = tv(label, 13f, Color.WHITE, onClick).apply {
        background = GradientDrawable().apply {
            setColor(Color.rgb(60, 60, 75))
            cornerRadius = dp(6).toFloat()
        }
        layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).apply { setMargins(0, dp(4), dp(6), 0) }
    }

    // ---------- Overlay construction ----------

    private fun buildOverlay() {
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.argb(225, 18, 18, 24))
                cornerRadius = dp(10).toFloat()
            }
            setPadding(dp(8), dp(6), dp(8), dp(8))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val handle = tv("☰ Albion prices (drag here)", 13f).apply {
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
            setPadding(0, dp(6), 0, dp(6))
        }
        attachDrag(handle)
        header.addView(handle)
        header.addView(tv("–", 18f, Color.WHITE) { toggleExpanded() })
        header.addView(tv("✕", 16f, Color.WHITE) { stopSelf() })
        r.addView(header)

        val inner = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        search = EditText(this).apply {
            hint = "Search item name or ID"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            textSize = 14f
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_DONE
            setSingleLine(true)
            // The overlay is not focusable by default so the game keeps its touch input.
            // Touching the search box makes the window focusable so the keyboard can appear.
            setOnTouchListener { _, e ->
                if (e.action == MotionEvent.ACTION_DOWN) {
                    setWindowFocusable(true)
                    ui.post {
                        requestFocus()
                        imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
                    }
                }
                false
            }
            setOnEditorActionListener { _, _, _ -> releaseKeyboard(); true }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { showSuggestions(s?.toString() ?: "") }
            })
        }
        inner.addView(search)

        suggestions = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        inner.addView(suggestions)

        title = tv("No item selected", 14f, Color.rgb(255, 214, 102))
        title.setPadding(0, dp(8), 0, dp(4))
        inner.addView(title)

        result = tv("Search an item or tap one from your watchlist.", 11f).apply {
            typeface = Typeface.MONOSPACE
        }
        inner.addView(result)

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        watchBtn = btn("☆ Watch") { toggleWatch() }
        actions.addView(watchBtn)
        actions.addView(btn("⟳ Refresh") { refresh(true) })
        inner.addView(actions)

        val wlLabel = tv("Watchlist", 12f, Color.LTGRAY)
        wlLabel.setPadding(0, dp(10), 0, dp(2))
        inner.addView(wlLabel)
        watchBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        inner.addView(watchBox)

        body = ScrollView(this).apply {
            addView(inner)
            layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f)
        }
        r.addView(body)

        params = WindowManager.LayoutParams(
            dp(310), dp(380),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(8)
            y = dp(80)
        }
        wm.addView(r, params)
        root = r
    }

    private fun attachDrag(v: View) {
        var sx = 0
        var sy = 0
        var tx = 0f
        var ty = 0f
        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = params.x; sy = params.y; tx = e.rawX; ty = e.rawY }
                MotionEvent.ACTION_MOVE -> {
                    params.x = sx + (e.rawX - tx).toInt()
                    params.y = sy + (e.rawY - ty).toInt()
                    root?.let { wm.updateViewLayout(it, params) }
                }
            }
            true
        }
    }

    private fun setWindowFocusable(on: Boolean) {
        params.flags = if (on) params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        else params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        root?.let { wm.updateViewLayout(it, params) }
    }

    private fun releaseKeyboard() {
        imm.hideSoftInputFromWindow(search.windowToken, 0)
        search.clearFocus()
        setWindowFocusable(false)
    }

    private fun toggleExpanded() {
        expanded = !expanded
        if (!expanded) releaseKeyboard()
        body.visibility = if (expanded) View.VISIBLE else View.GONE
        params.height = if (expanded) dp(380) else WindowManager.LayoutParams.WRAP_CONTENT
        root?.let { wm.updateViewLayout(it, params) }
    }

    // ---------- Search, selection, watchlist ----------

    private fun showSuggestions(q: String) {
        suggestions.removeAllViews()
        if (q.trim().length < 2) return
        for (id in ItemCatalog.search(q, 6)) {
            suggestions.addView(tv(ItemCatalog.label(id), 12f, Color.rgb(160, 200, 255)) { select(id) })
        }
    }

    private fun select(id: String) {
        releaseKeyboard()
        search.setText("")
        current = id
        refresh(false)
    }

    private fun toggleWatch() {
        val id = current ?: return
        if (WatchlistStore.has(this, id)) WatchlistStore.remove(this, id) else WatchlistStore.add(this, id)
        updateWatchBtn()
        renderWatchlist()
    }

    private fun updateWatchBtn() {
        val id = current
        watchBtn.text = if (id != null && WatchlistStore.has(this, id)) "★ Remove" else "☆ Watch"
    }

    private fun renderWatchlist() {
        watchBox.removeAllViews()
        for (id in WatchlistStore.all(this)) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val name = tv(ItemCatalog.label(id), 12f, Color.rgb(160, 200, 255)) { select(id) }
            name.layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
            row.addView(name)
            row.addView(tv("✕", 12f, Color.LTGRAY) {
                WatchlistStore.remove(this, id)
                updateWatchBtn()
                renderWatchlist()
            })
            watchBox.addView(row)
        }
    }

    // ---------- Fetch and render ----------

    private fun refresh(force: Boolean) {
        val id = current ?: return
        title.text = ItemCatalog.label(id)
        updateWatchBtn()
        result.text = "Loading…"
        exec.execute {
            try {
                val rows = AlbionApi.fetch(listOf(id), force)
                ui.post { if (current == id) result.text = format(rows) }
            } catch (e: Exception) {
                ui.post { if (current == id) result.text = "Error: ${e.message ?: e.javaClass.simpleName}" }
            }
        }
    }

    private fun money(v: Int) = if (v <= 0) "-" else String.format(Locale.US, "%,d", v)

    private fun age(t: Long?, now: Long): String {
        if (t == null) return "-"
        val m = ((now - t) / 60000).coerceAtLeast(0)
        val s = when {
            m < 60 -> "${m}m"
            m < 1440 -> "${m / 60}h"
            else -> "${m / 1440}d"
        }
        return if (m >= STALE_MINUTES) "$s!" else s
    }

    private fun format(rows: List<PriceRow>): String {
        if (rows.isEmpty()) return "No data returned for this item."
        val now = System.currentTimeMillis()
        val sb = StringBuilder()
        sb.append("City".padEnd(12)).append("Sell".padStart(8)).append(" ")
            .append("Age".padEnd(5)).append("Buy".padStart(8)).append(" ").append("Age").append('\n')
        for (city in AlbionApi.CITIES) {
            val r = rows.firstOrNull { it.city.equals(city, ignoreCase = true) }
            sb.append(city.take(12).padEnd(12))
                .append(money(r?.sellMin ?: 0).padStart(8)).append(" ")
                .append(age(r?.sellMinDate, now).padEnd(5))
                .append(money(r?.buyMax ?: 0).padStart(8)).append(" ")
                .append(age(r?.buyMaxDate, now)).append('\n')
        }
        val lowSell = rows.filter { it.sellMin > 0 }.minByOrNull { it.sellMin }
        val highBuy = rows.filter { it.buyMax > 0 }.maxByOrNull { it.buyMax }
        sb.append('\n')
        sb.append("Lowest sell: ").append(lowSell?.let { "${money(it.sellMin)} @ ${it.city}" } ?: "-").append('\n')
        sb.append("Highest buy: ").append(highBuy?.let { "${money(it.buyMax)} @ ${it.city}" } ?: "-").append('\n')
        sb.append("! = older than ${STALE_MINUTES}m")
        return sb.toString()
    }
}
