package com.arashiplayer.video

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.TypedValue
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.arashiplayer.data.SubtitleStyleConfig
import com.arashiplayer.data.formatDuration
import com.arashiplayer.ui.theme.LocalArashiTokens
import com.arashiplayer.ui.theme.visionGlassCapsule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    exoPlayer: ExoPlayer,
    videoUri: Uri,
    videoTitle: String,
    hasPrev: Boolean,
    hasNext: Boolean,
    onPrevVideo: () -> Unit,
    onNextVideo: () -> Unit,
    onEnterMiniWindow: () -> Unit,
    onBackgroundAudioMode: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val tokens = LocalArashiTokens.current
    val scope = rememberCoroutineScope()
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    var subtitleUri by remember(videoUri) { mutableStateOf<Uri?>(null) }
    var subtitleStyle by remember { mutableStateOf(SubtitleStyleConfig()) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var isLongPressBoost by remember { mutableStateOf(false) }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var isLocked by remember { mutableStateOf(false) }
    var isLooping by remember { mutableStateOf(false) }
    var isLandscape by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var showSubtitleSheet by remember { mutableStateOf(false) }
    var showSpeedSheet by remember { mutableStateOf(false) }
    var showTimerSheet by remember { mutableStateOf(false) }
    var hudText by remember { mutableStateOf<String?>(null) }
    var sleepRemainingSec by remember { mutableIntStateOf(-1) }

    var isPlaying by remember { mutableStateOf(true) }
    var currentPos by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }

    // 系统返回键返回上一界面，不直接退出 App
    BackHandler {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        onBack()
    }

    LaunchedEffect(videoUri, subtitleUri) {
        val currentId = exoPlayer.currentMediaItem?.mediaId
        val targetId = "$videoUri|$subtitleUri"
        if (currentId != targetId) {
            val prevPos = if (currentId?.startsWith("$videoUri|") == true) exoPlayer.currentPosition else 0L
            val builder = MediaItem.Builder().setMediaId(targetId).setUri(videoUri)
            subtitleUri?.let { subUri ->
                val mime = when {
                    subUri.toString().endsWith(".vtt", true) -> MimeTypes.TEXT_VTT
                    subUri.toString().endsWith(".ass", true) || subUri.toString().endsWith(".ssa", true) -> MimeTypes.TEXT_SSA
                    else -> MimeTypes.APPLICATION_SUBRIP
                }
                val subConfig = MediaItem.SubtitleConfiguration.Builder(subUri)
                    .setMimeType(mime)
                    .setLanguage("zh")
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build()
                builder.setSubtitleConfigurations(listOf(subConfig))
            }
            exoPlayer.setMediaItem(builder.build(), prevPos)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        }
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            isPlaying = exoPlayer.isPlaying
            currentPos = exoPlayer.currentPosition.coerceAtLeast(0L)
            duration = exoPlayer.duration.coerceAtLeast(0L)
            delay(300)
        }
    }

    LaunchedEffect(playbackSpeed, isLongPressBoost) {
        exoPlayer.playbackParameters = PlaybackParameters(if (isLongPressBoost) 2.5f else playbackSpeed)
    }

    LaunchedEffect(isLooping) {
        exoPlayer.repeatMode = if (isLooping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    LaunchedEffect(hudText) {
        if (hudText != null && !isLongPressBoost) {
            delay(1300)
            hudText = null
        }
    }

    LaunchedEffect(sleepRemainingSec) {
        if (sleepRemainingSec > 0) {
            delay(1000)
            sleepRemainingSec -= 1
        } else if (sleepRemainingSec == 0) {
            exoPlayer.pause()
            sleepRemainingSec = -1
        }
    }

    val subtitlePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            subtitleUri = uri
            hudText = "已加载外挂字幕"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(isLocked) {
                detectTapGestures(
                    onTap = { showControls = !showControls },
                    onDoubleTap = { offset ->
                        if (!isLocked) {
                            when {
                                offset.x < size.width * 0.3f -> {
                                    exoPlayer.seekTo((exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L))
                                    hudText = "-10s"
                                }
                                offset.x > size.width * 0.7f -> {
                                    exoPlayer.seekTo((exoPlayer.currentPosition + 10_000L).coerceAtMost(duration))
                                    hudText = "+10s"
                                }
                                else -> if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                            }
                        }
                    },
                    onLongPress = {
                        if (!isLocked) {
                            isLongPressBoost = true
                            hudText = "2.5x"
                        }
                    },
                    onPress = {
                        tryAwaitRelease()
                        if (isLongPressBoost) {
                            isLongPressBoost = false
                            hudText = null
                        }
                    }
                )
            }
            .pointerInput(isLocked) {
                if (!isLocked) {
                    detectHorizontalDragGestures { _, dragAmount ->
                        val target = (exoPlayer.currentPosition + (dragAmount * 120).toLong()).coerceIn(0L, duration.coerceAtLeast(1L))
                        exoPlayer.seekTo(target)
                        hudText = formatDuration(target)
                    }
                }
            }
            .pointerInput(isLocked) {
                if (!isLocked) {
                    detectVerticalDragGestures { change, dragAmount ->
                        if (change.position.x < size.width / 2f) {
                            activity?.window?.let { win ->
                                val lp = win.attributes
                                val cur = if (lp.screenBrightness < 0f) 0.5f else lp.screenBrightness
                                val next = (cur - dragAmount / 900f).coerceIn(0.05f, 1f)
                                lp.screenBrightness = next
                                win.attributes = lp
                                hudText = "亮度 ${(next * 100).toInt()}%"
                            }
                        } else {
                            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                            if (dragAmount < -18f) {
                                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0)
                            } else if (dragAmount > 18f) {
                                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0)
                            }
                            val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                            hudText = "音量 ${(curVol * 100 / maxVol.coerceAtLeast(1))}%"
                        }
                    }
                }
            }
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    this.resizeMode = resizeMode
                }
            },
            update = { playerView ->
                playerView.player = exoPlayer
                playerView.resizeMode = resizeMode
                playerView.subtitleView?.let { subView ->
                    val alphaText = (subtitleStyle.textOpacity * 255).toInt().coerceIn(0, 255)
                    val fgColor = AndroidColor.argb(
                        alphaText,
                        AndroidColor.red(subtitleStyle.textColorArgb),
                        AndroidColor.green(subtitleStyle.textColorArgb),
                        AndroidColor.blue(subtitleStyle.textColorArgb)
                    )
                    val bgAlpha = (subtitleStyle.backgroundOpacity * 255).toInt().coerceIn(0, 255)
                    subView.setStyle(
                        CaptionStyleCompat(
                            fgColor,
                            AndroidColor.argb(bgAlpha, 0, 0, 0),
                            AndroidColor.TRANSPARENT,
                            CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
                            AndroidColor.BLACK,
                            null
                        )
                    )
                    subView.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, subtitleStyle.textSizeSp)
                    subView.setBottomPaddingFraction(subtitleStyle.bottomPaddingFraction)
                }
            }
        )

        // 中央悬浮玻璃 HUD
        hudText?.let { text ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 64.dp)
                    .visionGlassCapsule(RoundedCornerShape(22.dp))
                    .padding(horizontal = 18.dp, vertical = 9.dp)
            ) {
                Text(text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // 顶部 visionOS 分离式悬浮玻璃胶囊（无全宽黑条）
        AnimatedVisibility(
            visible = showControls && !isLocked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    IconButton(
                        onClick = {
                            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                            onBack()
                        },
                        modifier = Modifier.size(42.dp).visionGlassCapsule(CircleShape)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White)
                    }
                    Box(
                        modifier = Modifier
                            .visionGlassCapsule(RoundedCornerShape(22.dp))
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(videoTitle, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                    }
                }

                Spacer(Modifier.width(10.dp))

                // 右上角功能胶囊：定时 / 后台听音频 / 截图 / 小窗 / 外挂字幕 / 字幕样式
                Row(
                    modifier = Modifier
                        .visionGlassCapsule(RoundedCornerShape(24.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { showTimerSheet = true }, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Default.Timer, contentDescription = null, tint = if (sleepRemainingSec > 0) tokens.accent else Color.White, modifier = Modifier.size(20.dp))
                    }
                    IconButton(
                        onClick = {
                            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                            onBackgroundAudioMode()
                        },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(Icons.Default.Headphones, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    IconButton(
                        onClick = {
                            val posUs = exoPlayer.currentPosition * 1000L
                            scope.launch {
                                val ok = captureVideoFrameToGallery(context, videoUri, posUs)
                                hudText = if (ok) "已保存截图" else "截图失败"
                            }
                        },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    IconButton(
                        onClick = {
                            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                            onEnterMiniWindow()
                        },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(Icons.Default.PictureInPictureAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = { subtitlePicker.launch(arrayOf("*/*")) }, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Default.Subtitles, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = { showSubtitleSheet = true }, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Default.Tune, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        // 左侧独立圆形锁屏胶囊
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 16.dp)
        ) {
            IconButton(
                onClick = { isLocked = !isLocked },
                modifier = Modifier.size(44.dp).visionGlassCapsule(CircleShape)
            ) {
                Icon(if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen, contentDescription = null, tint = Color.White)
            }
        }

        // 底部 visionOS 悬浮液态玻璃控制胶囊（含 上一个/暂停/下一个 + 倍速/比例/旋转）
        AnimatedVisibility(
            visible = showControls && !isLocked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 18.dp)
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .visionGlassCapsule(RoundedCornerShape(30.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatDuration(currentPos), color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
                    Slider(
                        value = if (duration > 0L) (currentPos.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f,
                        onValueChange = { exoPlayer.seekTo((it * duration).toLong()) },
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier.weight(1f).height(26.dp).padding(horizontal = 8.dp)
                    )
                    Text(formatDuration(duration), color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onPrevVideo, enabled = hasPrev, modifier = Modifier.size(38.dp)) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = null, tint = if (hasPrev) Color.White else Color.White.copy(alpha = 0.35f))
                        }
                        IconButton(onClick = { if (isPlaying) exoPlayer.pause() else exoPlayer.play() }, modifier = Modifier.size(42.dp)) {
                            Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
                        }
                        IconButton(onClick = onNextVideo, enabled = hasNext, modifier = Modifier.size(38.dp)) {
                            Icon(Icons.Default.SkipNext, contentDescription = null, tint = if (hasNext) Color.White else Color.White.copy(alpha = 0.35f))
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.16f))
                                .clickable { showSpeedSheet = true }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("${playbackSpeed}x", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Text(
                            text = "比例",
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    resizeMode = when (resizeMode) {
                                        AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    }
                                    hudText = when (resizeMode) {
                                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "满屏裁剪"
                                        AspectRatioFrameLayout.RESIZE_MODE_FILL -> "拉伸填充"
                                        else -> "原始比例"
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                        IconButton(onClick = { isLooping = !isLooping }, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.RepeatOne, contentDescription = null, tint = if (isLooping) tokens.accent else Color.White, modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = {
                                isLandscape = !isLandscape
                                activity?.requestedOrientation = if (isLandscape) {
                                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                } else {
                                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                }
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.Default.ScreenRotation, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        if (showSpeedSheet) {
            val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 2.5f, 3.0f)
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0x55000000)).clickable { showSpeedSheet = false },
                contentAlignment = Alignment.BottomCenter
            ) {
                Row(
                    modifier = Modifier
                        .padding(20.dp)
                        .visionGlassCapsule(RoundedCornerShape(24.dp))
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    speeds.forEach { spd ->
                        Text(
                            text = "${spd}x",
                            color = if (spd == playbackSpeed) tokens.accent else Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { playbackSpeed = spd; showSpeedSheet = false }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        if (showTimerSheet) {
            val mins = listOf(0, 15, 30, 60)
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0x55000000)).clickable { showTimerSheet = false },
                contentAlignment = Alignment.BottomCenter
            ) {
                Row(
                    modifier = Modifier
                        .padding(20.dp)
                        .visionGlassCapsule(RoundedCornerShape(24.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    mins.forEach { m ->
                        Text(
                            text = if (m == 0) "关闭" else "${m}分钟",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    sleepRemainingSec = if (m == 0) -1 else m * 60
                                    hudText = if (m == 0) "已取消定时" else "${m}分钟后暂停"
                                    showTimerSheet = false
                                }
                                .padding(8.dp)
                        )
                    }
                }
            }
        }

        if (showSubtitleSheet) {
            SubtitleStyleDialog(
                config = subtitleStyle,
                onUpdate = { subtitleStyle = it },
                onDismiss = { showSubtitleSheet = false }
            )
        }
    }
}

private suspend fun captureVideoFrameToGallery(context: Context, videoUri: Uri, positionUs: Long): Boolean =
    withContext(Dispatchers.IO) {
        runCatching {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, videoUri)
            val bmp = retriever.getFrameAtTime(positionUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
            retriever.release()
            if (bmp == null) return@runCatching false

            val name = "Arashi_${System.currentTimeMillis()}.png"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/ArashiPlayer")
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return@runCatching false
            context.contentResolver.openOutputStream(uri)?.use { out ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            bmp.recycle()
            true
        }.getOrDefault(false)
    }

@Composable
private fun SubtitleStyleDialog(
    config: SubtitleStyleConfig,
    onUpdate: (SubtitleStyleConfig) -> Unit,
    onDismiss: () -> Unit
) {
    val presetColors = listOf(
        Color.White, Color(0xFFFFE066), Color(0xFF64E8FF),
        Color(0xFF7BF1A8), Color(0xFFFF99C8), Color(0xFFFFB703)
    )
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0x66000000)).clickable { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .padding(16.dp)
                .visionGlassCapsule(RoundedCornerShape(28.dp))
                .clickable(enabled = false) {}
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("字幕样式", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                presetColors.forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(color)
                            .clickable { onUpdate(config.copy(textColorArgb = color.toArgb())) }
                    )
                }
            }
            Text("大小 ${config.textSizeSp.toInt()}sp", color = Color.White, fontSize = 13.sp)
            Slider(value = config.textSizeSp, onValueChange = { onUpdate(config.copy(textSizeSp = it)) }, valueRange = 12f..36f)
            Text("文字透明度 ${(config.textOpacity * 100).toInt()}%", color = Color.White, fontSize = 13.sp)
            Slider(value = config.textOpacity, onValueChange = { onUpdate(config.copy(textOpacity = it)) }, valueRange = 0.2f..1.0f)
            Text("背景透明度 ${(config.backgroundOpacity * 100).toInt()}%", color = Color.White, fontSize = 13.sp)
            Slider(value = config.backgroundOpacity, onValueChange = { onUpdate(config.copy(backgroundOpacity = it)) }, valueRange = 0.0f..0.9f)
            Text("位置 ${(config.bottomPaddingFraction * 100).toInt()}%", color = Color.White, fontSize = 13.sp)
            Slider(value = config.bottomPaddingFraction, onValueChange = { onUpdate(config.copy(bottomPaddingFraction = it)) }, valueRange = 0.02f..0.45f)
        }
    }
}
