package com.kkdesk

import android.app.Activity
import android.app.Presentation
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.Bundle
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.hardware.display.DisplayManager

class MainActivity : Activity(), DisplayManager.DisplayListener {
    private lateinit var displayManager: DisplayManager
    private var desktopPresentation: DesktopPresentation? = null
    private lateinit var status: TextView
    private var pointerX = 0.5f
    private var pointerY = 0.5f

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
            status.text = "TV connected • Pointer touchpad ready"
            if (desktopPresentation?.display?.displayId != display.displayId) {
                desktopPresentation?.dismiss()
                desktopPresentation = DesktopPresentation(this, display).also {
                    try {
                        it.show()
                        it.updatePointer(pointerX, pointerY, false)
                    } catch (error: Exception) {
                        status.text = "Display found, but desktop could not start: ${error.localizedMessage}"
                        desktopPresentation = null
                    }
                }
            }
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

private class DesktopPresentation(context: Context, display: Display) :
    Presentation(context, display) {
    private var desktopView: DesktopView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val view = DesktopView(context)
        desktopView = view
        setContentView(view)
    }

    fun updatePointer(x: Float, y: Float, clicked: Boolean) {
        desktopView?.updatePointer(x, y, clicked)
    }
}

private class DesktopView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var pointerX = 0.5f
    private var pointerY = 0.5f
    private var clickFlash = false

    fun updatePointer(x: Float, y: Float, clicked: Boolean) {
        pointerX = x
        pointerY = y
        clickFlash = clicked
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawColor(0xFF0B1220.toInt())

        // Desktop top bar
        paint.color = 0xFF111D30.toInt()
        canvas.drawRect(0f, 0f, w, 66f, paint)
        paint.color = 0xFFFFFFFF.toInt()
        paint.textSize = 25f
        paint.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        canvas.drawText("KK DESK", 34f, 42f, paint)
        paint.color = 0xFF9FB0C8.toInt()
        paint.textSize = 15f
        paint.typeface = android.graphics.Typeface.DEFAULT
        canvas.drawText("PHONE-POWERED DESKTOP", 160f, 40f, paint)
        canvas.drawText("●  CONNECTED", w - 150f, 40f, paint)

        // Desktop shortcuts
        drawShortcut(canvas, 74f, 145f, "Files", 0xFF2D75C7.toInt())
        drawShortcut(canvas, 74f, 275f, "Browser", 0xFF278C83.toInt())
        drawShortcut(canvas, 74f, 405f, "Settings", 0xFF6B65B5.toInt())

        // Welcome panel
        val panelLeft = (w * 0.25f).coerceAtLeast(230f)
        val panelTop = (h * 0.27f).coerceAtLeast(145f)
        paint.color = 0xFF17263D.toInt()
        canvas.drawRoundRect(panelLeft, panelTop, w - 70f, h - 90f, 24f, 24f, paint)
        paint.color = 0xFFFFFFFF.toInt()
        paint.textSize = 34f
        paint.typeface = android.graphics.Typeface.create("sans-serif-medium", 0)
        canvas.drawText("Welcome to KKAI-DESK", panelLeft + 38f, panelTop + 68f, paint)
        paint.color = 0xFFB9C7DA.toInt()
        paint.textSize = 20f
        paint.typeface = android.graphics.Typeface.DEFAULT
        canvas.drawText("Your external-screen desktop is active.", panelLeft + 38f, panelTop + 112f, paint)
        canvas.drawText("Move your finger on the phone to control the pointer.", panelLeft + 38f, panelTop + 148f, paint)
        paint.color = 0xFF6E86A8.toInt()
        paint.textSize = 16f
        canvas.drawText("PROTOTYPE 0.2  •  TOUCHPAD TEST", panelLeft + 38f, panelTop + 198f, paint)

        // Mouse pointer (drawn last so it stays visible)
        val px = pointerX * w
        val py = pointerY * h
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

    private fun drawShortcut(canvas: Canvas, x: Float, y: Float, label: String, color: Int) {
        paint.color = color
        canvas.drawRoundRect(x, y, x + 62f, y + 62f, 15f, 15f, paint)
        paint.color = 0xFFFFFFFF.toInt()
        paint.textSize = 15f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(label, x + 31f, y + 86f, paint)
        paint.textAlign = Paint.Align.LEFT
    }
}
