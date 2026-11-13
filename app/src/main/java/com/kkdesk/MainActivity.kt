package com.kkdesk

import android.app.Activity
import android.app.Presentation
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.text.InputType
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject
import java.util.Locale

class MainActivity : Activity(), DisplayManager.DisplayListener {
    private lateinit var displayManager: DisplayManager
    private var desktopPresentation: DesktopPresentation? = null
    private lateinit var status: TextView
    private lateinit var keyboardInput: EditText
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
            setPadding(16, 20, 16, 16)
            setBackgroundColor(0xFF101827.toInt())
        }
        val title = TextView(this).apply {
            text = "KK DEX CONTROLLER"
            textSize = 23f
            setTextColor(Color.WHITE)
        }
        status = TextView(this).apply {
            text = "Looking for an external screen…"
            textSize = 14f
            setTextColor(0xFFB9C7DA.toInt())
            setPadding(0, 8, 0, 10)
        }
        val instruction = TextView(this).apply {
            text = "Move one finger to control the TV pointer. Tap to click. Use the hold button to drag windows."
            textSize = 13f
            setTextColor(0xFFEAF1FA.toInt())
            setPadding(0, 2, 0, 8)
        }
        val trackpad = TouchpadView(this) { dx, dy, clicked ->
            pointerX = (pointerX + dx).coerceIn(0.005f, 0.995f)
            pointerY = (pointerY + dy).coerceIn(0.005f, 0.995f)
            desktopPresentation?.updatePointer(pointerX, pointerY, clicked)
        }
        root.addView(title)
        root.addView(status)
        root.addView(instruction)

        val holdButton = Button(this).apply {
            text = "HOLD LEFT MOUSE BUTTON: OFF"
            setOnClickListener {
                pointerButtonHeld = !pointerButtonHeld
                text = if (pointerButtonHeld) "RELEASE LEFT MOUSE BUTTON" else "HOLD LEFT MOUSE BUTTON: OFF"
                desktopPresentation?.setPointerButtonHeld(pointerButtonHeld, pointerX, pointerY)
            }
        }
        root.addView(holdButton)
        root.addView(trackpad, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        val keyboardRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        keyboardInput = EditText(this).apply {
            hint = "Type on the TV desktop…"
            setSingleLine(true)
            textSize = 14f
            setTextColor(Color.WHITE)
            setHintTextColor(0xFF91A2B9.toInt())
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
            imeOptions = EditorInfo.IME_ACTION_SEND
            setPadding(12, 4, 12, 4)
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    sendTextToDesktop()
                    true
                } else false
            }
        }
        keyboardRow.addView(keyboardInput, LinearLayout.LayoutParams(0, 54, 1f))
        val sendButton = Button(this).apply {
            text = "SEND"
            setOnClickListener { sendTextToDesktop() }
        }
        keyboardRow.addView(sendButton)
        root.addView(keyboardRow)

        val enterButton = Button(this).apply {
            text = "ENTER"
            setOnClickListener { desktopPresentation?.pressKey("Enter") }
        }
        root.addView(enterButton)

        val hint = TextView(this).apply {
            text = "The TV shows the full KK DEX desktop. This phone stays the touchpad and keyboard."
            textSize = 12f
            setTextColor(0xFF9FB0C8.toInt())
            setPadding(0, 8, 0, 0)
            gravity = Gravity.CENTER
        }
        root.addView(hint)
        setContentView(root)
    }

    private fun sendTextToDesktop() {
        val value = keyboardInput.text?.toString().orEmpty()
        if (value.isNotEmpty()) {
            desktopPresentation?.insertText(value)
            keyboardInput.text?.clear()
        }
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(keyboardInput.windowToken, 0)
    }

    private fun refreshExternalDisplay() {
        val display = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            .firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
        if (display == null) {
            desktopPresentation?.dismiss()
            desktopPresentation = null
            status.text = "No external screen detected. Connect your phone to a supported TV or monitor."
            return
        }

        status.text = "TV connected: " + display.name + " • Full KK DEX desktop starting…"
        if (desktopPresentation?.display?.displayId != display.displayId) {
            desktopPresentation?.dismiss()
            desktopPresentation = DesktopPresentation(this, display).also { presentation ->
                try {
                    presentation.show()
                    presentation.updatePointer(pointerX, pointerY, false)
                    presentation.setPointerButtonHeld(pointerButtonHeld, pointerX, pointerY)
                    status.text = "TV connected: " + display.name + " • KK DEX desktop loaded"
                } catch (error: Exception) {
                    status.text = "Display found, but desktop could not start: " + error.localizedMessage
                    desktopPresentation = null
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
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), 24f, 24f,
            paint.apply { color = 0xFF1B2940.toInt() })
        paint.color = 0xFF405675.toInt()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        canvas.drawRoundRect(5f, 5f, width - 5f, height - 5f, 20f, 20f, paint)
        paint.style = Paint.Style.FILL
        paint.color = 0xFFB9C7DA.toInt()
        paint.textSize = 17f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("TOUCHPAD", width / 2f, height / 2f - 5f, paint)
        paint.color = 0xFF8195B2.toInt()
        paint.textSize = 12f
        canvas.drawText("Tap to click • Drag to move", width / 2f, height / 2f + 22f, paint)
        paint.textAlign = Paint.Align.LEFT
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
    display: Display
) : Presentation(context, display) {
    private var desktopWebView: WebView? = null
    private var pageReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.BLACK))
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0f)
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
        }

        val web = WebView(context).apply {
            setBackgroundColor(Color.BLACK)
            isFocusable = true
            isFocusableInTouchMode = true
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                loadsImagesAutomatically = true
                javaScriptCanOpenWindowsAutomatically = true
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            }
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    pageReady = true
                }
            }
            layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
        }
        desktopWebView = web
        setContentView(web)
        web.loadUrl("file:///android_asset/kkdex/index.html")
    }

    fun updatePointer(x: Float, y: Float, clicked: Boolean) {
        val web = desktopWebView ?: return
        if (!pageReady || web.width <= 0 || web.height <= 0) return
        val density = web.resources.displayMetrics.density.coerceAtLeast(1f)
        val px = x * (web.width / density)
        val py = y * (web.height / density)
        val args = String.format(Locale.US, "%.1f,%.1f", px, py)
        web.evaluateJavascript("window.KKDexAPI && window.KKDexAPI.movePointer($args);", null)
        if (clicked) {
            web.evaluateJavascript("window.KKDexAPI && window.KKDexAPI.pointerClick($args);", null)
        }
    }

    fun setPointerButtonHeld(held: Boolean, x: Float, y: Float) {
        val web = desktopWebView ?: return
        if (!pageReady || web.width <= 0 || web.height <= 0) return
        val density = web.resources.displayMetrics.density.coerceAtLeast(1f)
        val args = String.format(Locale.US, "%.1f,%.1f", x * (web.width / density), y * (web.height / density))
        val method = if (held) "pointerDown" else "pointerUp"
        web.evaluateJavascript("window.KKDexAPI && window.KKDexAPI.$method($args);", null)
    }

    fun insertText(value: String) {
        val safe = JSONObject.quote(value)
        desktopWebView?.takeIf { pageReady }?.evaluateJavascript(
            "window.KKDexAPI && window.KKDexAPI.insertText($safe);", null
        )
    }

    fun pressKey(key: String) {
        val safe = JSONObject.quote(key)
        desktopWebView?.takeIf { pageReady }?.evaluateJavascript(
            "window.KKDexAPI && window.KKDexAPI.pressKey($safe);", null
        )
    }

}
