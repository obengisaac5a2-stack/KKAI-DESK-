package com.kkdesk

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/** A normal Android Activity used to test whether the system permits app UI on a cast display. */
class ExternalDisplayTestActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.rgb(12, 35, 58))
        }
        val heading = TextView(this).apply {
            text = "KK DEX — ANDROID DISPLAY TEST"
            textSize = 28f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        val detail = TextView(this).apply {
            text = "If you can read this on the TV, Android has allowed a normal app Activity to open on the external display.\n\nThis is a real Android Activity, not the Presentation desktop."
            textSize = 19f
            setTextColor(Color.rgb(190, 220, 245))
            gravity = Gravity.CENTER
            setPadding(0, 28, 0, 28)
        }
        val close = TextView(this).apply {
            text = "RETURN"
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(36, 24, 36, 24)
            setBackgroundColor(Color.rgb(35, 100, 150))
            setOnClickListener { finish() }
        }
        root.addView(heading)
        root.addView(detail)
        root.addView(close)
        setContentView(root)
    }
}