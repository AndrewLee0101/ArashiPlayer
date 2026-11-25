package com.arashiplayer.music

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

data class DesktopLyricsConfig(
    val enabled: Boolean = false,
    val fontSizeSp: Float = 19f,
    val textColorArgb: Int = Color.WHITE
)

val DesktopLyricColorSwatches = listOf(
    "纯白" to Color.WHITE,
    "冰川青" to 0xFF64E8FF.toInt(),
    "暖阳黄" to 0xFFFFE066.toInt(),
    "樱花粉" to 0xFFFF7EB6.toInt(),
    "薄荷绿" to 0xFF6EE7B7.toInt()
)

class DesktopLyricsStore(context: Context) {
    private val prefs = context.getSharedPreferences("arashi_desktop_lyrics_v6", Context.MODE_PRIVATE)

    fun load(): DesktopLyricsConfig = DesktopLyricsConfig(
        enabled = prefs.getBoolean("enabled", false),
        fontSizeSp = prefs.getFloat("fontSizeSp", 19f),
        textColorArgb = prefs.getInt("textColorArgb", Color.WHITE)
    )

    fun save(config: DesktopLyricsConfig) {
        prefs.edit()
            .putBoolean("enabled", config.enabled)
            .putFloat("fontSizeSp", config.fontSizeSp)
            .putInt("textColorArgb", config.textColorArgb)
            .apply()
    }
}

fun canDrawDesktopLyrics(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true

fun openOverlayPermissionSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        runCatching {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(intent)
        }
    }
}

class DesktopLyricsOverlayController(private val context: Context) {
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var container: LinearLayout? = null
    private var mainLineView: TextView? = null
    private var transLineView: TextView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var isAttached = false

    fun update(
        config: DesktopLyricsConfig,
        lyricText: String?,
        translationText: String?
    ) {
        if (!config.enabled || !canDrawDesktopLyrics(context) || lyricText.isNullOrBlank()) {
            hide()
            return
        }
        ensureAttached()
        mainLineView?.apply {
            text = lyricText
            setTextColor(config.textColorArgb)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, config.fontSizeSp)
        }
        transLineView?.apply {
            if (translationText.isNullOrBlank()) {
                visibility = android.view.View.GONE
            } else {
                visibility = android.view.View.VISIBLE
                text = translationText
                setTextColor(config.textColorArgb)
                alpha = 0.84f
                setTextSize(TypedValue.COMPLEX_UNIT_SP, (config.fontSizeSp - 4f).coerceAtLeast(12f))
            }
        }
    }

    private fun ensureAttached() {
        if (isAttached) return
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(28, 12, 28, 12)
        }
        val mainTv = TextView(context).apply {
            gravity = Gravity.CENTER
            setShadowLayer(8f, 0f, 2f, Color.argb(185, 0, 0, 0))
            paint.isFakeBoldText = true
        }
        val transTv = TextView(context).apply {
            gravity = Gravity.CENTER
            setShadowLayer(6f, 0f, 2f, Color.argb(175, 0, 0, 0))
        }
        box.addView(mainTv)
        box.addView(transTv)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 1400
        }

        var startY = 0
        var touchY = 0f
        box.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startY = lp.y
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    lp.y = (startY + (event.rawY - touchY)).toInt().coerceAtLeast(80)
                    runCatching { wm.updateViewLayout(box, lp) }
                    true
                }
                else -> false
            }
        }

        runCatching {
            wm.addView(box, lp)
            container = box
            mainLineView = mainTv
            transLineView = transTv
            layoutParams = lp
            isAttached = true
        }
    }

    fun hide() {
        if (!isAttached) return
        container?.let { runCatching { wm.removeView(it) } }
        container = null
        mainLineView = null
        transLineView = null
        layoutParams = null
        isAttached = false
    }
}
