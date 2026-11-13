package com.kkdesk

import android.app.Activity
import android.app.Presentation
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.Display
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity(), DisplayManager.DisplayListener {
    private lateinit var displayManager: DisplayManager
    private var desktopPresentation: DesktopPresentation? = null
    private lateinit var status: TextView

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
            setPadding(28, 36, 28, 28)
            setBackgroundColor(0xFF101827.toInt())
        }
        val title = TextView(this).apply {
            text = "KKAI-DESK"
            textSize = 28f
            setTextColor(0xFFFFFFFF.toInt())
        }
        status = TextView(this).apply {
            text = "Looking for an external screen…"
            textSize = 16f
            setTextColor(0xFFB9C7DA.toInt())
            setPadding(0, 18, 0, 24)
        }
        val refresh = Button(this).apply {
            text = "Check external screen"
            setOnClickListener { refreshExternalDisplay() }
        }
        val note = TextView(this).apply {
            text = "This phone is the controller. The desktop appears separately on a connected TV or monitor; this app does not mirror the phone screen."
            textSize = 15f
            setTextColor(0xFFE4EAF3.toInt())
            setPadding(0, 24, 0, 0)
        }
        root.addView(title)
        root.addView(status)
        root.addView(refresh)
        root.addView(note)
        setContentView(root)
    }

    private fun refreshExternalDisplay() {
        val display = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            .firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
        if (display == null) {
            desktopPresentation?.dismiss()
            desktopPresentation = null
            status.text = "No presentation display detected. Connect your phone to a supported TV or monitor."
        } else {
            status.text = "External display detected (ID: ${display.displayId}). KK desktop is running there."
            if (desktopPresentation?.display?.displayId != display.displayId) {
                desktopPresentation?.dismiss()
                desktopPresentation = DesktopPresentation(this, display).also {
                    try {
                        it.show()
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

private class DesktopPresentation(context: Context, display: Display) :
    Presentation(context, display) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(56, 40, 56, 32)
            setBackgroundColor(0xFF0B1220.toInt())
        }
        val heading = TextView(context).apply {
            text = "KK DESK"
            textSize = 38f
            setTextColor(0xFFFFFFFF.toInt())
        }
        val subtitle = TextView(context).apply {
            text = "Your phone-powered desktop"
            textSize = 18f
            setTextColor(0xFF9FB0C8.toInt())
            setPadding(0, 8, 0, 28)
        }
        val card = TextView(context).apply {
            text = "Welcome to KKAI-DESK\\n\\nPrototype 0.1\\n\\nExternal display is active. The phone remains your controller."
            textSize = 21f
            setTextColor(0xFFEAF1FA.toInt())
            setPadding(28, 24, 28, 24)
            setBackgroundColor(0xFF1B2940.toInt())
        }
        val footer = TextView(context).apply {
            text = "KK  •  DESKTOP PREVIEW"
            textSize = 14f
            setTextColor(0xFF7F93B0.toInt())
            setPadding(0, 32, 0, 0)
        }
        root.addView(heading)
        root.addView(subtitle)
        root.addView(card)
        root.addView(footer)
        setContentView(root)
    }
}
