package com.arashiplayer

import android.Manifest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.Coil
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import com.arashiplayer.data.*
import com.arashiplayer.music.AppleMusicPlayerSheet
import com.arashiplayer.music.OnlineTagMatcher
import com.arashiplayer.ui.screens.*
import com.arashiplayer.ui.theme.*
import com.arashiplayer.vault.PrivacyVaultManager
import com.arashiplayer.vault.VaultMediaType
import com.arashiplayer.video.VideoPlayerScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val imageLoader = ImageLoader.Builder(this)
            .components { add(VideoFrameDecoder.Factory()) }
            .crossfade(true)
            .build()
        Coil.setImageLoader(imageLoader)

        setContent {
            val themeStore = remember { ThemePrefsStore(this) }
            var themeState by remember { mutableStateOf(themeStore.load()) }

            ArashiPlayerTheme(themeState = themeState) {
                ArashiPlayerApp(
                    themeState = themeState,
                    onThemeChange = {
                        themeState = it
                        themeStore.save(it)
                    },
                    onExitApp = { finish() }
                )
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun ArashiPlayerApp(
    themeState: ArashiThemeState,
    onThemeChange: (ArashiThemeState) -> Unit,
    onExitApp: () -> Unit
) {
    val context = LocalContext.current
    val tokens = LocalArashiTokens.current
    val scope = rememberCoroutineScope()
    val repo = remember { MediaStoreRepository(context) }
    val vaultManager = remember { PrivacyVaultManager(context) }
    val tagMatcher = remember { OnlineTagMatcher(context) }
    val favoritesStore = remember { FavoritesStore(context) }

    // 独立视频播放器（支持后台音频播放 & 可缩放悬浮小窗无缝切换）
    val videoPlayer = remember { ExoPlayer.Builder(context).build() }
    val musicPlayer = remember { ExoPlayer.Builder(context).build() }

    DisposableEffect(Unit) {
        onDispose {
            videoPlayer.release()
            musicPlayer.release()
        }
    }

    var videos by remember { mutableStateOf<List<LocalVideo>>(emptyList()) }
    var audios by remember { mutableStateOf<List<LocalAudio>>(emptyList()) }
    var images by remember { mutableStateOf<List<LocalImage>>(emptyList()) }
    var currentTab by remember { mutableIntStateOf(0) }
    var tagVersion by remember { mutableIntStateOf(0) }
    var showSearchBar by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // 视频播放队列、全屏状态、悬浮可调大小小窗状态、后台音频播放状态
    var videoPlaylist by remember { mutableStateOf<List<Pair<Uri, String>>>(emptyList()) }
    var videoIndex by remember { mutableIntStateOf(-1) }
    var isVideoFullScreen by remember { mutableStateOf(false) }
    var isVideoMiniWindow by remember { mutableStateOf(false) }
    var isVideoBgAudio by remember { mutableStateOf(false) }
    var isVideoPlaying by remember { mutableStateOf(false) }

    // 可拖动 & 可调节大小的小窗坐标和宽度
    var miniOffsetX by remember { mutableFloatStateOf(300f) }
    var miniOffsetY by remember { mutableFloatStateOf(900f) }
    var miniWidthDp by remember { mutableFloatStateOf(210f) }

    // 音乐播放状态
    var activeTrack by remember { mutableStateOf<LocalAudio?>(null) }
    var isMusicPlaying by remember { mutableStateOf(false) }
    var musicPosMs by remember { mutableLongStateOf(0L) }
    var musicDurMs by remember { mutableLongStateOf(0L) }
    var showFullMusicSheet by remember { mutableStateOf(false) }

    // 主界面二次返回退出计时
    var lastBackPressMs by remember { mutableLongStateOf(0L) }

    fun refreshAll() {
        scope.launch {
            videos = repo.loadLocalVideos()
            audios = repo.loadLocalAudios()
            images = repo.loadLocalImages()
        }
    }

    val permissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_MEDIA_IMAGES
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refreshAll() }

    LaunchedEffect(Unit) {
        permLauncher.launch(permissions)
        refreshAll()
    }

    LaunchedEffect(Unit) {
        while (true) {
            isMusicPlaying = musicPlayer.isPlaying
            musicPosMs = musicPlayer.currentPosition.coerceAtLeast(0L)
            musicDurMs = musicPlayer.duration.coerceAtLeast(0L)
            isVideoPlaying = videoPlayer.isPlaying
            delay(300)
        }
    }

    // 搜索栏打开时按返回先收起搜索栏
    if (showSearchBar) {
        BackHandler { searchQuery = ""; showSearchBar = false }
    } else if (!isVideoFullScreen && !showFullMusicSheet) {
        // 主界面二次返回退出
        BackHandler {
            if (currentTab != 0) {
                currentTab = 0
            } else {
                val now = System.currentTimeMillis()
                if (now - lastBackPressMs <= 2000L) {
                    onExitApp()
                } else {
                    lastBackPressMs = now
                    Toast.makeText(context, "再按一次退出岚播放器", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 全屏视频播放页
    if (isVideoFullScreen && videoIndex in videoPlaylist.indices) {
        val (uri, title) = videoPlaylist[videoIndex]
        VideoPlayerScreen(
            exoPlayer = videoPlayer,
            videoUri = uri,
            videoTitle = title,
            hasPrev = videoIndex > 0,
            hasNext = videoIndex < videoPlaylist.lastIndex,
            onPrevVideo = { if (videoIndex > 0) videoIndex-- },
            onNextVideo = { if (videoIndex < videoPlaylist.lastIndex) videoIndex++ },
            onEnterMiniWindow = {
                isVideoFullScreen = false
                isVideoMiniWindow = true
                isVideoBgAudio = false
            },
            onBackgroundAudioMode = {
                isVideoFullScreen = false
                isVideoMiniWindow = false
                isVideoBgAudio = true
                Toast.makeText(context, "已切入后台音频播放", Toast.LENGTH_SHORT).show()
            },
            onBack = {
                videoPlayer.pause()
                isVideoFullScreen = false
                isVideoMiniWindow = false
                isVideoBgAudio = false
            }
        )
        return
    }

    // 全屏音乐播放页
    if (showFullMusicSheet && activeTrack != null) {
        val current = activeTrack!!
        AppleMusicPlayerSheet(
            track = current,
            isPlaying = isMusicPlaying,
            currentPositionMs = musicPosMs,
            durationMs = musicDurMs,
            onSeekTo = { musicPlayer.seekTo(it) },
            onTogglePlay = { if (musicPlayer.isPlaying) musicPlayer.pause() else musicPlayer.play() },
            onNext = {
                val idx = audios.indexOfFirst { it.id == current.id }
                if (audios.isNotEmpty()) {
                    val next = audios[(idx + 1) % audios.size]
                    activeTrack = next
                    musicPlayer.setMediaItem(MediaItem.fromUri(next.uri))
                    musicPlayer.prepare()
                    musicPlayer.play()
                }
            },
            onPrev = {
                val idx = audios.indexOfFirst { it.id == current.id }
                if (audios.isNotEmpty()) {
                    val prev = audios[(idx - 1 + audios.size) % audios.size]
                    activeTrack = prev
                    musicPlayer.setMediaItem(MediaItem.fromUri(prev.uri))
                    musicPlayer.prepare()
                    musicPlayer.play()
                }
            },
            onTagSaved = { tagVersion++ },
            onClose = { showFullMusicSheet = false }
        )
        return
    }

    ArashiBackdrop {
        when (currentTab) {
            0 -> VideoLibraryScreen(
                videos = videos,
                searchQuery = searchQuery,
                favoritesStore = favoritesStore,
                onToggleSearch = { showSearchBar = !showSearchBar },
                onOpenVault = { currentTab = 2 },
                onVideoClick = { clicked, list ->
                    musicPlayer.pause()
                    videoPlaylist = list.map { it.uri to it.title }
                    videoIndex = list.indexOfFirst { it.id == clicked.id }.coerceAtLeast(0)
                    isVideoFullScreen = true
                    isVideoMiniWindow = false
                    isVideoBgAudio = false
                },
                onRenameVideo = { v, newName ->
                    if (!vaultManager.hasAllFilesAccess()) openAllFilesPermission(context)
                    else { repo.renameMediaFile(v.path, newName); refreshAll() }
                },
                onDeleteVideos = { list ->
                    if (!vaultManager.hasAllFilesAccess()) openAllFilesPermission(context)
                    else { list.forEach { repo.deleteMediaFile(it.path, it.uri) }; refreshAll() }
                },
                onEncryptVideos = { list ->
                    if (!vaultManager.hasAllFilesAccess()) openAllFilesPermission(context)
                    else { list.forEach { vaultManager.encryptInPlace(it.path, VaultMediaType.VIDEO) }; refreshAll() }
                }
            )
            1 -> MusicLibraryScreen(
                audios = audios,
                searchQuery = searchQuery,
                tagVersion = tagVersion,
                favoritesStore = favoritesStore,
                onToggleSearch = { showSearchBar = !showSearchBar },
                onAudioClick = { audio ->
                    videoPlayer.pause()
                    isVideoMiniWindow = false
                    isVideoBgAudio = false
                    activeTrack = audio
                    musicPlayer.setMediaItem(MediaItem.fromUri(audio.uri))
                    musicPlayer.prepare()
                    musicPlayer.play()
                    showFullMusicSheet = true
                },
                onRenameAudio = { a, newName ->
                    if (!vaultManager.hasAllFilesAccess()) openAllFilesPermission(context)
                    else { repo.renameMediaFile(a.path, newName); refreshAll() }
                },
                onDeleteAudios = { list ->
                    if (!vaultManager.hasAllFilesAccess()) openAllFilesPermission(context)
                    else { list.forEach { repo.deleteMediaFile(it.path, it.uri) }; refreshAll() }
                },
                onEncryptAudios = { list ->
                    if (!vaultManager.hasAllFilesAccess()) openAllFilesPermission(context)
                    else { list.forEach { vaultManager.encryptInPlace(it.path, VaultMediaType.AUDIO) }; refreshAll() }
                }
            )
            2 -> VaultScreen(
                vaultManager = vaultManager,
                allVideos = videos,
                allAudios = audios,
                allImages = images,
                onRefreshMedia = { refreshAll() },
                onPlayVaultVideo = { item ->
                    musicPlayer.pause()
                    videoPlaylist = listOf(Uri.fromFile(File(item.encryptedPath)) to item.originalName)
                    videoIndex = 0
                    isVideoFullScreen = true
                    isVideoMiniWindow = false
                    isVideoBgAudio = false
                },
                onPlayVaultAudio = { item ->
                    val audio = LocalAudio(
                        id = item.id.hashCode().toLong(),
                        title = item.originalName,
                        artist = "私密音频",
                        album = "",
                        path = item.encryptedPath,
                        uri = Uri.fromFile(File(item.encryptedPath)),
                        albumArtUri = null,
                        durationMs = 0L
                    )
                    activeTrack = audio
                    musicPlayer.setMediaItem(MediaItem.fromUri(audio.uri))
                    musicPlayer.prepare()
                    musicPlayer.play()
                    showFullMusicSheet = true
                }
            )
            else -> SettingsScreen(
                themeState = themeState,
                onThemeChange = onThemeChange,
                vaultManager = vaultManager,
                onRefreshMedia = { refreshAll() }
            )
        }

        // 顶部搜索浮条
        if (showSearchBar) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .fillMaxWidth()
                    .arashiSurface(RoundedCornerShape(24.dp))
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = tokens.textSecondary)
                Spacer(Modifier.width(8.dp))
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("搜索") },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { searchQuery = ""; showSearchBar = false }) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = tokens.textSecondary)
                }
            }
        }

        // 可拖动 & 右下角可自由缩放大小的悬浮液态玻璃小窗
        if (isVideoMiniWindow && videoIndex in videoPlaylist.indices) {
            val miniHeightDp = miniWidthDp * 9f / 16f
            Box(
                modifier = Modifier
                    .offset { IntOffset(miniOffsetX.roundToInt(), miniOffsetY.roundToInt()) }
                    .width(miniWidthDp.dp)
                    .height(miniHeightDp.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.2.dp, Color.White.copy(alpha = 0.65f), RoundedCornerShape(20.dp))
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            miniOffsetX += dragAmount.x
                            miniOffsetY += dragAmount.y
                        }
                    }
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = videoPlayer
                            useController = false
                        }
                    }
                )
                // 右上角全屏与关闭按钮
                Row(
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = { isVideoMiniWindow = false; isVideoFullScreen = true },
                        modifier = Modifier.size(26.dp).visionGlassCapsule(CircleShape)
                    ) {
                        Icon(Icons.Default.OpenInFull, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                    IconButton(
                        onClick = { videoPlayer.pause(); isVideoMiniWindow = false },
                        modifier = Modifier.size(26.dp).visionGlassCapsule(CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
                // 中央播放/暂停
                IconButton(
                    onClick = { if (videoPlayer.isPlaying) videoPlayer.pause() else videoPlayer.play() },
                    modifier = Modifier.align(Alignment.Center).size(36.dp).visionGlassCapsule(CircleShape)
                ) {
                    Icon(if (isVideoPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                // 右下角拖拽缩放手柄（自由调节小窗大小）
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(28.dp)
                        .clip(RoundedCornerShape(topStart = 12.dp))
                        .background(Color(0x88000000))
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                miniWidthDp = (miniWidthDp + dragAmount.x * 0.6f).coerceIn(150f, 340f)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.ZoomOutMap, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        }

        // 底部悬浮控制条（视频后台音频条 / 音乐 Mini 条）+ 带完整图标的液态玻璃胶囊底栏
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (isVideoBgAudio && videoIndex in videoPlaylist.indices) {
                val title = videoPlaylist[videoIndex].second
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .arashiSurface(RoundedCornerShape(22.dp))
                        .clickable { isVideoBgAudio = false; isVideoFullScreen = true }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Headphones, contentDescription = null, tint = tokens.accent)
                    Spacer(Modifier.width(10.dp))
                    Text(title, color = tokens.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, modifier = Modifier.weight(1f))
                    IconButton(onClick = { if (videoPlayer.isPlaying) videoPlayer.pause() else videoPlayer.play() }) {
                        Icon(if (isVideoPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null, tint = tokens.textPrimary)
                    }
                    IconButton(onClick = { videoPlayer.pause(); isVideoBgAudio = false }) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = tokens.textSecondary)
                    }
                }
            } else {
                activeTrack?.let { track ->
                    val saved = remember(track.id, tagVersion) { tagMatcher.getSavedTag(track.id) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .arashiSurface(RoundedCornerShape(22.dp))
                            .clickable { showFullMusicSheet = true }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = saved?.coverUrl ?: track.albumArtUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp))
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "${saved?.title?.ifBlank { track.title } ?: track.title} · ${saved?.artist?.ifBlank { track.artist } ?: track.artist}",
                            color = tokens.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { if (musicPlayer.isPlaying) musicPlayer.pause() else musicPlayer.play() }) {
                            Icon(if (isMusicPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null, tint = tokens.textPrimary)
                        }
                    }
                }
            }

            // 底部液态玻璃悬浮胶囊导航栏（带完整图标 + 文字）
            val navItems = listOf(
                Triple(0, "视频", Icons.Default.PlayCircle),
                Triple(1, "音乐", Icons.Default.MusicNote),
                Triple(2, "私密", Icons.Default.Lock),
                Triple(3, "设置", Icons.Default.Settings)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .arashiSurface(RoundedCornerShape(32.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                navItems.forEach { (idx, label, icon) ->
                    val selected = currentTab == idx
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .then(if (selected) Modifier.background(tokens.accent.copy(alpha = 0.14f)) else Modifier)
                            .clickable { currentTab = idx }
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(icon, contentDescription = label, tint = if (selected) tokens.accent else tokens.textSecondary, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.height(2.dp))
                        Text(label, color = if (selected) tokens.accent else tokens.textSecondary, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
    }
}
