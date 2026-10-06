package com.example.albionoverlay

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        status = TextView(this).apply { textSize = 15f }
        col.addView(status)

        col.addView(Button(this).apply {
            text = "1. Grant overlay permission"
            setOnClickListener {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                )
            }
        })
        col.addView(Button(this).apply {
            text = "2. Start overlay"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    Toast.makeText(this@MainActivity, "Grant overlay permission first", Toast.LENGTH_LONG).show()
                } else {
                    startForegroundService(Intent(this@MainActivity, OverlayService::class.java))
                }
            }
        })
        col.addView(Button(this).apply {
            text = "Stop overlay"
            setOnClickListener { stopService(Intent(this@MainActivity, OverlayService::class.java)) }
        })
        setContentView(col)

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        ItemCatalog.load(this)
        val perm = if (Settings.canDrawOverlays(this)) "granted" else "NOT granted"
        val items = if (ItemCatalog.count() > 0) "${ItemCatalog.count()} items loaded"
        else "items.txt not found (name search disabled; exact IDs still work)"
        status.text = "Overlay permission: $perm\nItem list: $items\nServer: Asia (East)"
    }
}
