package com.kkdesk

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.view.View
import android.graphics.Typeface

/**
 * KK Dex's independent TV desktop shell.
 * This is deliberately a shell with internal workspaces, not phone-screen mirroring.
 */
internal class DesktopShellView(
    context: Context,
    private val onLaunchPhoneApp: (String) -> Unit
) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pm = context.packageManager
    private val installedApps = pm.queryIntentActivities(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
    ).filter { it.activityInfo.packageName != context.packageName }
        .distinctBy { it.activityInfo.packageName }
        .sortedBy { it.loadLabel(pm).toString().lowercase() }
        .take(24)

    private var pointerX = .5f
    private var pointerY = .5f
    private var activeApp = ""
    private var launcherOpen = false
    private var wallpaper = 0
    private var notificationOpen = false
    private var clickPulse = false

    private val apps = listOf(
        AppTile("Files", "Browse your phone files", "▤", 0xFF55A7F5.toInt()),
        AppTile("Browser", "Explore the web", "◎", 0xFF46C7B2.toInt()),
        AppTile("Gallery", "Photos and videos", "▧", 0xFFE6A45C.toInt()),
        AppTile("Notes", "Write and save ideas", "✎", 0xFFB99AF4.toInt()),
        AppTile("Media", "Your music and videos", "♫", 0xFFEC7FA4.toInt()),
        AppTile("Settings", "Personalize KK Dex", "⚙", 0xFF8EA5C7.toInt()),
        AppTile("Phone Apps", "Installed Android apps", "▦", 0xFF69C99A.toInt())
    )

    fun updatePointer(x: Float, y: Float, clicked: Boolean) {
        pointerX = x.coerceIn(.005f, .995f)
        pointerY = y.coerceIn(.005f, .995f)
        clickPulse = clicked
        if (clicked) handleClick(pointerX * 1280f, pointerY * 720f)
        invalidate()
    }

    private fun handleClick(x: Float, y: Float) {
        if (notificationOpen) { notificationOpen = false; return }
        if (launcherOpen) {
            if (x < 300f && y > 650f) { launcherOpen = false; return }
            val col = ((x - 340f) / 205f).toInt().coerceIn(0, 3)
            val row = ((y - 190f) / 145f).toInt().coerceIn(0, 2)
            val index = row * 4 + col
            if (x in 300f..1170f && y in 165f..620f) {
                apps.getOrNull(index)?.let { openApp(it.name) }
            } else launcherOpen = false
            return
        }
        if (y < 58f && x > 1090f) { notificationOpen = true; return }
        if (y > 650f) {
            if (x < 100f) { launcherOpen = !launcherOpen; return }
            val index = when (x) {
                in 110f..265f -> 0
                in 265f..420f -> 1
                in 420f..575f -> 2
                in 575f..730f -> 3
                in 730f..885f -> 4
                else -> -1
            }
            if (index >= 0) openApp(apps[index].name)
            return
        }
        if (activeApp.isNotEmpty()) {
            if (x > 1130f && y < 120f) { activeApp = ""; return }
            if (activeApp == "Settings" && x in 300f..1120f && y in 210f..580f) {
                wallpaper = (wallpaper + 1) % 3
            }
            if (activeApp == "Phone Apps" && x in 300f..1160f && y in 165f..610f) {
                val index = ((y - 175f) / 48f).toInt()
                installedApps.getOrNull(index)?.let { onLaunchPhoneApp(it.activityInfo.packageName) }
            }
            return
        }
        val tile = when {
            x in 32f..150f && y in 105f..220f -> "Files"
            x in 32f..150f && y in 225f..340f -> "Browser"
            x in 32f..150f && y in 345f..460f -> "Gallery"
            x in 32f..150f && y in 465f..580f -> "Notes"
            x in 180f..430f && y in 430f..620f -> "Phone Apps"
            x in 450f..700f && y in 430f..620f -> "Media"
            else -> ""
        }
        if (tile.isNotEmpty()) openApp(tile)
    }

    private fun openApp(name: String) {
        activeApp = name
        launcherOpen = false
        notificationOpen = false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val sx = width / 1280f
        val sy = height / 720f
        canvas.save()
        canvas.scale(sx, sy)
        drawWallpaper(canvas)
        drawTopBar(canvas)
        if (activeApp.isEmpty()) drawDesktop(canvas) else drawWindow(canvas)
        drawDock(canvas)
        if (launcherOpen) drawLauncher(canvas)
        if (notificationOpen) drawNotifications(canvas)
        drawPointer(canvas)
        canvas.restore()
    }

    private fun drawWallpaper(c: Canvas) {
        val colors = when (wallpaper) {
            1 -> intArrayOf(0xFF071E25.toInt(), 0xFF123B3B.toInt(), 0xFF172B3C.toInt())
            2 -> intArrayOf(0xFF21172F.toInt(), 0xFF38234A.toInt(), 0xFF10243A.toInt())
            else -> intArrayOf(0xFF071321.toInt(), 0xFF102D48.toInt(), 0xFF18243D.toInt())
        }
        p.shader = LinearGradient(0f, 0f, 1280f, 720f, colors, null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, 1280f, 720f, p)
        p.shader = null
        p.color = 0x14FFFFFF
        for (i in 0..12) c.drawCircle(180f + i * 112f, 120f + (i % 4) * 145f, 1.5f, p)
        p.color = 0x12A7DFFF
        c.drawCircle(1050f, 190f, 210f, p)
    }

    private fun drawTopBar(c: Canvas) {
        panel(c, 0f, 0f, 1280f, 58f, 0xCC091421, 0f)
        label(c, "KK", 28f, 38f, 26f, 0xFF8EDBFF.toInt(), true)
        label(c, "DEX", 70f, 38f, 23f, 0xFFFFFFFF.toInt(), true)
        label(c, "WORKSPACE", 145f, 36f, 12f, 0xFF91A9C1.toInt(), true)
        label(c, "DESKTOP", 1000f, 36f, 12f, 0xFFB8C9D9.toInt(), true)
        label(c, "●", 1085f, 36f, 12f, 0xFF65D8A6.toInt())
        label(c, "QUICK SETTINGS  ▾", 1110f, 36f, 12f, 0xFFD8E7F5.toInt(), true)
    }

    private fun drawDesktop(c: Canvas) {
        drawDesktopTile(c, 34f, 100f, apps[0])
        drawDesktopTile(c, 34f, 218f, apps[1])
        drawDesktopTile(c, 34f, 336f, apps[2])
        drawDesktopTile(c, 34f, 454f, apps[3])

        panel(c, 190f, 100f, 1240f, 390f, 0xB5102033.toInt(), 24f)
        label(c, "YOUR SPACE. YOUR RULES.", 232f, 153f, 13f, 0xFF8EDBFF.toInt(), true)
        label(c, "Welcome to KK Dex", 232f, 215f, 42f, 0xFFFFFFFF.toInt(), true)
        label(c, "A new desktop experience, powered by your phone.", 234f, 252f, 18f, 0xFFB5C9DD.toInt())
        panel(c, 232f, 292f, 510f, 365f, 0xFF1A3550.toInt(), 15f)
        label(c, "WORKSPACE", 258f, 326f, 12f, 0xFF8FAFCB.toInt(), true)
        label(c, "Open your tools", 258f, 359f, 21f, 0xFFFFFFFF.toInt(), true)
        label(c, "Files  •  Browser  •  Notes  •  Media", 258f, 388f, 13f, 0xFFB5C9DD.toInt())
        panel(c, 530f, 292f, 900f, 365f, 0xFF1A3550.toInt(), 15f)
        label(c, "DESKTOP CONTROL", 556f, 326f, 12f, 0xFF8FAFCB.toInt(), true)
        label(c, "Your phone is the touchpad", 556f, 359f, 21f, 0xFFFFFFFF.toInt(), true)
        label(c, "Move the pointer and tap to open desktop tools.", 556f, 388f, 13f, 0xFFB5C9DD.toInt())

        panel(c, 190f, 415f, 420f, 625f, 0xA9162B40.toInt(), 18f)
        label(c, "QUICK ACCESS", 218f, 451f, 12f, 0xFF8FAFCB.toInt(), true)
        drawQuickApp(c, 220f, 478f, apps[6])
        drawQuickApp(c, 220f, 535f, apps[4])
        panel(c, 445f, 415f, 1240f, 625f, 0xA9162B40.toInt(), 18f)
        label(c, "MAKE IT YOURS", 474f, 451f, 12f, 0xFF8FAFCB.toInt(), true)
        label(c, "Personalize your desktop", 474f, 490f, 23f, 0xFFFFFFFF.toInt(), true)
        label(c, "Open Settings to change your wallpaper and desktop style.", 474f, 520f, 14f, 0xFFB5C9DD.toInt())
        pill(c, 474f, 548f, 205f, 43f, "OPEN SETTINGS", 0xFF28618C.toInt())
    }

    private fun drawWindow(c: Canvas) {
        panel(c, 185f, 75f, 1245f, 630f, 0xF00D1B2B.toInt(), 22f)
        panel(c, 185f, 75f, 1245f, 145f, 0xFF172B40.toInt(), 22f)
        val app = apps.firstOrNull { it.name == activeApp }
        val accent = app?.color ?: 0xFF8EDBFF.toInt()
        label(c, app?.symbol ?: "◈", 220f, 119f, 25f, accent, true)
        label(c, activeApp.uppercase(), 260f, 117f, 17f, 0xFFFFFFFF.toInt(), true)
        label(c, "KK DEX APPLICATION", 430f, 116f, 11f, 0xFF8FAFCB.toInt(), true)
        panel(c, 1170f, 91f, 1225f, 135f, 0xFF344A60.toInt(), 10f)
        label(c, "×", 1188f, 122f, 25f, 0xFFFFFFFF.toInt(), true)

        when (activeApp) {
            "Files" -> drawFiles(c)
            "Browser" -> drawBrowser(c)
            "Gallery" -> drawGallery(c)
            "Notes" -> drawNotes(c)
            "Media" -> drawMedia(c)
            "Settings" -> drawSettings(c)
            "Phone Apps" -> drawPhoneApps(c)
        }
    }

    private fun drawFiles(c: Canvas) {
        label(c, "FILE MANAGER", 225f, 185f, 12f, 0xFF8FAFCB.toInt(), true)
        label(c, "Your files, organized.", 225f, 225f, 28f, 0xFFFFFFFF.toInt(), true)
        val folders = listOf("Pictures", "Camera", "Downloads", "Documents", "Music", "Videos")
        folders.forEachIndexed { i, name ->
            val x = 225f + (i % 3) * 310f
            val y = 255f + (i / 3) * 135f
            panel(c, x, y, x + 275f, y + 105f, 0xFF1C354E.toInt(), 14f)
            label(c, "▣", x + 22f, y + 48f, 28f, 0xFF8EDBFF.toInt())
            label(c, name, x + 68f, y + 47f, 17f, 0xFFFFFFFF.toInt(), true)
        }
        label(c, "Phone storage integration is the next build stage.", 225f, 565f, 14f, 0xFFFFD28A.toInt())
    }

    private fun drawBrowser(c: Canvas) {
        label(c, "WEB WORKSPACE", 225f, 185f, 12f, 0xFF8FAFCB.toInt(), true)
        panel(c, 225f, 205f, 1205f, 267f, 0xFF203B55.toInt(), 13f)
        label(c, "⌕   Search or enter a website", 255f, 243f, 18f, 0xFFB9CBDD.toInt())
        panel(c, 225f, 290f, 1205f, 570f, 0xFF172B40.toInt(), 14f)
        label(c, "Your browser", 270f, 365f, 32f, 0xFFFFFFFF.toInt(), true)
        label(c, "A built-in browser workspace will live here.", 270f, 405f, 16f, 0xFFB9CBDD.toInt())
    }

    private fun drawGallery(c: Canvas) {
        label(c, "YOUR VISUAL LIBRARY", 225f, 185f, 12f, 0xFF8FAFCB.toInt(), true)
        label(c, "Photos and videos", 225f, 225f, 28f, 0xFFFFFFFF.toInt(), true)
        for (i in 0..7) {
            val x = 225f + (i % 4) * 235f
            val y = 255f + (i / 4) * 145f
            panel(c, x, y, x + 205f, y + 120f, intArrayOf(0xFF27445A.toInt(),0xFF31435B.toInt(),0xFF3B3554.toInt(),0xFF254C4B.toInt())[i % 4], 13f)
            label(c, "▧", x + 85f, y + 72f, 34f, 0xFFD7E9F8.toInt())
        }
        label(c, "Media access will be connected to Android storage APIs.", 225f, 565f, 14f, 0xFFFFD28A.toInt())
    }

    private fun drawNotes(c: Canvas) {
        label(c, "NOTES", 225f, 185f, 12f, 0xFF8FAFCB.toInt(), true)
        panel(c, 225f, 210f, 1205f, 570f, 0xFF1A3047.toInt(), 15f)
        label(c, "A clear space for your ideas.", 270f, 280f, 29f, 0xFFFFFFFF.toInt(), true)
        label(c, "Your first note will appear here.", 270f, 325f, 16f, 0xFFB9CBDD.toInt())
        pill(c, 270f, 375f, 165f, 45f, "NEW NOTE", 0xFF28618C.toInt())
    }

    private fun drawMedia(c: Canvas) {
        label(c, "MEDIA CENTER", 225f, 185f, 12f, 0xFF8FAFCB.toInt(), true)
        panel(c, 225f, 215f, 1205f, 555f, 0xFF1A3047.toInt(), 18f)
        label(c, "♫", 620f, 360f, 66f, 0xFF8EDBFF.toInt())
        label(c, "Your entertainment space", 450f, 420f, 25f, 0xFFFFFFFF.toInt(), true)
        label(c, "Music and video playback will be integrated here.", 450f, 453f, 15f, 0xFFB9CBDD.toInt())
    }

    private fun drawSettings(c: Canvas) {
        label(c, "PERSONALIZATION", 225f, 185f, 12f, 0xFF8FAFCB.toInt(), true)
        label(c, "Make KK Dex yours", 225f, 225f, 28f, 0xFFFFFFFF.toInt(), true)
        listOf("Midnight blue", "Emerald dusk", "Violet horizon").forEachIndexed { i, name ->
            val y = 260f + i * 88f
            panel(c, 225f, y, 1165f, y + 68f, 0xFF1C354E.toInt(), 13f)
            label(c, name, 255f, y + 42f, 17f, 0xFFFFFFFF.toInt(), true)
            label(c, if (wallpaper == i) "ACTIVE" else "TAP TO APPLY", 1000f, y + 42f, 12f, 0xFF8EDBFF.toInt(), true)
        }
        label(c, "Tap a wallpaper row to change the desktop background.", 225f, 565f, 14f, 0xFFB9CBDD.toInt())
    }

    private fun drawPhoneApps(c: Canvas) {
        label(c, "INSTALLED ON YOUR PHONE", 225f, 185f, 12f, 0xFF8FAFCB.toInt(), true)
        label(c, "Phone applications", 225f, 220f, 26f, 0xFFFFFFFF.toInt(), true)
        installedApps.take(8).forEachIndexed { i, info ->
            val y = 245f + i * 43f
            panel(c, 225f, y, 1195f, y + 37f, 0xFF1C354E.toInt(), 8f)
            label(c, info.loadLabel(pm).toString().take(38), 245f, y + 25f, 15f, 0xFFFFFFFF.toInt())
            label(c, "OPEN REQUEST  ›", 1015f, y + 25f, 11f, 0xFF8EDBFF.toInt(), true)
        }
        label(c, "These apps may still be restricted by Android on external displays.", 225f, 610f, 13f, 0xFFFFD28A.toInt())
    }

    private fun drawDock(c: Canvas) {
        panel(c, 0f, 650f, 1280f, 720f, 0xE8091420.toInt(), 0f)
        pill(c, 22f, 661f, 76f, 46f, "KK", 0xFF28618C.toInt())
        val dockApps = apps.take(5)
        dockApps.forEachIndexed { i, app ->
            val x = 125f + i * 155f
            panel(c, x, 660f, x + 140f, 708f, if (activeApp == app.name) 0xFF31506B.toInt() else 0xFF1A2B3E.toInt(), 11f)
            label(c, app.symbol, x + 15f, 692f, 20f, app.color, true)
            label(c, app.name, x + 45f, 691f, 13f, 0xFFEAF3FB.toInt(), activeApp == app.name)
        }
        label(c, "KK DEX  •  READY", 1030f, 691f, 12f, 0xFF9FB7CE.toInt(), true)
    }

    private fun drawLauncher(c: Canvas) {
        panel(c, 285f, 120f, 1195f, 630f, 0xF20B1928.toInt(), 24f)
        label(c, "APPLICATION LAUNCHER", 325f, 165f, 13f, 0xFF8EDBFF.toInt(), true)
        label(c, "Everything in one place", 325f, 205f, 27f, 0xFFFFFFFF.toInt(), true)
        apps.forEachIndexed { i, app ->
            val x = 325f + (i % 4) * 205f
            val y = 235f + (i / 4) * 145f
            panel(c, x, y, x + 180f, y + 125f, 0xFF1B3046.toInt(), 14f)
            panel(c, x + 17f, y + 16f, x + 64f, y + 63f, app.color, 12f)
            label(c, app.symbol, x + 30f, y + 49f, 23f, 0xFFFFFFFF.toInt(), true)
            label(c, app.name, x + 17f, y + 91f, 16f, 0xFFFFFFFF.toInt(), true)
            label(c, app.detail, x + 17f, y + 112f, 10f, 0xFF9FB7CE.toInt())
        }
    }

    private fun drawNotifications(c: Canvas) {
        panel(c, 850f, 65f, 1250f, 390f, 0xF20B1928.toInt(), 20f)
        label(c, "QUICK SETTINGS", 885f, 110f, 14f, 0xFF8EDBFF.toInt(), true)
        label(c, "External display", 885f, 165f, 19f, 0xFFFFFFFF.toInt(), true)
        label(c, "KK Dex desktop session", 885f, 194f, 13f, 0xFF9FB7CE.toInt())
        label(c, "Appearance", 885f, 255f, 19f, 0xFFFFFFFF.toInt(), true)
        label(c, "Wallpaper and desktop style", 885f, 284f, 13f, 0xFF9FB7CE.toInt())
        label(c, "Tap outside this panel to close", 885f, 345f, 12f, 0xFF8FAFCB.toInt())
    }

    private fun drawDesktopTile(c: Canvas, x: Float, y: Float, app: AppTile) {
        panel(c, x + 16f, y, x + 80f, y + 64f, app.color, 17f)
        label(c, app.symbol, x + 34f, y + 43f, 28f, 0xFFFFFFFF.toInt(), true)
        label(c, app.name, x + 48f, y + 91f, 14f, 0xFFFFFFFF.toInt(), true, Paint.Align.CENTER)
    }

    private fun drawQuickApp(c: Canvas, x: Float, y: Float, app: AppTile) {
        panel(c, x, y, x + 47f, y + 43f, app.color, 10f)
        label(c, app.symbol, x + 12f, y + 29f, 21f, 0xFFFFFFFF.toInt(), true)
        label(c, app.name, x + 62f, y + 27f, 16f, 0xFFFFFFFF.toInt(), true)
        label(c, app.detail, x + 210f, y + 27f, 11f, 0xFF9FB7CE.toInt())
    }

    private fun pill(c: Canvas, x: Float, y: Float, w: Float, h: Float, title: String, color: Int) {
        panel(c, x, y, x + w, y + h, color, 12f)
        label(c, title, x + w / 2f, y + h / 2f + 5f, 12f, 0xFFFFFFFF.toInt(), true, Paint.Align.CENTER)
    }

    private fun panel(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int, radius: Float) {
        p.shader = null
        p.color = color
        p.style = Paint.Style.FILL
        c.drawRoundRect(l, t, r, b, radius, radius, p)
        if (color ushr 24 > 0x80) {
            p.color = 0x18FFFFFF
            p.style = Paint.Style.STROKE
            p.strokeWidth = 1f
            c.drawRoundRect(l, t, r, b, radius, radius, p)
            p.style = Paint.Style.FILL
        }
    }

    private fun label(c: Canvas, value: String, x: Float, y: Float, size: Float, color: Int, bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT) {
        p.shader = null
        p.color = color
        p.textSize = size
        p.textAlign = align
        p.typeface = Typeface.create(if (bold) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
        c.drawText(value, x, y, p)
        p.textAlign = Paint.Align.LEFT
    }

    private fun drawPointer(c: Canvas) {
        val x = pointerX * 1280f
        val y = pointerY * 720f
        val path = Path().apply {
            moveTo(x, y); lineTo(x + 3f, y + 27f); lineTo(x + 10f, y + 20f)
            lineTo(x + 18f, y + 36f); lineTo(x + 25f, y + 32f)
            lineTo(x + 17f, y + 16f); lineTo(x + 28f, y + 15f); close()
        }
        p.color = 0xFF000000.toInt()
        p.style = Paint.Style.STROKE
        p.strokeWidth = 4f
        c.drawPath(path, p)
        p.style = Paint.Style.FILL
        p.color = 0xFFFFFFFF.toInt()
        c.drawPath(path, p)
        if (clickPulse) {
            p.color = 0xAA63D5FF.toInt()
            p.style = Paint.Style.STROKE
            p.strokeWidth = 3f
            c.drawCircle(x + 12f, y + 15f, 24f, p)
            p.style = Paint.Style.FILL
        }
    }

    private data class AppTile(val name: String, val detail: String, val symbol: String, val color: Int)
}
