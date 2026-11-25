package com.arashiplayer

import android.Manifest
import android.content.Intent
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
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.Coil
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import com.arashiplayer.data.*
import com.arashiplayer.music.AppleMusicPlayerSheet
import com.arashiplayer.music.ArashiMediaNotifier
import com.arashiplayer.music.DesktopLyricsOverlayController
import com.arashiplayer.music.DesktopLyricsStore
import com.arashiplayer.music.LrcParser
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
    val recycleBinManager = remember { RecycleBinManager(context) }
    val desktopLyricsStore = remember { DesktopLyricsStore(context) }
    var desktopLyricsConfig by remember { mutableStateOf(desktopLyricsStore.load()) }
    val desktopOverlay = remember { DesktopLyricsOverlayController(context) }

    val videoPlayer = remember { ExoPlayer.Builder(context).build() }
    val musicPlayer = remember { ExoPlayer.Builder(context).build() }
    val mediaNotifier = remember { ArashiMediaNotifier(context, musicPlayer) }

    DisposableEffect(Unit) {
        onDispose {
            desktopOverlay.hide()
            mediaNotifier.release()
            videoPlayer.release()
            musicPlayer.release()
        }
    }

    var videos by remember { mutableStateOf<List<LocalVideo>>(emptyList()) }
    var audios by remember { mutableStateOf<List<LocalAudio>>(emptyList()) }
    var images by remember { mutableStateOf<List<LocalImage>>(emptyList()) }
    var currentTab by remember { mutableIntStateOf(0) } // 0=视频 1=音乐 2=喜欢 3=设置
    var showHiddenVault by remember { mutableStateOf(false) }
    var tagVersion by remember { mutableIntStateOf(0) }
    var favVersion by remember { mutableIntStateOf(0) }
    var showSearchBar by remember { mutableStateOf(false) }
    var showWelcomeDialog by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }

    var videoPlaylist by remember { mutableStateOf<List<Pair<Uri, String>>>(emptyList()) }
    var videoIndex by remember { mutableIntStateOf(-1) }
    var isVideoFullScreen by remember { mutableStateOf(false) }
    var isVideoMiniWindow by remember { mutableStateOf(false) }
    var isVideoBgAudio by remember { mutableStateOf(false) }
    var isVideoPlaying by remember { mutableStateOf(false) }

    var miniOffsetX by remember { mutableFloatStateOf(360f) }
    var miniOffsetY by remember { mutableFloatStateOf(860f) }
    var miniWidthDp by remember { mutableFloatStateOf(180f) }
    var videoAspectRatio by remember { mutableFloatStateOf(16f / 9f) }
    var showMiniControls by remember { mutableStateOf(false) }
    var isIslandExpanded by remember { mutableStateOf(false) }

    DisposableEffect(videoPlayer) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    videoAspectRatio = videoSize.width.toFloat() / videoSize.height.toFloat()
                }
            }
        }
        videoPlayer.addListener(listener)
        onDispose { videoPlayer.removeListener(listener) }
    }

    fun playAudioWithQueue(target: LocalAudio) {
        videoPlayer.pause(); isVideoMiniWindow = false; isVideoBgAudio = false
        val queue = if (audios.any { it.id == target.id }) audios else listOf(target)
        val startIdx = queue.indexOfFirst { it.id == target.id }.coerceAtLeast(0)
        val mediaItems = queue.map { a ->
            val saved = tagMatcher.getSavedTag(a.id)
            MediaItem.Builder()
                .setUri(a.uri)
                .setMediaId(a.id.toString())
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(saved?.title?.ifBlank { a.title } ?: a.title)
                        .setArtist(saved?.artist?.ifBlank { a.artist } ?: a.artist)
                        .setAlbumTitle(saved?.album?.ifBlank { a.album } ?: a.album)
                        .setArtworkUri(saved?.coverUrl?.let { Uri.parse(it) } ?: a.albumArtUri)
                        .build()
                )
                .build()
        }
        activeTrack = target
        musicPlayer.setMediaItems(mediaItems, startIdx, 0L)
        musicPlayer.prepare()
        musicPlayer.play()
    }

    DisposableEffect(musicPlayer, audios) {
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val id = mediaItem?.mediaId?.toLongOrNull() ?: return
                audios.firstOrNull { it.id == id }?.let { activeTrack = it }
            }
        }
        musicPlayer.addListener(listener)
        onDispose { musicPlayer.removeListener(listener) }
    }

    LaunchedEffect(showMiniControls, isVideoMiniWindow) {
        if (showMiniControls && isVideoMiniWindow) {
            delay(3000)
            showMiniControls = false
        }
    }

    var activeTrack by remember { mutableStateOf<LocalAudio?>(null) }
    var isMusicPlaying by remember { mutableStateOf(false) }
    var musicPlayMode by remember { mutableStateOf(MusicPlayMode.ORDER) }
    var musicPosMs by remember { mutableLongStateOf(0L) }
    var musicDurMs by remember { mutableLongStateOf(0L) }
    var showFullMusicSheet by remember { mutableStateOf(false) }

    var lastBackPressMs by remember { mutableLongStateOf(0L) }

    fun refreshAll() {
        scope.launch {
            videos = repo.loadLocalVideos()
            audios = repo.loadLocalAudios()
            images = repo.loadLocalImages()
        }
    }

    LaunchedEffect(musicPlayMode) {
        musicPlayer.repeatMode = if (musicPlayMode == MusicPlayMode.SINGLE_LOOP) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    val permissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.POST_NOTIFICATIONS
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refreshAll() }

    LaunchedEffect(Unit) { permLauncher.launch(permissions); refreshAll() }

    LaunchedEffect(Unit) {
        while (true) {
            isMusicPlaying = musicPlayer.isPlaying
            musicPosMs = musicPlayer.currentPosition.coerceAtLeast(0L)
            musicDurMs = musicPlayer.duration.coerceAtLeast(0L)
            isVideoPlaying = videoPlayer.isPlaying

            val curTrack = activeTrack
            if (curTrack != null) {
                val saved = tagMatcher.getSavedTag(curTrack.id)
                val title = saved?.title?.ifBlank { curTrack.title } ?: curTrack.title
                val artist = saved?.artist?.ifBlank { curTrack.artist } ?: curTrack.artist
                mediaNotifier.updatePlaybackNotification(title, artist, isMusicPlaying)
                val lines = LrcParser.parseMultilingual(saved?.syncedLyrics, saved?.translatedLyrics, saved?.phoneticLyrics)
                val line = lines.getOrNull(lines.indexOfLast { it.timestampMs <= musicPosMs }.coerceAtLeast(0))
                desktopOverlay.update(desktopLyricsConfig, line?.text, line?.translation)
            } else {
                desktopOverlay.hide()
                if (isVideoBgAudio && videoIndex in videoPlaylist.indices) {
                    mediaNotifier.updatePlaybackNotification(videoPlaylist[videoIndex].second, "后台音频播放", isVideoPlaying)
                } else {
                    mediaNotifier.clear()
                }
            }
            delay(300)
        }
    }

    if (showSearchBar) {
        BackHandler { searchQuery = ""; showSearchBar = false }
    } else if (!isVideoFullScreen && !showFullMusicSheet && !showHiddenVault) {
        BackHandler {
            if (currentTab != 0) {
                currentTab = 0
            } else {
                val now = System.currentTimeMillis()
                if (now - lastBackPressMs <= 2000L) onExitApp()
                else {
                    lastBackPressMs = now
                    Toast.makeText(context, "再按一次退出岚播放器", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

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
            onEnterMiniWindow = { isVideoFullScreen = false; isVideoMiniWindow = true; isVideoBgAudio = false },
            onBackgroundAudioMode = {
                isVideoFullScreen = false; isVideoMiniWindow = false; isVideoBgAudio = true
                Toast.makeText(context, "已切入后台音频播放", Toast.LENGTH_SHORT).show()
            },
            onBack = { videoPlayer.pause(); isVideoFullScreen = false; isVideoMiniWindow = false; isVideoBgAudio = false }
        )
        return
    }

    if (showFullMusicSheet && activeTrack != null) {
        val current = activeTrack!!
        val isFav = remember(current.path, favVersion) { favoritesStore.isFavorite(current.path) }
        AppleMusicPlayerSheet(
            track = current,
            isPlaying = isMusicPlaying,
            playMode = musicPlayMode,
            isFavorite = isFav,
            currentPositionMs = musicPosMs,
            durationMs = musicDurMs,
            onSeekTo = { musicPlayer.seekTo(it) },
            onTogglePlay = { if (musicPlayer.isPlaying) musicPlayer.pause() else musicPlayer.play() },
            onNext = {
                if (audios.isNotEmpty()) {
                    val idx = audios.indexOfFirst { it.id == current.id }
                    val next = if (musicPlayMode == MusicPlayMode.SHUFFLE) audios.random() else audios[(idx + 1) % audios.size]
                    playAudioWithQueue(next)
                }
            },
            onPrev = {
                if (audios.isNotEmpty()) {
                    val idx = audios.indexOfFirst { it.id == current.id }
                    val prev = audios[(idx - 1 + audios.size) % audios.size]
                    playAudioWithQueue(prev)
                }
            },
            onCyclePlayMode = {
                musicPlayMode = when (musicPlayMode) {
                    MusicPlayMode.ORDER -> MusicPlayMode.SINGLE_LOOP
                    MusicPlayMode.SINGLE_LOOP -> MusicPlayMode.SHUFFLE
                    MusicPlayMode.SHUFFLE -> MusicPlayMode.ORDER
                }
            },
            onToggleFavorite = { favoritesStore.toggleFavorite(current.path); favVersion++ },
            lyricTextColor = Color(desktopLyricsConfig.textColorArgb),
            lyricHighlightColor = Color(desktopLyricsConfig.highlightColorArgb),
            onTagSaved = { tagVersion++ },
            onClose = { showFullMusicSheet = false }
        )
        return
    }

    if (showHiddenVault) {
        ArashiBackdrop {
            VaultScreen(
                vaultManager = vaultManager,
                allVideos = videos,
                allAudios = audios,
                allImages = images,
                onRefreshMedia = { refreshAll() },
                onPlayVaultVideo = { item ->
                    musicPlayer.pause()
                    videoPlaylist = listOf(Uri.fromFile(File(item.encryptedPath)) to item.originalName)
                    videoIndex = 0
                    isVideoFullScreen = true; isVideoMiniWindow = false; isVideoBgAudio = false
                },
                onPlayVaultAudio = { item ->
                    val audio = LocalAudio(item.id.hashCode().toLong(), item.originalName, "隐私音频", "", item.encryptedPath, Uri.fromFile(File(item.encryptedPath)), null, 0L)
                    activeTrack = audio
                    musicPlayer.setMediaItem(MediaItem.fromUri(audio.uri))
                    musicPlayer.prepare(); musicPlayer.play()
                    showFullMusicSheet = true
                },
                onBack = { showHiddenVault = false }
            )
        }
        return
    }

    ArashiBackdrop {
        when (currentTab) {
            0 -> VideoLibraryScreen(
                videos = videos,
                searchQuery = searchQuery,
                favoritesStore = favoritesStore,
                favVersion = favVersion,
                onFavoriteChanged = { favVersion++ },
                onToggleSearch = { showSearchBar = !showSearchBar },
                onRefresh = { refreshAll(); Toast.makeText(context, "已刷新视频列表", Toast.LENGTH_SHORT).show() },
                onVideoClick = { clicked, list ->
                    musicPlayer.pause()
                    videoPlaylist = list.map { it.uri to it.title }
                    videoIndex = list.indexOfFirst { it.id == clicked.id }.coerceAtLeast(0)
                    isVideoFullScreen = true; isVideoMiniWindow = false; isVideoBgAudio = false
                },
                onRenameVideo = { v, newName ->
                    if (!vaultManager.hasAllFilesAccess()) openAllFilesPermission(context)
                    else { repo.renameMediaFile(v.path, newName); refreshAll() }
                },
                onDeleteVideos = { list ->
                    if (!vaultManager.hasAllFilesAccess()) openAllFilesPermission(context)
                    else {
                        var moved = 0
                        list.forEach { v ->
                            if (recycleBinManager.moveToTrash(v.path, v.uri, v.title, TrashMediaType.VIDEO, v.durationMs, v.sizeBytes).isSuccess) moved++
                        }
                        refreshAll()
                        if (moved > 0) Toast.makeText(context, "已移入回收站（${moved}项），可在设置中恢复", Toast.LENGTH_SHORT).show()
                    }
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
                favVersion = favVersion,
                onFavoriteChanged = { favVersion++ },
                onToggleSearch = { showSearchBar = !showSearchBar },
                onRefresh = { refreshAll(); Toast.makeText(context, "已刷新音乐列表", Toast.LENGTH_SHORT).show() },
                onAudioClick = { audio ->
                    playAudioWithQueue(audio)
                    showFullMusicSheet = true
                },
                onRenameAudio = { a, newName ->
                    if (!vaultManager.hasAllFilesAccess()) openAllFilesPermission(context)
                    else { repo.renameMediaFile(a.path, newName); refreshAll() }
                },
                onDeleteAudios = { list ->
                    if (!vaultManager.hasAllFilesAccess()) openAllFilesPermission(context)
                    else {
                        var moved = 0
                        list.forEach { a ->
                            if (recycleBinManager.moveToTrash(a.path, a.uri, a.title, TrashMediaType.AUDIO, a.durationMs, java.io.File(a.path).length()).isSuccess) moved++
                        }
                        refreshAll()
                        if (moved > 0) Toast.makeText(context, "已移入回收站（${moved}项），可在设置中恢复", Toast.LENGTH_SHORT).show()
                    }
                },
                onEncryptAudios = { list ->
                    if (!vaultManager.hasAllFilesAccess()) openAllFilesPermission(context)
                    else { list.forEach { vaultManager.encryptInPlace(it.path, VaultMediaType.AUDIO) }; refreshAll() }
                }
            )
            2 -> FavoritesScreen(
                videos = videos,
                audios = audios,
                favoritesStore = favoritesStore,
                favVersion = favVersion,
                tagVersion = tagVersion,
                onToggleFav = { path -> favoritesStore.toggleFavorite(path); favVersion++ },
                onVideoClick = { clicked, list ->
                    musicPlayer.pause()
                    videoPlaylist = list.map { it.uri to it.title }
                    videoIndex = list.indexOfFirst { it.id == clicked.id }.coerceAtLeast(0)
                    isVideoFullScreen = true; isVideoMiniWindow = false; isVideoBgAudio = false
                },
                onAudioClick = { audio ->
                    playAudioWithQueue(audio)
                    showFullMusicSheet = true
                }
            )
            else -> SettingsScreen(
                themeState = themeState,
                onThemeChange = onThemeChange,
                vaultManager = vaultManager,
                recycleBinManager = recycleBinManager,
                desktopLyricsConfig = desktopLyricsConfig,
                onDesktopLyricsChange = {
                    desktopLyricsConfig = it
                    desktopLyricsStore.save(it)
                    if (!it.enabled) desktopOverlay.hide()
                },
                onRefreshMedia = { refreshAll() },
                onOpenHiddenVault = { showHiddenVault = true }
            )
        }

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

        // 顶部灵动岛胶囊（播放时登岛，点击展开控制卡片）
        if ((activeTrack != null || isVideoBgAudio) && !showSearchBar) {
            val islandTitle = if (isVideoBgAudio && videoIndex in videoPlaylist.indices) {
                videoPlaylist[videoIndex].second
            } else {
                val t = activeTrack!!
                val s = remember(t.id, tagVersion) { tagMatcher.getSavedTag(t.id) }
                s?.title?.ifBlank { t.title } ?: t.title
            }
            val islandArtist = if (isVideoBgAudio) "后台音频" else {
                val t = activeTrack!!
                val s = remember(t.id, tagVersion) { tagMatcher.getSavedTag(t.id) }
                s?.artist?.ifBlank { t.artist } ?: t.artist
            }
            val islandCover: Any? = if (isVideoBgAudio) null else {
                val t = activeTrack!!
                val s = remember(t.id, tagVersion) { tagMatcher.getSavedTag(t.id) }
                s?.coverUrl ?: t.albumArtUri
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 6.dp)
            ) {
                if (!isIslandExpanded) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(22.dp))
                            .background(Color(0xE81C1F26))
                            .border(0.8.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(22.dp))
                            .clickable { isIslandExpanded = true }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (islandCover != null) {
                            AsyncImage(
                                model = islandCover,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(22.dp).clip(CircleShape)
                            )
                        } else {
                            Icon(Icons.Default.GraphicEq, contentDescription = null, tint = tokens.accent, modifier = Modifier.size(18.dp))
                        }
                        Text(islandTitle, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, modifier = Modifier.widthIn(max = 140.dp))
                        Icon(
                            if ((isVideoBgAudio && isVideoPlaying) || (!isVideoBgAudio && isMusicPlaying)) Icons.Default.GraphicEq else Icons.Default.Pause,
                            contentDescription = null,
                            tint = tokens.accent,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .width(320.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .background(Color(0xF01C1F26))
                            .border(0.9.dp, Color.White.copy(alpha = 0.24f), RoundedCornerShape(26.dp))
                            .clickable { isIslandExpanded = false }
                            .padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF2B3140))
                                    .clickable {
                                        isIslandExpanded = false
                                        if (isVideoBgAudio) { isVideoBgAudio = false; isVideoFullScreen = true }
                                        else showFullMusicSheet = true
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (islandCover != null) {
                                    AsyncImage(model = islandCover, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                } else {
                                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White)
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(islandTitle, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                Spacer(Modifier.height(2.dp))
                                Text(islandArtist, color = Color.White.copy(alpha = 0.68f), fontSize = 12.sp, maxLines = 1)
                            }
                            IconButton(onClick = { isIslandExpanded = false }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = null, tint = Color.White.copy(alpha = 0.75f))
                            }
                        }
                        if (!isVideoBgAudio && musicDurMs > 0L) {
                            Spacer(Modifier.height(10.dp))
                            LinearProgressIndicator(
                                progress = { (musicPosMs.toFloat() / musicDurMs.toFloat()).coerceIn(0f, 1f) },
                                color = Color.White,
                                trackColor = Color.White.copy(alpha = 0.22f),
                                modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp))
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(formatDuration(musicPosMs), color = Color.White.copy(alpha = 0.65f), fontSize = 10.sp)
                                Text(formatDuration(musicDurMs), color = Color.White.copy(alpha = 0.65f), fontSize = 10.sp)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = {
                                if (!isVideoBgAudio && audios.isNotEmpty() && activeTrack != null) {
                                    val idx = audios.indexOfFirst { it.id == activeTrack!!.id }
                                    val prev = audios[(idx - 1 + audios.size) % audios.size]
                                    activeTrack = prev
                                    musicPlayer.setMediaItem(MediaItem.fromUri(prev.uri))
                                    musicPlayer.prepare(); musicPlayer.play()
                                }
                            }, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.Default.SkipPrevious, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                            }
                            IconButton(onClick = {
                                if (isVideoBgAudio) { if (videoPlayer.isPlaying) videoPlayer.pause() else videoPlayer.play() }
                                else { if (musicPlayer.isPlaying) musicPlayer.pause() else musicPlayer.play() }
                            }, modifier = Modifier.size(38.dp)) {
                                val playing = if (isVideoBgAudio) isVideoPlaying else isMusicPlaying
                                Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                            }
                            IconButton(onClick = {
                                if (!isVideoBgAudio && audios.isNotEmpty() && activeTrack != null) {
                                    val idx = audios.indexOfFirst { it.id == activeTrack!!.id }
                                    val next = if (musicPlayMode == MusicPlayMode.SHUFFLE) audios.random() else audios[(idx + 1) % audios.size]
                                    activeTrack = next
                                    musicPlayer.setMediaItem(MediaItem.fromUri(next.uri))
                                    musicPlayer.prepare(); musicPlayer.play()
                                }
                            }, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.Default.SkipNext, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
        }

        // QQ影音风格自适应小窗（竖屏竖着无黑边；轻点才显隐小巧按钮）
        if (isVideoMiniWindow && videoIndex in videoPlaylist.indices) {
            val isPortrait = videoAspectRatio < 1f
            val resolvedWidth = if (isPortrait) miniWidthDp.coerceIn(118f, 210f) else miniWidthDp.coerceIn(170f, 340f)
            val resolvedHeight = (resolvedWidth / videoAspectRatio).coerceIn(110f, 390f)
            Box(
                modifier = Modifier
                    .offset { IntOffset(miniOffsetX.roundToInt(), miniOffsetY.roundToInt()) }
                    .width(resolvedWidth.dp)
                    .height(resolvedHeight.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { showMiniControls = !showMiniControls })
                    }
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume(); miniOffsetX += dragAmount.x; miniOffsetY += dragAmount.y
                        }
                    }
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = videoPlayer
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        }
                    }
                )
                if (showMiniControls) {
                    IconButton(
                        onClick = { videoPlayer.pause(); isVideoMiniWindow = false; showMiniControls = false },
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp).visionGlassCapsule(CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                    }
                    IconButton(
                        onClick = { isVideoMiniWindow = false; isVideoFullScreen = true; showMiniControls = false },
                        modifier = Modifier.align(Alignment.Center).size(30.dp).visionGlassCapsule(CircleShape)
                    ) {
                        Icon(Icons.Default.Fullscreen, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp)
                            .visionGlassCapsule(RoundedCornerShape(16.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { videoPlayer.seekTo((videoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)) },
                            modifier = Modifier.size(22.dp)
                        ) {
                            Icon(Icons.Default.FastRewind, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                        IconButton(
                            onClick = { if (videoPlayer.isPlaying) videoPlayer.pause() else videoPlayer.play() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(if (isVideoPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                        IconButton(
                            onClick = { videoPlayer.seekTo(videoPlayer.currentPosition + 10_000L) },
                            modifier = Modifier.size(22.dp)
                        ) {
                            Icon(Icons.Default.FastForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(20.dp)
                            .clip(RoundedCornerShape(topStart = 8.dp))
                            .background(Color(0x66000000))
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    val minW = if (videoAspectRatio < 1f) 118f else 170f
                                    val maxW = if (videoAspectRatio < 1f) 210f else 340f
                                    miniWidthDp = (miniWidthDp + dragAmount.x * 0.6f).coerceIn(minW, maxW)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.ZoomOutMap, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
                    }
                }
            }
        }

        // 底部悬浮栏 + 4 图标液态玻璃底栏（视频 / 音乐 / 喜欢 / 设置）
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (isVideoBgAudio && videoIndex in videoPlaylist.indices) {
                val title = videoPlaylist[videoIndex].second
                Row(
                    modifier = Modifier.fillMaxWidth().arashiSurface(RoundedCornerShape(22.dp))
                        .clickable { isVideoBgAudio = false; isVideoFullScreen = true }.padding(horizontal = 14.dp, vertical = 8.dp),
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
                        modifier = Modifier.fillMaxWidth().arashiSurface(RoundedCornerShape(22.dp))
                            .clickable { showFullMusicSheet = true }.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(model = saved?.coverUrl ?: track.albumArtUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)))
                        Spacer(Modifier.width(10.dp))
                        Text("${saved?.title?.ifBlank { track.title } ?: track.title} · ${saved?.artist?.ifBlank { track.artist } ?: track.artist}", color = tokens.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, modifier = Modifier.weight(1f))
                        IconButton(onClick = { if (musicPlayer.isPlaying) musicPlayer.pause() else musicPlayer.play() }) {
                            Icon(if (isMusicPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null, tint = tokens.textPrimary)
                        }
                    }
                }
            }

            val navItems = listOf(
                Triple(0, "视频", Icons.Default.PlayCircle),
                Triple(1, "音乐", Icons.Default.MusicNote),
                Triple(2, "喜欢", Icons.Default.FavoriteBorder),
                Triple(3, "设置", Icons.Default.Settings)
            )
            Row(
                modifier = Modifier.fillMaxWidth().arashiSurface(RoundedCornerShape(32.dp)).padding(horizontal = 8.dp, vertical = 6.dp),
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

        if (showWelcomeDialog) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x77000000))
                    .clickable { showWelcomeDialog = false },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp)
                        .arashiSurface(RoundedCornerShape(28.dp), elevation = 10.dp)
                        .clickable(enabled = false) {}
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(tokens.accent.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Group, contentDescription = null, tint = tokens.accent, modifier = Modifier.size(28.dp))
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("交流群", color = tokens.textPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "欢迎加入交流群获取版本更新，提出宝贵意见！",
                        color = tokens.textSecondary,
                        fontSize = 14.sp,
                        lineHeight = 21.sp
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(18.dp))
                                .background(tokens.textPrimary.copy(alpha = 0.08f))
                                .clickable { showWelcomeDialog = false }
                                .padding(vertical = 11.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("取消", color = tokens.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(18.dp))
                                .background(tokens.accent)
                                .clickable {
                                    showWelcomeDialog = false
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse("https://qm.qq.com/q/GtxBLm2e8E"))
                                        )
                                    }
                                }
                                .padding(vertical = 11.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("确认", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
