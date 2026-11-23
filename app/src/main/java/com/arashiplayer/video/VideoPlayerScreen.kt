package com.arashiplayer.video

import android.app.Activity
import android.content.Context
import android.graphics.Color as AndroidColor
import android.media.AudioManager
import android.net.Uri
import android.util.TypedValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.arashiplayer.data.SubtitleStyleConfig
import com.arashiplayer.ui.theme.AccentCyan
import com.arashiplayer.ui.theme.SurfaceGlass

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
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var isLocked by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var showSubtitleSheet by remember { mutableStateOf(false) }

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

    LaunchedEffect(playbackSpeed) {
        exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
    }

    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    val subtitlePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) subtitleUri = uri
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(isLocked) {
                if (!isLocked) {
                    detectVerticalDragGestures { change, dragAmount ->
                        val isLeftHalf = change.position.x < size.width / 2f
                        if (isLeftHalf) {
                            // 左侧上下滑动调节屏幕亮度
                            activity?.window?.let { win ->
                                val lp = win.attributes
                                val current = if (lp.screenBrightness < 0f) 0.5f else lp.screenBrightness
                                lp.screenBrightness = (current - dragAmount / 1000f).coerceIn(0.05f, 1f)
                                win.attributes = lp
                            }
                        } else {
                            // 右侧上下滑动调节媒体音量
                            if (dragAmount < -18f) {
                                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0)
                            } else if (dragAmount > 18f) {
                                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0)
                            }
                        }
                    }
                }
            }
            .clickable { showControls = !showControls }
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = !isLocked
                    this.resizeMode = resizeMode
                }
            },
            update = { playerView ->
                playerView.player = exoPlayer
                playerView.useController = !isLocked
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
                    val bgColor = AndroidColor.argb(bgAlpha, 0, 0, 0)
                    subView.setStyle(
                        CaptionStyleCompat(
                            fgColor,
                            bgColor,
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

        // 顶部专业工具栏（仿 QQ 影音 / Reex）
        AnimatedVisibility(
            visible = showControls && !isLocked,
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x99000000))
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
                }
                Text(
                    text = videoTitle,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                // 倍速切换
                TextButton(onClick = {
                    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f)
                    val idx = speeds.indexOf(playbackSpeed)
                    playbackSpeed = speeds[(idx + 1) % speeds.size]
                }) {
                    Text("${playbackSpeed}x", color = AccentCyan, fontWeight = FontWeight.Bold)
                }
                // 画面比例
                IconButton(onClick = {
                    resizeMode = when (resizeMode) {
                        AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                }) {
                    Icon(Icons.Default.AspectRatio, contentDescription = "画面比例", tint = Color.White)
                }
                // 外挂字幕选择
                IconButton(onClick = { subtitlePicker.launch(arrayOf("*/*")) }) {
                    Icon(Icons.Default.Subtitles, contentDescription = "加载外挂字幕", tint = Color.White)
                }
                // 字幕样式设置（颜色/透明度/位置/大小）
                IconButton(onClick = { showSubtitleSheet = true }) {
                    Icon(Icons.Default.Tune, contentDescription = "字幕样式", tint = AccentCyan)
                }
            }
        }

        // 锁屏防误触按钮
        if (showControls) {
            IconButton(
                onClick = { isLocked = !isLocked },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 16.dp)
                    .clip(CircleShape)
                    .background(SurfaceGlass)
            ) {
                Icon(
                    imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = "锁定屏幕",
                    tint = Color.White
                )
            }
        }

        // 外挂字幕样式实时调节面板
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
        Color(0xFF5CE1E6),
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
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(Color(0xF0161925))
                .clickable(enabled = false) {}
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("外挂字幕样式", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)

            Text("字体颜色", color = Color.LightGray, fontSize = 13.sp)
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

            Text("字体大小: ${config.textSizeSp.toInt()} sp", color = Color.LightGray, fontSize = 13.sp)
            Slider(
                value = config.textSizeSp,
                onValueChange = { onUpdate(config.copy(textSizeSp = it)) },
                valueRange = 12f..36f
            )

            Text("文字透明度: ${(config.textOpacity * 100).toInt()}%", color = Color.LightGray, fontSize = 13.sp)
            Slider(
                value = config.textOpacity,
                onValueChange = { onUpdate(config.copy(textOpacity = it)) },
                valueRange = 0.2f..1.0f
            )

            Text("背景透明度: ${(config.backgroundOpacity * 100).toInt()}%", color = Color.LightGray, fontSize = 13.sp)
            Slider(
                value = config.backgroundOpacity,
                onValueChange = { onUpdate(config.copy(backgroundOpacity = it)) },
                valueRange = 0.0f..0.9f
            )

            Text("垂直位置（距底边 ${(config.bottomPaddingFraction * 100).toInt()}%）", color = Color.LightGray, fontSize = 13.sp)
            Slider(
                value = config.bottomPaddingFraction,
                onValueChange = { onUpdate(config.copy(bottomPaddingFraction = it)) },
                valueRange = 0.02f..0.45f
            )
        }
    }
}
