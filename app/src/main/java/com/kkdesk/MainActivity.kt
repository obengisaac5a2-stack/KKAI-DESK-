package com.kkdesk

import android.app.Activity
import android.app.Presentation
import android.app.ActivityOptions
import android.content.Intent
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.Bundle
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.Button
import android.widget.TextView
import android.hardware.display.DisplayManager

class MainActivity : Activity(), DisplayManager.DisplayListener {
    private lateinit var displayManager: DisplayManager
    private var desktopPresentation: DesktopPresentation? = null
    private lateinit var status: TextView
    private var pointerX = 0.5f
    private var pointerY = 0.5f
    private var pointerButtonHeld = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        buildController()
        displayManager.registerDisplayListener(this, null)
        refreshExternalDisplay()
    }

    private fun buildController() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 26, 22, 20)
            setBackgroundColor(0xFF101827.toInt())
        }
        val title = TextView(this).apply {
            text = "KKAI-DESK"
            textSize = 27f
            setTextColor(0xFFFFFFFF.toInt())
        }
        status = TextView(this).apply {
            text = "Looking for an external screen…"
            textSize = 15f
            setTextColor(0xFFB9C7DA.toInt())
            setPadding(0, 10, 0, 12)
        }
        val instruction = TextView(this).apply {
            text = "TOUCHPAD  •  Move one finger to move the TV pointer. Tap once to click."
            textSize = 14f
            setTextColor(0xFFEAF1FA.toInt())
            setPadding(0, 4, 0, 12)
        }
        val trackpad = TouchpadView(this) { dx, dy, clicked ->
            pointerX = (pointerX + dx).coerceIn(0.015f, 0.985f)
            pointerY = (pointerY + dy).coerceIn(0.04f, 0.96f)
            desktopPresentation?.updatePointer(pointerX, pointerY, clicked)
        }
        root.addView(title)
        root.addView(status)
        root.addView(instruction)
        val holdButton = Button(this).apply {
            text = "HOLD LEFT MOUSE BUTTON: OFF"
            setOnClickListener {
                pointerButtonHeld = !pointerButtonHeld
                text = if (pointerButtonHeld) "HOLD LEFT MOUSE BUTTON: ON" else "HOLD LEFT MOUSE BUTTON: OFF"
                desktopPresentation?.setPointerButtonHeld(pointerButtonHeld)
            }
        }
        root.addView(holdButton)
        root.addView(trackpad, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        val hint = TextView(this).apply {
            text = "Your phone is now the mouse. The separate desktop appears on your TV."
            textSize = 13f
            setTextColor(0xFF9FB0C8.toInt())
            setPadding(0, 12, 0, 0)
            gravity = Gravity.CENTER
        }
        root.addView(hint)
        setContentView(root)
    }

    private fun refreshExternalDisplay() {
        val display = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            .firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
        if (display == null) {
            desktopPresentation?.dismiss()
            desktopPresentation = null
            status.text = "No external screen detected. Connect your phone to a supported TV or monitor."
        } else {
            status.text = "TV: ${display.name} • ID ${display.displayId} • flags=${display.flags} • state=${display.state} • pointer ready"
            if (desktopPresentation?.display?.displayId != display.displayId) {
                desktopPresentation?.dismiss()
                desktopPresentation = DesktopPresentation(this, display) { packageName ->
                    launchInstalledAppOnDisplay(packageName, display.displayId)
                }.also {
                    try {
                        it.show()
                        it.updatePointer(pointerX, pointerY, false)
                        it.setPointerButtonHeld(pointerButtonHeld)
                    } catch (error: Exception) {
                        status.text = "Display found, but desktop could not start: " + error.localizedMessage
                        desktopPresentation = null
                    }
                }
            }
        }
    }

    private fun testActivityOnExternalDisplay() {
        val display = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            .firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
        if (display == null) {
            status.text = "TEST: No presentation display is currently available."
            return
        }
        try {
            val intent = Intent(this, ExternalDisplayTestActivity::class.java)
            val options = ActivityOptions.makeBasic().setLaunchDisplayId(display.displayId)
            startActivity(intent, options.toBundle())
            status.text = "TEST SENT to display ${display.displayId}. Check whether the KK Dex test screen appears on the TV. Android accepting the request is not proof yet."
        } catch (error: Exception) {
            status.text = "TEST FAILED: ${error.javaClass.simpleName}: ${error.localizedMessage}"
        }
    }

    private fun launchInstalledAppOnDisplay(packageName: String, displayId: Int) {
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent == null) {
                status.text = "This app does not provide a launch screen."
                return
            }
            val options = ActivityOptions.makeBasic().setLaunchDisplayId(displayId)
            startActivity(launchIntent, options.toBundle())
            status.text = "Requested app launch on external display (ID $displayId)."
        } catch (error: SecurityException) {
            status.text = "Android blocked this app from opening on the TV: ${error.localizedMessage}"
        } catch (error: Exception) {
            status.text = "Could not launch app on TV: ${error.localizedMessage}"
        }
    }

    override fun onDisplayAdded(displayId: Int) = refreshExternalDisplay()
    override fun onDisplayRemoved(displayId: Int) = refreshExternalDisplay()
    override fun onDisplayChanged(displayId: Int) = refreshExternalDisplay()

    override fun onDestroy() {
        displayManager.unregisterDisplayListener(this)
        desktopPresentation?.dismiss()
        desktopPresentation = null
        super.onDestroy()
    }
}

private class TouchpadView(
    context: Context,
    private val onPointerInput: (Float, Float, Boolean) -> Unit
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var lastX = 0f
    private var lastY = 0f
    private var moved = false

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), 28f, 28f,
            paint.apply { color = 0xFF1B2940.toInt() })
        paint.color = 0xFF405675.toInt()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        canvas.drawRoundRect(5f, 5f, width - 5f, height - 5f, 24f, 24f, paint)
        paint.style = Paint.Style.FILL
        paint.color = 0xFFB9C7DA.toInt()
        paint.textSize = 18f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("TOUCH HERE TO MOVE THE POINTER", width / 2f, height / 2f, paint)
        paint.color = 0xFF8195B2.toInt()
        paint.textSize = 13f
        canvas.drawText("Tap to click  •  Drag to move", width / 2f, height / 2f + 30f, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                moved = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.x - lastX) / width.coerceAtLeast(1) * 1.55f
                val dy = (event.y - lastY) / height.coerceAtLeast(1) * 1.55f
                if (kotlin.math.abs(dx) + kotlin.math.abs(dy) > 0.001f) moved = true
                lastX = event.x
                lastY = event.y
                onPointerInput(dx, dy, false)
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!moved) onPointerInput(0f, 0f, true)
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> return true
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}

private class DesktopPresentation(
    context: Context,
    display: Display,
    private val onLaunchApp: (String) -> Unit
) : Presentation(context, display) {
    private var desktopView: WindowDesktopView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep the first external-display render deliberately simple and visible.
        // Immersive/system-bar flags are applied only after the desktop is confirmed
        // visible; some cast receivers otherwise show a blank output surface.
        val view = WindowDesktopView(context)
        desktopView = view
        setContentView(view)

        window?.apply {
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0f)
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(
                android.graphics.Color.rgb(13, 25, 42)
            ))
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
            decorView.setBackgroundColor(android.graphics.Color.rgb(13, 25, 42))
            decorView.systemUiVisibility = 0
        }
    }

    fun updatePointer(x: Float, y: Float, clicked: Boolean) {
        desktopView?.updatePointer(x, y, clicked)
    }

    fun setPointerButtonHeld(held: Boolean) {
        desktopView?.setPointerButtonHeld(held)
    }
}

private class DesktopView(
    context: Context,
    private val onLaunchApp: (String) -> Unit
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val appPackageManager = context.packageManager
    private val installedApps = appPackageManager.queryIntentActivities(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
    ).filter { it.activityInfo.packageName != context.packageName }
        .distinctBy { it.activityInfo.packageName }
        .sortedBy { it.loadLabel(context.packageManager).toString().lowercase() }
        .take(8)
    private var pointerX = 0.5f
    private var pointerY = 0.5f
    private var clickFlash = false
    private var page = "HOME"
    private var startOpen = false
    private var wallpaper = 0

    fun updatePointer(x: Float, y: Float, clicked: Boolean) {
        pointerX = x
        pointerY = y
        clickFlash = clicked
        if (clicked) handleClick(x * 1280f, y * 720f)
        invalidate()
    }

    private fun handleClick(x: Float, y: Float) {
        if (startOpen) {
            if (x in 28f..270f && y in 420f..680f) {
                page = when {
                    y < 505f -> "FILES"
                    y < 565f -> "BROWSER"
                    else -> "SETTINGS"
                }
                startOpen = false
                return
            }
            startOpen = false
        }
        if (y > 650f) {
            if (x < 125f) {
                startOpen = !startOpen
                return
            }
            if (x in 130f..280f) page = "FILES"
            if (x in 280f..430f) page = "BROWSER"
            if (x in 430f..590f) page = "SETTINGS"
            if (x in 585f..735f) page = "APPS"
            return
        }
        if (page == "HOME") {
            if (x in 35f..180f) {
                if (y in 95f..220f) page = "FILES"
                else if (y in 225f..350f) page = "BROWSER"
                else if (y in 355f..470f) page = "SETTINGS"
                else if (y in 475f..600f) page = "APPS"
            }
        } else {
            if (x > 1160f && y in 65f..125f) {
                page = "HOME"
                return
            }
            if (page == "SETTINGS" && x in 330f..950f && y in 250f..570f) {
                wallpaper = (wallpaper + 1) % 3
            }
            if (page == "APPS" && x in 265f..1170f && y in 185f..610f) {
                val index = ((y - 195f) / 52f).toInt()
                installedApps.getOrNull(index)?.let {
                    onLaunchApp(it.activityInfo.packageName)
                }
            }
            if (page == "BROWSER" && x in 330f..1080f && y in 150f..220f) {
                // The address field is a visual prototype; phone keyboard is planned for the next test.
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val sx = width / 1280f
        val sy = height / 720f
        canvas.save()
        canvas.scale(sx, sy)
        val backgrounds = intArrayOf(0xFF0B1728.toInt(), 0xFF10251F.toInt(), 0xFF211A35.toInt())
        canvas.drawColor(backgrounds[wallpaper])
        drawTopBar(canvas)
        if (page == "HOME") drawHome(canvas) else drawAppPage(canvas)
        drawTaskbar(canvas)
        if (startOpen) drawStartMenu(canvas)
        drawPointer(canvas)
        canvas.restore()
    }

    private fun drawTopBar(canvas: Canvas) {
        paint.color = 0xD9111D30.toInt()
        canvas.drawRect(0f, 0f, 1280f, 58f, paint)
        text(canvas, "KK", 30f, 38f, 27f, 0xFF8ED8FF.toInt(), true)
        text(canvas, "DEX", 70f, 38f, 25f, 0xFFFFFFFF.toInt(), true)
        text(canvas, "YOUR PHONE. YOUR DESKTOP.", 155f, 35f, 13f, 0xFF9FB0C8.toInt())
        text(canvas, "●  CONNECTED", 1090f, 35f, 14f, 0xFF77E0B0.toInt(), true)
    }

    private fun drawHome(canvas: Canvas) {
        drawShortcut(canvas, 48f, 100f, "Files", 0xFF347DD1.toInt(), "▤")
        drawShortcut(canvas, 48f, 225f, "Browser", 0xFF238E83.toInt(), "◎")
        drawShortcut(canvas, 48f, 350f, "Settings", 0xFF7669C8.toInt(), "⚙")
        drawShortcut(canvas, 48f, 475f, "Phone Apps", 0xFFB06A42.toInt(), "▦")
        roundRect(canvas, 285f, 145f, 1190f, 575f, 28f, 0xC51B2C45.toInt())
        text(canvas, "Welcome to KK Dex", 340f, 230f, 39f, 0xFFFFFFFF.toInt(), true)
        text(canvas, "A desktop experience, powered by your phone.", 340f, 278f, 21f, 0xFFBDCCE0.toInt())
        text(canvas, "Choose an application to get started", 340f, 355f, 17f, 0xFF8FA8C6.toInt())
        drawPill(canvas, 340f, 390f, 235f, 50f, "OPEN FILES", 0xFF245D91.toInt())
        text(canvas, "PROTOTYPE 0.4  •  ANDROID APP LAUNCH TEST", 340f, 520f, 14f, 0xFF7F9AB9.toInt())
        text(canvas, "Open Phone Apps to test real apps on this external display.", 340f, 555f, 16f, 0xFFB9C7DA.toInt())
    }

    private fun drawAppPage(canvas: Canvas) {
        roundRect(canvas, 235f, 82f, 1225f, 625f, 22f, 0xF0182940.toInt())
        val title = when (page) {
            "FILES" -> "Files"
            "BROWSER" -> "Browser"
            "APPS" -> "Phone Apps"
            else -> "Settings"
        }
        text(canvas, title, 275f, 130f, 29f, 0xFFFFFFFF.toInt(), true)
        roundRect(canvas, 1165f, 92f, 1205f, 132f, 10f, 0xFF344A66.toInt())
        text(canvas, "×", 1178f, 121f, 27f, 0xFFFFFFFF.toInt(), true)
        when (page) {
            "FILES" -> {
                text(canvas, "PHONE FILES", 275f, 185f, 14f, 0xFF8FA8C6.toInt(), true)
                val labels = arrayOf("Pictures", "Camera", "Screenshots", "Videos", "Downloads", "Documents")
                for (i in labels.indices) {
                    val col = i % 3
                    val row = i / 3
                    val left = 275f + col * 285f
                    val top = 220f + row * 135f
                    roundRect(canvas, left, top, left + 250f, top + 105f, 15f, 0xFF243A55.toInt())
                    text(canvas, "▣", left + 20f, top + 42f, 25f, 0xFF8ED8FF.toInt())
                    text(canvas, labels[i], left + 62f, top + 43f, 18f, 0xFFFFFFFF.toInt(), true)
                }
                text(canvas, "Prototype folders only — real phone-file access comes in a later integration test.", 275f, 545f, 14f, 0xFFFFD28A.toInt())
            }
            "BROWSER" -> {
                text(canvas, "WEB BROWSER", 275f, 185f, 14f, 0xFF8FA8C6.toInt(), true)
                roundRect(canvas, 275f, 205f, 1175f, 265f, 14f, 0xFF263B55.toInt())
                text(canvas, "Search or enter website address…", 305f, 242f, 18f, 0xFFB9C7DA.toInt())
                roundRect(canvas, 275f, 290f, 1175f, 550f, 16f, 0xFF20334B.toInt())
                text(canvas, "Your browser workspace", 325f, 365f, 28f, 0xFFFFFFFF.toInt(), true)
                text(canvas, "The phone keyboard and real browsing will be added in later tests.", 325f, 410f, 17f, 0xFFB9C7DA.toInt())
            }
            "APPS" -> {
                text(canvas, "REAL APPS INSTALLED ON THIS PHONE", 275f, 175f, 14f, 0xFF8FA8C6.toInt(), true)
                if (installedApps.isEmpty()) {
                    text(canvas, "No launchable apps were found.", 275f, 235f, 19f, 0xFFFFFFFF.toInt())
                } else {
                    installedApps.forEachIndexed { index, info ->
                        val y = 195f + index * 52f
                        roundRect(canvas, 275f, y, 1145f, y + 44f, 10f, 0xFF263B55.toInt())
                        val label = info.loadLabel(appPackageManager).toString()
                        text(canvas, label.take(42), 295f, y + 28f, 17f, 0xFFFFFFFF.toInt(), true)
                        text(canvas, "OPEN ON TV  ›", 985f, y + 28f, 13f, 0xFF8ED8FF.toInt(), true)
                    }
                }
                text(canvas, "Tap an app. Android may block some apps from launching on a cast display.", 275f, 625f, 13f, 0xFFFFD28A.toInt())
            }
            "SETTINGS" -> {
                text(canvas, "PERSONALIZE YOUR DESKTOP", 275f, 185f, 14f, 0xFF8FA8C6.toInt(), true)
                settingRow(canvas, 275f, 215f, "Wallpaper", "Tap to cycle desktop backgrounds")
                settingRow(canvas, 275f, 305f, "Appearance", "KK Dex dark theme")
                settingRow(canvas, 275f, 395f, "Display", "External screen desktop mode")
                settingRow(canvas, 275f, 485f, "About", "KK Dex prototype 0.3")
            }
        }
    }

    private fun settingRow(canvas: Canvas, x: Float, y: Float, title: String, detail: String) {
        roundRect(canvas, x, y, 1145f, y + 72f, 13f, 0xFF263B55.toInt())
        text(canvas, title, x + 22f, y + 29f, 18f, 0xFFFFFFFF.toInt(), true)
        text(canvas, detail, x + 22f, y + 52f, 13f, 0xFFB9C7DA.toInt())
        text(canvas, "›", 1105f, y + 46f, 27f, 0xFF8ED8FF.toInt())
    }

    private fun drawTaskbar(canvas: Canvas) {
        paint.color = 0xEE101B2B.toInt()
        canvas.drawRect(0f, 650f, 1280f, 720f, paint)
        drawPill(canvas, 22f, 662f, 92f, 46f, "KK", 0xFF245D91.toInt())
        taskItem(canvas, 135f, "Files", page == "FILES")
        taskItem(canvas, 285f, "Browser", page == "BROWSER")
        taskItem(canvas, 435f, "Settings", page == "SETTINGS")
        taskItem(canvas, 585f, "Phone Apps", page == "APPS")
        text(canvas, "KK DEX", 1040f, 692f, 13f, 0xFF8FA8C6.toInt(), true)
        text(canvas, "●", 1140f, 692f, 13f, 0xFF77E0B0.toInt())
        text(canvas, "DESKTOP", 1160f, 692f, 12f, 0xFFB9C7DA.toInt())
    }

    private fun taskItem(canvas: Canvas, x: Float, label: String, active: Boolean) {
        val color = if (active) 0xFF2D4968.toInt() else 0xFF1A2A40.toInt()
        roundRect(canvas, x, 662f, x + 135f, 708f, 10f, color)
        text(canvas, label, x + 15f, 691f, 15f, 0xFFFFFFFF.toInt(), active)
    }

    private fun drawStartMenu(canvas: Canvas) {
        roundRect(canvas, 22f, 405f, 275f, 640f, 18f, 0xF21A2A40.toInt())
        text(canvas, "APPLICATIONS", 45f, 440f, 13f, 0xFF8FA8C6.toInt(), true)
        text(canvas, "▤   Files", 45f, 485f, 18f, 0xFFFFFFFF.toInt())
        text(canvas, "◎   Browser", 45f, 545f, 18f, 0xFFFFFFFF.toInt())
        text(canvas, "⚙   Settings", 45f, 605f, 18f, 0xFFFFFFFF.toInt())
    }

    private fun drawShortcut(canvas: Canvas, x: Float, y: Float, label: String, color: Int, symbol: String) {
        roundRect(canvas, x, y, x + 66f, y + 66f, 17f, color)
        paint.textAlign = Paint.Align.CENTER
        text(canvas, symbol, x + 33f, y + 44f, 31f, 0xFFFFFFFF.toInt(), true, Paint.Align.CENTER)
        text(canvas, label, x + 33f, y + 91f, 15f, 0xFFFFFFFF.toInt(), false, Paint.Align.CENTER)
        paint.textAlign = Paint.Align.LEFT
    }

    private fun drawPill(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, label: String, color: Int) {
        roundRect(canvas, x, y, x + w, y + h, 12f, color)
        text(canvas, label, x + w / 2f, y + h / 2f + 6f, 15f, 0xFFFFFFFF.toInt(), true, Paint.Align.CENTER)
    }

    private fun roundRect(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, radius: Float, color: Int) {
        paint.color = color
        canvas.drawRoundRect(l, t, r, b, radius, radius, paint)
    }

    private fun text(
        canvas: Canvas, value: String, x: Float, y: Float, size: Float,
        color: Int, bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT
    ) {
        paint.color = color
        paint.textSize = size
        paint.textAlign = align
        paint.typeface = if (bold) android.graphics.Typeface.create("sans-serif-medium", 0)
            else android.graphics.Typeface.create("sans-serif", 0)
        canvas.drawText(value, x, y, paint)
        paint.textAlign = Paint.Align.LEFT
    }

    private fun drawPointer(canvas: Canvas) {
        val px = pointerX * 1280f
        val py = pointerY * 720f
        val pointer = Path().apply {
            moveTo(px, py)
            lineTo(px + 3f, py + 29f)
            lineTo(px + 10f, py + 22f)
            lineTo(px + 18f, py + 39f)
            lineTo(px + 25f, py + 35f)
            lineTo(px + 17f, py + 18f)
            lineTo(px + 29f, py + 17f)
            close()
        }
        paint.color = 0xFF000000.toInt()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        canvas.drawPath(pointer, paint)
        paint.style = Paint.Style.FILL
        paint.color = 0xFFFFFFFF.toInt()
        canvas.drawPath(pointer, paint)
        if (clickFlash) {
            paint.color = 0xAA55C7FF.toInt()
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 4f
            canvas.drawCircle(px + 12f, py + 17f, 28f, paint)
            paint.style = Paint.Style.FILL
        }
    }
}
