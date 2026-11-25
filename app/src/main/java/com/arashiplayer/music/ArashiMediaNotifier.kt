package com.arashiplayer.music

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import com.arashiplayer.MainActivity
import com.arashiplayer.R

class ArashiMediaNotifier(
    private val context: Context,
    player: Player
) {
    private val channelId = "arashi_playback_channel"
    private val notificationId = 6001
    private val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val mediaSession = MediaSession.Builder(context, player)
        .setId("arashi_island_session")
        .build()

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                channelId,
                "岚播放器正在播放",
                NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(ch)
        }
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun updatePlaybackNotification(title: String, artist: String, isPlaying: Boolean) {
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.app_logo_icon)
            .setContentTitle(title)
            .setContentText(artist.ifBlank { "岚播放器" })
            .setContentIntent(pi)
            .setOngoing(isPlaying)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(MediaStyleNotificationHelper.MediaStyle(mediaSession))
            .build()
        runCatching { nm.notify(notificationId, notification) }
    }

    fun clear() {
        runCatching { nm.cancel(notificationId) }
    }

    fun release() {
        clear()
        runCatching { mediaSession.release() }
    }
}
