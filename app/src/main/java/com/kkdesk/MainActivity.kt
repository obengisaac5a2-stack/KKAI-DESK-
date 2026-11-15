package com.kkdesk

import android.app.Activity
import android.app.ActivityOptions
import android.app.Presentation
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.app.DownloadManager
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import android.os.Environment
import android.webkit.DownloadListener
import android.webkit.ValueCallback
import android.webkit.URLUtil
import android.widget.Toast
import org.json.JSONArray
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
import android.widget.VideoView
import android.widget.MediaController
import android.widget.LinearLayout
import android.widget.Button
import android.widget.TextView
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.provider.Settings
import org.json.JSONObject

class MainActivity : Activity(), DisplayManager.DisplayListener {
    private lateinit var displayManager: DisplayManager
    private lateinit var phoneWebView: WebView
    private var desktopPresentation: DesktopPresentation? = null
    private var pendingFileChooser: ValueCallback<Array<Uri>>? = null
    private val fileChooserRequestCode = 7001
    private val storageTreeRequestCode = 7002
    private var storageTreeUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        getSharedPreferences("kkdex_storage", Context.MODE_PRIVATE).getString("tree_uri", null)?.let { storageTreeUri = Uri.parse(it) }
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
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(view: WebView?, filePathCallback: ValueCallback<Array<Uri>>?, fileChooserParams: WebChromeClient.FileChooserParams?): Boolean {
                pendingFileChooser?.onReceiveValue(null)
                pendingFileChooser = filePathCallback
                return try {
                    val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"; putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true) }
                    intent.addCategory(Intent.CATEGORY_OPENABLE)
                    intent.type = "*/*"
                    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                    startActivityForResult(intent, fileChooserRequestCode)
                    true
                } catch (_: Exception) { pendingFileChooser?.onReceiveValue(null); pendingFileChooser = null; false }
            }
        }
        web.setDownloadListener(DownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            try {
                val filename = URLUtil.guessFileName(url, contentDisposition, mimeType)
                val request = DownloadManager.Request(Uri.parse(url)).apply {
                    setMimeType(mimeType)
                    addRequestHeader("User-Agent", userAgent)
                    setTitle(filename)
                    setDescription("Downloading with KK DEX")
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
                    setAllowedOverMetered(true)
                    setAllowedOverRoaming(false)
                }
                (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
                Toast.makeText(this@MainActivity, "Download started", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) { Toast.makeText(this@MainActivity, "Could not start this download", Toast.LENGTH_LONG).show() }
        })
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
        fun getInstalledApps(): String {
            val intent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
            val apps = packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
                .map { info -> JSONObject().apply {
                    put("packageName", info.activityInfo.packageName)
                    put("name", info.loadLabel(packageManager).toString())
                } }
                .sortedBy { it.optString("name").lowercase() }
            return JSONArray(apps).toString()
        }

        @JavascriptInterface
        fun launchApp(packageName: String): Boolean {
            if (packageName.isBlank() || packageName == this@MainActivity.packageName) return false
            return try {
                val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return false
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val externalDisplay = displayManager
                    .getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
                    .firstOrNull { it.displayId != Display.DEFAULT_DISPLAY && it.state == Display.STATE_ON }
                if (externalDisplay != null) {
                    try {
                        val options = ActivityOptions.makeBasic()
                            .setLaunchDisplayId(externalDisplay.displayId)
                        startActivity(launch, options.toBundle())
                    } catch (_: Exception) {
                        // Do not swallow the tap: if Android blocks secondary-display launch,
                        // fall back to the normal Android launch on the phone.
                        startActivity(launch)
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "This app cannot open on the TV, so Android opened it on the phone.", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    startActivity(launch)
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "No TV desktop display was detected. Opened the app on the phone.", Toast.LENGTH_LONG).show()
                    }
                }
                true
            } catch (_: Exception) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Android could not open this application.", Toast.LENGTH_LONG).show()
                }
                false
            }
        }

        @JavascriptInterface
        fun requestStorageAccess(): Boolean {
            return try {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                }
                runOnUiThread { startActivityForResult(intent, storageTreeRequestCode) }
                true
            } catch (_: Exception) {
                runOnUiThread { Toast.makeText(this@MainActivity, "Could not open Android storage picker.", Toast.LENGTH_LONG).show() }
                false
            }
        }

        @JavascriptInterface
        fun getStorageFiles(relativePath: String): String {
            return try {
                val rootUri = storageTreeUri ?: return JSONArray().toString()
                var directory = DocumentFile.fromTreeUri(this@MainActivity, rootUri) ?: return JSONArray().toString()
                relativePath.split("/").filter { it.isNotBlank() && it != "Phone storage" }.forEach { part ->
                    directory = directory.findFile(part) ?: return JSONArray().toString()
                }
                val items = directory.listFiles().sortedWith(compareBy<DocumentFile> { !it.isDirectory }.thenBy { it.name?.lowercase() ?: "" })
                JSONArray().apply {
                    items.forEach { file ->
                        put(JSONObject().apply {
                            put("name", file.name ?: "Unnamed")
                            put("type", if (file.isDirectory) "folder" else "file")
                            put("size", if (file.isFile) file.length() else 0)
                            put("uri", file.uri.toString())
                            put("mimeType", file.type ?: "")
                        })
                    }
                }.toString()
            } catch (_: Exception) { JSONArray().toString() }
        }

        @JavascriptInterface
        fun openStorageFile(uriText: String): Boolean {
            return try {
                val uri = Uri.parse(uriText)
                val mime = contentResolver.getType(uri) ?: "*/*"
                if (mime.startsWith("video/") && role == "desktop") {
                    runOnUiThread { desktopPresentation?.playVideo(uri) }
                    true
                } else {
                    if (mime == "application/vnd.android.package-archive" && android.os.Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "Allow KKAI-DESK to install apps, then select the APK again.", Toast.LENGTH_LONG).show()
                            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + packageName)))
                        }
                        return false
                    }
                    val view = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, mime)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(view)
                    true
                }
            } catch (_: Exception) {
                runOnUiThread { Toast.makeText(this@MainActivity, "Android could not open this file. Check that a compatible app is installed.", Toast.LENGTH_LONG).show() }
                false
            }
        }

        @JavascriptInterface
        fun openWebPage(url: String): Boolean {
            if (url.isBlank() || !(url.startsWith("https://") || url.startsWith("http://"))) return false
            return try {
                if (role == "desktop") {
                    runOnUiThread { desktopPresentation?.openWebPage(url) }
                    true
                } else false
            } catch (_: Exception) { false }
        }

        @JavascriptInterface
        fun openWifiSettings(): Boolean {
            return try {
                runOnUiThread { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }
                true
            } catch (_: Exception) { false }
        }

        @JavascriptInterface
        fun openBluetoothSettings(): Boolean {
            return try {
                runOnUiThread { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
                true
            } catch (_: Exception) { false }
        }

        @JavascriptInterface
        fun postMessage(channel: String, json: String) {
            if (channel != "kkdex-remote") return
            runOnUiThread {
                if (role == "phone") desktopPresentation?.deliverToDesktop(json)
                else deliverToPhone(json)
            }
        }
    }

    @Deprecated("Deprecated in Android API; retained for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == storageTreeRequestCode) {
            if (resultCode == RESULT_OK && data?.data != null) {
                val uri = data.data!!
                try {
                    val flags = data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    contentResolver.takePersistableUriPermission(uri, flags)
                } catch (_: Exception) {
                    // Some document providers do not support persistable grants; keep this session's URI.
                }
                storageTreeUri = uri
                getSharedPreferences("kkdex_storage", Context.MODE_PRIVATE).edit().putString("tree_uri", uri.toString()).apply()
                Toast.makeText(this, "Phone storage connected.", Toast.LENGTH_SHORT).show()
                notifyStorageReady()
            }
            return
        }
        if (requestCode != fileChooserRequestCode) return
        val callback = pendingFileChooser ?: return
        pendingFileChooser = null
        if (resultCode != RESULT_OK || data == null) { callback.onReceiveValue(null); return }
        val results = mutableListOf<Uri>()
        data.clipData?.let { clip -> for (index in 0 until clip.itemCount) results.add(clip.getItemAt(index).uri) }
            ?: data.data?.let { results.add(it) }
        callback.onReceiveValue(results.distinct().toTypedArray())
    }

    private fun notifyStorageReady() {
        val script = "window.dispatchEvent(new Event('kkdex-storage-ready'));"
        if (::phoneWebView.isInitialized) phoneWebView.evaluateJavascript(script, null)
        desktopPresentation?.notifyStorageReady()
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
        private lateinit var desktopRoot: FrameLayout
        private var videoOverlay: FrameLayout? = null
        private var browserOverlay: FrameLayout? = null

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
            desktopRoot = FrameLayout(context)
            desktopRoot.addView(desktopWebView, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
            setContentView(desktopRoot)
            desktopWebView.loadUrl("file:///android_asset/kkdex/index.html?desktop=1")
        }

        fun playVideo(uri: Uri) {
            if (!::desktopRoot.isInitialized) return
            videoOverlay?.let { desktopRoot.removeView(it) }
            browserOverlay?.let { desktopRoot.removeView(it); browserOverlay = null }
            val overlay = FrameLayout(context).apply { setBackgroundColor(Color.BLACK) }
            val player = VideoView(context)
            overlay.addView(player, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
            val controls = MediaController(context)
            controls.setAnchorView(player)
            player.setMediaController(controls)
            val close = Button(context).apply {
                text = "✕"
                textSize = 18f
                setOnClickListener {
                    player.stopPlayback()
                    desktopRoot.removeView(overlay)
                    videoOverlay = null
                }
            }
            overlay.addView(close, FrameLayout.LayoutParams(
                56, 56, Gravity.TOP or Gravity.END
            ))
            desktopRoot.addView(overlay, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
            videoOverlay = overlay
            player.setOnErrorListener { _, _, _ ->
                Toast.makeText(context, "This video format could not be played on KK DEX.", Toast.LENGTH_LONG).show()
                true
            }
            player.setOnCompletionListener {
                desktopRoot.removeView(overlay)
                videoOverlay = null
            }
            player.setVideoURI(uri)
            player.setOnPreparedListener { it.start() }
        }

        fun openWebPage(url: String) {
            if (!::desktopRoot.isInitialized) return
            browserOverlay?.let { desktopRoot.removeView(it) }
            videoOverlay?.let { desktopRoot.removeView(it); videoOverlay = null }
            val overlay = FrameLayout(context).apply { setBackgroundColor(Color.BLACK) }
            val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            val toolbar = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(Color.rgb(24, 29, 40))
            }
            val browser = WebView(context)
            val back = Button(context).apply {
                text = "‹"
                setOnClickListener { if (browser.canGoBack()) browser.goBack() }
            }
            val address = TextView(context).apply {
                setTextColor(Color.WHITE)
                textSize = 12f
                maxLines = 1
                text = url
                layoutParams = LinearLayout.LayoutParams(0, 52, 1f)
            }
            val close = Button(context).apply {
                text = "✕"
                setOnClickListener {
                    browser.stopLoading()
                    desktopRoot.removeView(overlay)
                    browserOverlay = null
                }
            }
            toolbar.addView(back, LinearLayout.LayoutParams(52, 52))
            toolbar.addView(address)
            toolbar.addView(close, LinearLayout.LayoutParams(56, 52))
            configureWebView(browser, "browser")
            browser.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, pageUrl: String?) {
                    address.text = pageUrl ?: ""
                }
            }
            column.addView(toolbar)
            column.addView(browser, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            ))
            overlay.addView(column, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
            desktopRoot.addView(overlay, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
            browserOverlay = overlay
            browser.loadUrl(url)
        }

        fun notifyStorageReady() {
            if (!::desktopWebView.isInitialized) return
            desktopWebView.evaluateJavascript("window.dispatchEvent(new Event('kkdex-storage-ready'));", null)
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
