package com.kkdesk

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Native Android View-based desktop. Windows are real View hierarchies,
 * not a Canvas illustration. Each window can be focused, moved, resized,
 * minimized, maximized, restored and closed.
 */
internal class WindowDesktopView(context: Context) : FrameLayout(context) {
    private data class WindowState(
        val name: String,
        val view: FrameLayout,
        var minimized: Boolean = false,
        var maximized: Boolean = false,
        var oldWidth: Int = 0,
        var oldHeight: Int = 0,
        var oldX: Int = 0,
        var oldY: Int = 0
    )

    private val windows = mutableListOf<WindowState>()
    private val taskbar = LinearLayout(context)
    private val pointer = PointerView(context)
    private val desktop = LinearLayout(context)
    private var pointerX = 0.5f
    private var pointerY = 0.5f
    private var remoteButtonHeld = false
    private var remoteDownTime = 0L

    init {
        setBackgroundColor(Color.rgb(13, 25, 42))
        buildDesktop()
        addView(pointer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun bg(color: Int, radius: Int = 12): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(1), Color.argb(55, 220, 238, 255))
        }

    private fun text(value: String, size: Float, color: Int = Color.WHITE, bold: Boolean = false): TextView =
        TextView(context).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER_VERTICAL
        }

    private fun button(value: String, action: () -> Unit): TextView =
        text(value, 13f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(12), 0, dp(12), 0)
            background = bg(Color.rgb(43, 69, 96), 8)
            setOnClickListener { action() }
        }

    private fun buildDesktop() {
        val base = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(28), dp(22), dp(28), dp(10))
        }
        val top = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = bg(Color.argb(205, 8, 18, 31), 14)
            setPadding(dp(18), dp(8), dp(18), dp(8))
        }
        top.addView(text("KK", 23f, Color.rgb(126, 205, 255), true))
        top.addView(text(" DEX", 22f, Color.WHITE, true))
        val spacer = View(context)
        top.addView(spacer, LinearLayout.LayoutParams(0, dp(1), 1f))
        top.addView(text("DESKTOP WORKSPACE   •   READY", 12f, Color.rgb(177, 198, 220), true))
        base.addView(top, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(52)))

        val body = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(24), 0, dp(12))
        }
        val shortcuts = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        listOf("Files", "Browser", "Notes", "Settings").forEach { name ->
            val tile = button("▣   $name") { openWindow(name) }
            tile.gravity = Gravity.CENTER_VERTICAL
            shortcuts.addView(tile, LinearLayout.LayoutParams(dp(145), dp(54)).apply {
                bottomMargin = dp(12)
            })
        }
        body.addView(shortcuts)
        val welcome = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = bg(Color.argb(155, 25, 45, 67), 20)
            setPadding(dp(34), dp(24), dp(34), dp(24))
        }
        welcome.addView(text("Your workspace. Your windows.", 30f, Color.WHITE, true))
        welcome.addView(text("Open applications, arrange windows and work across them.", 16f, Color.rgb(188, 207, 227)))
        welcome.addView(text("This is a native window system — not a drawn desktop mock-up.", 14f, Color.rgb(130, 202, 245)))
        val open = button("OPEN APPLICATION LAUNCHER") { openLauncher() }
        welcome.addView(open, LinearLayout.LayoutParams(dp(260), dp(48)).apply { topMargin = dp(28) })
        body.addView(welcome, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply {
            leftMargin = dp(24)
        })
        base.addView(body, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        taskbar.orientation = LinearLayout.HORIZONTAL
        taskbar.gravity = Gravity.CENTER_VERTICAL
        taskbar.setPadding(dp(12), dp(6), dp(12), dp(6))
        taskbar.background = bg(Color.argb(235, 7, 15, 26), 14)
        val start = button("KK  Apps") { openLauncher() }
        taskbar.addView(start, LinearLayout.LayoutParams(dp(105), dp(42)))
        base.addView(taskbar, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(54)))
        addView(base, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        // Keep the cursor above the desktop and all application windows.
        removeView(pointer)
        addView(pointer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    private fun openLauncher() {
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = bg(Color.rgb(20, 34, 51), 18)
            setPadding(dp(20), dp(18), dp(20), dp(18))
        }
        panel.addView(text("APPLICATIONS", 17f, Color.rgb(139, 211, 255), true))
        listOf("Files", "Browser", "Notes", "Settings").forEach { name ->
            panel.addView(button(name) { removeView(panel); openWindow(name) },
                LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(46)).apply { topMargin = dp(8) })
        }
        val lp = LayoutParams(dp(300), LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.LEFT)
        lp.leftMargin = dp(28)
        lp.bottomMargin = dp(82)
        addView(panel, lp)
        panel.bringToFront()
        pointer.bringToFront()
    }

    private fun openWindow(name: String) {
        windows.firstOrNull { it.name == name }?.let {
            it.minimized = false
            it.view.visibility = View.VISIBLE
            it.view.bringToFront()
            pointer.bringToFront()
            refreshTaskbar()
            return
        }
        val shell = FrameLayout(context).apply {
            background = bg(Color.rgb(23, 37, 54), 14)
            elevation = dp(18).toFloat()
            isClickable = true
        }
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val titlebar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(8), 0)
            background = bg(Color.rgb(31, 51, 72), 12)
        }
        titlebar.addView(text(name, 15f, Color.WHITE, true),
            LinearLayout.LayoutParams(0, dp(48), 1f))
        titlebar.addView(button("—") { minimize(name) }, LinearLayout.LayoutParams(dp(42), dp(34)).apply { rightMargin = dp(5) })
        titlebar.addView(button("□") { maximize(name) }, LinearLayout.LayoutParams(dp(42), dp(34)).apply { rightMargin = dp(5) })
        titlebar.addView(button("×") { closeWindow(name) }, LinearLayout.LayoutParams(dp(42), dp(34)))
        column.addView(titlebar)
        column.addView(makeAppContent(name), LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        shell.addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        val resize = text("◢", 18f, Color.rgb(174, 205, 230), true).apply {
            gravity = Gravity.CENTER
            setOnTouchListener { v, e ->
                val state = windows.firstOrNull { it.name == name } ?: return@setOnTouchListener true
                val lp = shell.layoutParams as LayoutParams
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        state.oldWidth = lp.width; state.oldHeight = lp.height
                        state.oldX = e.rawX.toInt(); state.oldY = e.rawY.toInt()
                    }
                    MotionEvent.ACTION_MOVE -> {
                        lp.width = (state.oldWidth + e.rawX.toInt() - state.oldX).coerceAtLeast(dp(420))
                        lp.height = (state.oldHeight + e.rawY.toInt() - state.oldY).coerceAtLeast(dp(280))
                        shell.layoutParams = lp
                    }
                }
                true
            }
        }
        shell.addView(resize, LayoutParams(dp(30), dp(30), Gravity.RIGHT or Gravity.BOTTOM))
        val state = WindowState(name, shell)
        windows.add(state)
        titlebar.setOnTouchListener { _, e ->
            if (state.maximized) return@setOnTouchListener false
            val lp = shell.layoutParams as LayoutParams
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    state.oldX = e.rawX.toInt() - lp.leftMargin
                    state.oldY = e.rawY.toInt() - lp.topMargin
                    shell.bringToFront()
                }
                MotionEvent.ACTION_MOVE -> {
                    lp.gravity = Gravity.TOP or Gravity.LEFT
                    lp.leftMargin = (e.rawX.toInt() - state.oldX).coerceAtLeast(0)
                    lp.topMargin = (e.rawY.toInt() - state.oldY).coerceAtLeast(0)
                    shell.layoutParams = lp
                }
            }
            true
        }
        val width = (resources.displayMetrics.widthPixels * 0.72f).toInt().coerceAtLeast(dp(560))
        val height = (resources.displayMetrics.heightPixels * 0.68f).toInt().coerceAtLeast(dp(360))
        val lp = LayoutParams(width, height, Gravity.CENTER)
        addView(shell, lp)
        shell.bringToFront()
        pointer.bringToFront()
        refreshTaskbar()
    }

    private fun makeAppContent(name: String): View {
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(18), dp(22), dp(22))
        }
        when (name) {
            "Files" -> {
                content.addView(text("PHONE STORAGE", 12f, Color.rgb(132, 198, 238), true))
                listOf("Internal storage", "Downloads", "Documents", "Pictures", "Music", "Movies").forEach {
                    content.addView(button("▰    $it     ›") {},
                        LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(7) })
                }
            }
            "Browser" -> {
                val address = EditText(context).apply {
                    hint = "Enter a website or search"
                    singleLine = true
                    setTextColor(Color.WHITE)
                    setHintTextColor(Color.LTGRAY)
                    setBackground(bg(Color.rgb(38, 55, 74), 8))
                }
                content.addView(address, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(48)))
                val web = WebView(context).apply {
                    webViewClient = WebViewClient()
                    settings.javaScriptEnabled = false
                }
                content.addView(button("GO") {
                    var url = address.text.toString().trim()
                    if (url.isNotEmpty()) {
                        if (!url.contains("://")) url = if (url.contains(".")) "https://$url" else "https://www.google.com/search?q=" + android.net.Uri.encode(url)
                        web.loadUrl(url)
                    }
                }, LinearLayout.LayoutParams(dp(90), dp(40)).apply { topMargin = dp(8) })
                content.addView(web, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(10) })
            }
            "Notes" -> {
                content.addView(text("QUICK NOTE", 12f, Color.rgb(132, 198, 238), true))
                val note = EditText(context).apply {
                    hint = "Start typing..."
                    gravity = Gravity.TOP
                    setTextColor(Color.WHITE)
                    setHintTextColor(Color.GRAY)
                    setPadding(dp(12), dp(12), dp(12), dp(12))
                    background = bg(Color.rgb(31, 48, 67), 10)
                    setText(context.getSharedPreferences("kkdex_notes", Context.MODE_PRIVATE).getString("note", ""))
                }
                content.addView(note, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(10) })
                content.addView(button("SAVE NOTE") {
                    context.getSharedPreferences("kkdex_notes", Context.MODE_PRIVATE).edit().putString("note", note.text.toString()).apply()
                }, LinearLayout.LayoutParams(dp(140), dp(44)).apply { topMargin = dp(10) })
            }
            "Settings" -> {
                content.addView(text("DESKTOP SETTINGS", 13f, Color.rgb(132, 198, 238), true))
                content.addView(text("Window controls are active. Use the title bar to move a window, □ to maximize, — to minimize, and × to close.", 17f))
                content.addView(text("KK Dex is running as an independent desktop on the external display.", 14f, Color.LTGRAY))
            }
        }
        return ScrollView(context).apply { addView(content) }
    }

    private fun minimize(name: String) {
        windows.firstOrNull { it.name == name }?.let { it.minimized = true; it.view.visibility = View.GONE }
        refreshTaskbar()
    }

    private fun maximize(name: String) {
        val state = windows.firstOrNull { it.name == name } ?: return
        val lp = state.view.layoutParams as LayoutParams
        if (!state.maximized) {
            state.oldWidth = lp.width; state.oldHeight = lp.height
            state.oldX = lp.leftMargin; state.oldY = lp.topMargin
            lp.width = LayoutParams.MATCH_PARENT; lp.height = LayoutParams.MATCH_PARENT
            lp.gravity = Gravity.TOP or Gravity.LEFT
            lp.setMargins(dp(20), dp(68), dp(20), dp(72))
        } else {
            lp.width = state.oldWidth; lp.height = state.oldHeight
            lp.gravity = Gravity.CENTER
            lp.setMargins(0, 0, 0, 0)
        }
        state.maximized = !state.maximized
        state.view.layoutParams = lp
        state.view.bringToFront()
        pointer.bringToFront()
    }

    private fun closeWindow(name: String) {
        val state = windows.firstOrNull { it.name == name } ?: return
        removeView(state.view)
        windows.remove(state)
        refreshTaskbar()
    }

    private fun refreshTaskbar() {
        taskbar.removeViews(1, (taskbar.childCount - 1).coerceAtLeast(0))
        windows.forEach { state ->
            val item = button((if (state.minimized) "◻ " else "▰ ") + state.name) {
                state.minimized = false
                state.view.visibility = View.VISIBLE
                state.view.bringToFront()
                pointer.bringToFront()
            }
            taskbar.addView(item, LinearLayout.LayoutParams(dp(112), dp(42)).apply { leftMargin = dp(7) })
        }
    }

    fun updatePointer(x: Float, y: Float, clicked: Boolean) {
        pointerX = x.coerceIn(0f, 1f)
        pointerY = y.coerceIn(0f, 1f)
        pointer.invalidate()
        if (width > 0 && height > 0) {
            if (remoteButtonHeld) {
                sendPointerEvent(MotionEvent.ACTION_MOVE)
            } else if (clicked) {
                val now = android.os.SystemClock.uptimeMillis()
                sendPointerEvent(MotionEvent.ACTION_DOWN, now)
                sendPointerEvent(MotionEvent.ACTION_UP, now + 40)
            }
        }
    }

    fun setPointerButtonHeld(held: Boolean) {
        if (remoteButtonHeld == held || width == 0 || height == 0) return
        remoteButtonHeld = held
        if (held) {
            remoteDownTime = android.os.SystemClock.uptimeMillis()
            sendPointerEvent(MotionEvent.ACTION_DOWN, remoteDownTime)
        } else {
            sendPointerEvent(MotionEvent.ACTION_UP)
        }
    }

    private fun sendPointerEvent(action: Int, eventTime: Long = android.os.SystemClock.uptimeMillis()) {
        val px = pointerX * width
        val py = pointerY * height
        val downTime = if (remoteButtonHeld && remoteDownTime != 0L) remoteDownTime else eventTime
        val event = MotionEvent.obtain(downTime, eventTime, action, px, py, 0)
        dispatchTouchEvent(event)
        event.recycle()
    }

    private inner class PointerView(ctx: Context) : View(ctx) {
        private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: android.graphics.Canvas) {
            super.onDraw(canvas)
            val x = pointerX * width
            val y = pointerY * height
            paint.color = Color.BLACK
            paint.style = android.graphics.Paint.Style.STROKE
            paint.strokeWidth = dp(3).toFloat()
            val path = android.graphics.Path().apply {
                moveTo(x, y); lineTo(x, y + dp(24)); lineTo(x + dp(7), y + dp(18))
                lineTo(x + dp(14), y + dp(31)); lineTo(x + dp(20), y + dp(28))
                lineTo(x + dp(13), y + dp(15)); lineTo(x + dp(23), y + dp(14)); close()
            }
            canvas.drawPath(path, paint)
            paint.color = Color.WHITE
            paint.style = android.graphics.Paint.Style.FILL
            canvas.drawPath(path, paint)
        }
    }
}
