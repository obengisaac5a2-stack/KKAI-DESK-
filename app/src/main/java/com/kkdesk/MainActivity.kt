package com.kkdesk

import android.app.Activity
import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.Display
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import org.json.JSONObject

class MainActivity : Activity(), DisplayManager.DisplayListener {
    private lateinit var displayManager: DisplayManager
    private lateinit var phoneWebView: WebView
    private var desktopPresentation: DesktopPresentation? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        buildPhoneController()
        displayManager.registerDisplayListener(this, null)
        refreshExternalDisplay()
    }

    private fun configureWebView(web: WebView, role: String) {
        web.setBackgroundColor(Color.rgb(10, 15, 27))
        web.isFocusable = true
        web.isFocusableInTouchMode = true
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            loadsImagesAutomatically = true
            javaScriptCanOpenWindowsAutomatically = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }
        web.addJavascriptInterface(HostBridge(role), "KKDexHost")
        web.webChromeClient = WebChromeClient()
        web.webViewClient = WebViewClient()
    }

    private fun buildPhoneController() {
        phoneWebView = WebView(this)
        configureWebView(phoneWebView, "phone")
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(10, 15, 27))
            addView(phoneWebView, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
        })
        phoneWebView.loadUrl("file:///android_asset/kkdex/index.html?controller=1")
    }

    private inner class HostBridge(private val role: String) {
        @JavascriptInterface
        fun postMessage(channel: String, json: String) {
            if (channel != "kkdex-remote") return
            runOnUiThread {
                if (role == "phone") desktopPresentation?.deliverToDesktop(json)
                else deliverToPhone(json)
            }
        }
    }

    private fun deliverToPhone(json: String) {
        if (!::phoneWebView.isInitialized) return
        phoneWebView.evaluateJavascript(
            "window.__KKDEX_NATIVE_BROADCAST && window.__KKDEX_NATIVE_BROADCAST('kkdex-remote', ${JSONObject.quote(json)});",
            null
        )
    }

    private fun refreshExternalDisplay() {
        val display = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            .firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
        if (display == null) {
            desktopPresentation?.dismiss()
            desktopPresentation = null
            return
        }
        if (desktopPresentation?.display?.displayId != display.displayId) {
            desktopPresentation?.dismiss()
            desktopPresentation = DesktopPresentation(this, display) { json ->
                runOnUiThread { deliverToPhone(json) }
            }.also { presentation ->
                try {
                    presentation.show()
                } catch (_: Exception) {
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
        if (::phoneWebView.isInitialized) {
            phoneWebView.loadUrl("about:blank")
            phoneWebView.destroy()
        }
        super.onDestroy()
    }

    private inner class DesktopPresentation(
        context: Context,
        display: Display,
        private val onDesktopMessage: (String) -> Unit
    ) : Presentation(context, display) {
        private lateinit var desktopWebView: WebView

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            window?.apply {
                setBackgroundDrawableResource(android.R.color.black)
                clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT
                )
            }
            desktopWebView = WebView(context)
            configureWebView(desktopWebView, "desktop")
            setContentView(desktopWebView)
            desktopWebView.loadUrl("file:///android_asset/kkdex/index.html?desktop=1")
        }

        fun deliverToDesktop(json: String) {
            if (!::desktopWebView.isInitialized) return
            desktopWebView.evaluateJavascript(
                "window.__KKDEX_NATIVE_BROADCAST && window.__KKDEX_NATIVE_BROADCAST('kkdex-remote', ${JSONObject.quote(json)});",
                null
            )
        }

        override fun onStop() {
            if (::desktopWebView.isInitialized) {
                desktopWebView.loadUrl("about:blank")
                desktopWebView.destroy()
            }
            super.onStop()
        }
    }
}
