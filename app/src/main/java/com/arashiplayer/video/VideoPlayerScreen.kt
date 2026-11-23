package com.arashiplayer.video

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Color as AndroidColor
import android.media.AudioManager
import android.net.Uri
import android.util.Rational
import android.util.TypedValue
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
import androidx.compose.ui.graphics.Brush
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
import com.arashiplayer.ui.theme.GlassCyan
import com.arashiplayer.ui.theme.liquidGlass
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    videoUri: Uri,
    videoTitle: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    var subtitleUri by remember { mutableStateOf<Uri?>(null) }
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

    val exoPlayer = remember(videoUri, subtitleUri) {
        ExoPlayer.Builder(context).build().apply {
            val builder = MediaItem.Builder().setUri(videoUri)
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
            setMediaItem(builder.build())
            prepare()
            playWhenReady = true
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
        val actual = if (isLongPressBoost) 2.5f else playbackSpeed
        exoPlayer.playbackParameters = PlaybackParameters(actual)
    }

    LaunchedEffect(isLooping) {
        exoPlayer.repeatMode = if (isLooping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    LaunchedEffect(hudText) {
        if (hudText != null && !isLongPressBoost) {
            delay(1200)
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

    DisposableEffect(exoPlayer) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            exoPlayer.release()
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
                                else -> {
                                    if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                }
                            }
                        }
                    },
                    onLongPress = {
                        if (!isLocked) {
                            isLongPressBoost = true
                            hudText = "2.5x 快进中"
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
                        val deltaMs = (dragAmount * 120).toLong()
                        val target = (exoPlayer.currentPosition + deltaMs).coerceIn(0L, duration.coerceAtLeast(1L))
                        exoPlayer.seekTo(target)
                        hudText = formatDuration(target)
                    }
                }
            }
            .pointerInput(isLocked) {
                if (!isLocked) {
                    detectVerticalDragGestures { change, dragAmount ->
                        val isLeft = change.position.x < size.width / 2f
                        if (isLeft) {
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

        // 中央液态玻璃手势 HUD
        hudText?.let { text ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .liquidGlass(RoundedCornerShape(18.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(text, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // 顶部液态玻璃控制栏
        AnimatedVisibility(
            visible = showControls && !isLocked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                    .statusBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White)
                }
                Text(
                    text = videoTitle,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { isLooping = !isLooping }) {
                    Icon(
                        Icons.Default.RepeatOne,
                        contentDescription = null,
                        tint = if (isLooping) GlassCyan else Color.White
                    )
                }
                IconButton(onClick = { showTimerSheet = true }) {
                    Icon(
                        Icons.Default.Timer,
                        contentDescription = null,
                        tint = if (sleepRemainingSec > 0) GlassCyan else Color.White
                    )
                }
                IconButton(onClick = {
                    runCatching {
                        activity?.enterPictureInPictureMode(
                            PictureInPictureParams.Builder()
                                .setAspectRatio(Rational(16, 9))
                                .build()
                        )
                    }
                }) {
                    Icon(Icons.Default.PictureInPictureAlt, contentDescription = null, tint = Color.White)
                }
                IconButton(onClick = { subtitlePicker.launch(arrayOf("*/*")) }) {
                    Icon(Icons.Default.Subtitles, contentDescription = null, tint = Color.White)
                }
                IconButton(onClick = { showSubtitleSheet = true }) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = GlassCyan)
                }
            }
        }

        // 左侧防误触锁
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 16.dp)
        ) {
            IconButton(
                onClick = { isLocked = !isLocked },
                modifier = Modifier.liquidGlass(CircleShape)
            ) {
                Icon(
                    imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = null,
                    tint = Color.White
                )
            }
        }

        // 底部液态玻璃进度条与专业控制栏
        AnimatedVisibility(
            visible = showControls && !isLocked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD000000))))
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatDuration(currentPos), color = Color.White, fontSize = 12.sp)
                    Slider(
                        value = if (duration > 0L) (currentPos.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f,
                        onValueChange = { frac ->
                            exoPlayer.seekTo((frac * duration).toLong())
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = GlassCyan,
                            activeTrackColor = GlassCyan
                        ),
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )
                    Text(formatDuration(duration), color = Color.LightGray, fontSize = 12.sp)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { exoPlayer.seekTo((currentPos - 10_000L).coerceAtLeast(0L)) }) {
                            Icon(Icons.Default.Replay10, contentDescription = null, tint = Color.White)
                        }
                        IconButton(onClick = { if (isPlaying) exoPlayer.pause() else exoPlayer.play() }) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        IconButton(onClick = { exoPlayer.seekTo((currentPos + 10_000L).coerceAtMost(duration)) }) {
                            Icon(Icons.Default.Forward10, contentDescription = null, tint = Color.White)
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { showSpeedSheet = true }) {
                            Text("${playbackSpeed}x", color = GlassCyan, fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = {
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
                        }) {
                            Text("比例", color = Color.White)
                        }
                        IconButton(onClick = {
                            isLandscape = !isLandscape
                            activity?.requestedOrientation = if (isLandscape) {
                                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            } else {
                                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            }
                        }) {
                            Icon(Icons.Default.ScreenRotation, contentDescription = null, tint = Color.White)
                        }
                    }
                }
            }
        }

        // 倍速选择弹窗
        if (showSpeedSheet) {
            val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 2.5f, 3.0f)
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0x66000000)).clickable { showSpeedSheet = false },
                contentAlignment = Alignment.BottomCenter
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .liquidGlass(RoundedCornerShape(24.dp))
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    speeds.forEach { spd ->
                        Text(
                            text = "${spd}x",
                            color = if (spd == playbackSpeed) GlassCyan else Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    playbackSpeed = spd
                                    showSpeedSheet = false
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // 定时关闭弹窗
        if (showTimerSheet) {
            val mins = listOf(0, 15, 30, 60)
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0x66000000)).clickable { showTimerSheet = false },
                contentAlignment = Alignment.BottomCenter
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .liquidGlass(RoundedCornerShape(24.dp))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    mins.forEach { m ->
                        Text(
                            text = if (m == 0) "关闭定时" else "${m}分钟",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    sleepRemainingSec = if (m == 0) -1 else m * 60
                                    hudText = if (m == 0) "已取消定时" else "${m}分钟后暂停"
                                    showTimerSheet = false
                                }
                                .padding(10.dp)
                        )
                    }
                }
            }
        }

        // 外挂字幕样式设置面板
        if (showSubtitleSheet) {
            SubtitleStyleDialog(
                config = subtitleStyle,
                onUpdate = { subtitleStyle = it },
                onDismiss = { showSubtitleSheet = false }
            )
        }
    }
}

@Composable
private fun SubtitleStyleDialog(
    config: SubtitleStyleConfig,
    onUpdate: (SubtitleStyleConfig) -> Unit,
    onDismiss: () -> Unit
) {
    val presetColors = listOf(
        Color.White,
        Color(0xFFFFE066),
        Color(0xFF64E8FF),
        Color(0xFF7BF1A8),
        Color(0xFFFF99C8),
        Color(0xFFFFB703)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x80000000))
            .clickable { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
                .liquidGlass(RoundedCornerShape(28.dp), alphaTop = 0.22f, alphaBottom = 0.10f)
                .clickable(enabled = false) {}
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("字幕样式", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                presetColors.forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(32.dp)
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
