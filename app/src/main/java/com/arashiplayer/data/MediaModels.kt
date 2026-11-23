package com.arashiplayer.data

import android.net.Uri

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
    val resolutionBadge: String
        get() = when {
            height >= 2160 || width >= 3840 -> "4K"
            height >= 1080 -> "1080P"
            height >= 720 -> "720P"
            else -> "SD"
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

data class SubtitleStyleConfig(
    val textColorArgb: Int = android.graphics.Color.WHITE,
    val backgroundOpacity: Float = 0.45f,
    val textOpacity: Float = 1.0f,
    val textSizeSp: Float = 18f,
    val bottomPaddingFraction: Float = 0.08f
)

data class OnlineTagResult(
    val title: String,
    val artist: String,
    val album: String,
    val coverUrl: String?,
    val syncedLyrics: String?
)

data class LyricLine(
    val timestampMs: Long,
    val text: String
)
