package com.arashiplayer.data

import android.content.Context
import android.net.Uri
import java.io.File

enum class VideoLayoutMode { FOLDER_ROW, COLUMN_LIST, GRID }

data class LocalVideo(
    val id: Long,
    val title: String,
    val path: String,
    val uri: Uri,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val sizeBytes: Long
) {
    val folderName: String
        get() = File(path).parentFile?.name ?: "默认"

    val resolutionBadge: String
        get() = when {
            height >= 2160 || width >= 3840 -> "4K"
            height >= 1080 -> "1080P"
            height >= 720 -> "720P"
            else -> "HD"
        }
}

data class LocalAudio(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val path: String,
    val uri: Uri,
    val albumArtUri: Uri?,
    val durationMs: Long
)

data class LocalImage(
    val id: Long,
    val title: String,
    val path: String,
    val uri: Uri,
    val sizeBytes: Long
)

data class SubtitleStyleConfig(
    val textColorArgb: Int = android.graphics.Color.WHITE,
    val backgroundOpacity: Float = 0.35f,
    val textOpacity: Float = 1.0f,
    val textSizeSp: Float = 18f,
    val bottomPaddingFraction: Float = 0.08f
)

data class OnlineTagResult(
    val songId: String,
    val title: String,
    val artist: String,
    val album: String,
    val coverUrl: String?,
    val syncedLyrics: String?,
    val translatedLyrics: String? = null,
    val phoneticLyrics: String? = null,
    val source: String
)

data class LyricLine(
    val timestampMs: Long,
    val text: String,
    val translation: String? = null,
    val phonetic: String? = null
)

class FavoritesStore(context: Context) {
    private val prefs = context.getSharedPreferences("arashi_favorites", Context.MODE_PRIVATE)

    fun isFavorite(path: String): Boolean = prefs.getBoolean("fav_$path", false)

    fun toggleFavorite(path: String): Boolean {
        val next = !isFavorite(path)
        prefs.edit().putBoolean("fav_$path", next).apply()
        return next
    }
}

fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> "%.2f GB".format(gb)
        mb >= 1.0 -> "%.1f MB".format(mb)
        else -> "%.0f KB".format(kb)
    }
}
