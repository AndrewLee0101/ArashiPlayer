package com.arashiplayer

import android.Manifest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import coil.Coil
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import com.arashiplayer.data.*
import com.arashiplayer.music.AppleMusicPlayerSheet
import com.arashiplayer.music.OnlineTagMatcher
import com.arashiplayer.ui.screens.MusicLibraryScreen
import com.arashiplayer.ui.screens.VaultScreen
import com.arashiplayer.ui.screens.VideoLibraryScreen
import com.arashiplayer.ui.screens.openAllFilesPermission
import com.arashiplayer.ui.theme.*
import com.arashiplayer.vault.PrivacyVaultManager
import com.arashiplayer.vault.VaultMediaType
import com.arashiplayer.video.VideoPlayerScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 注册 VideoFrameDecoder，确保所有本地视频显示高清缩略图
        val imageLoader = ImageLoader.Builder(this)
            .components { add(VideoFrameDecoder.Factory()) }
            .crossfade(true)
            .build()
        Coil.setImageLoader(imageLoader)

        setContent {
            ArashiPlayerTheme {
                ArashiPlayerApp()
            }
        }
    }
}

@Composable
fun ArashiPlayerApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { MediaStoreRepository(context) }
    val vaultManager = remember { PrivacyVaultManager(context) }
    val tagMatcher = remember { OnlineTagMatcher(context) }
    val musicPlayer = remember { ExoPlayer.Builder(context).build() }

    DisposableEffect(musicPlayer) {
        onDispose { musicPlayer.release() }
    }

    var videos by remember { mutableStateOf<List<LocalVideo>>(emptyList()) }
    var audios by remember { mutableStateOf<List<LocalAudio>>(emptyList()) }
    var images by remember { mutableStateOf<List<LocalImage>>(emptyList()) }
    var currentTab by remember { mutableIntStateOf(0) }
    var tagVersion by remember { mutableIntStateOf(0) }

    var activeVideo by remember { mutableStateOf<Pair<Uri, String>?>(null) }
    var activeTrack by remember { mutableStateOf<LocalAudio?>(null) }
    var isMusicPlaying by remember { mutableStateOf(false) }
    var musicPosMs by remember { mutableLongStateOf(0L) }
    var musicDurMs by remember { mutableLongStateOf(0L) }
    var showFullMusicSheet by remember { mutableStateOf(false) }

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

    LaunchedEffect(musicPlayer) {
        while (true) {
            isMusicPlaying = musicPlayer.isPlaying
            musicPosMs = musicPlayer.currentPosition.coerceAtLeast(0L)
            musicDurMs = musicPlayer.duration.coerceAtLeast(0L)
            delay(300)
        }
    }

    activeVideo?.let { (uri, title) ->
        VideoPlayerScreen(
            videoUri = uri,
            videoTitle = title,
            onBack = { activeVideo = null }
        )
        return
    }

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

    LiquidGlassBackdrop {
        when (currentTab) {
            0 -> VideoLibraryScreen(
                videos = videos,
                onVideoClick = { activeVideo = it.uri to it.title },
                onEncryptVideo = { v ->
                    if (!vaultManager.hasAllFilesAccess()) {
                        openAllFilesPermission(context)
                    } else {
                        vaultManager.encryptInPlace(v.path, VaultMediaType.VIDEO)
                        refreshAll()
                    }
                },
                onOpenVault = { currentTab = 2 }
            )
            1 -> MusicLibraryScreen(
                audios = audios,
                tagVersion = tagVersion,
                onAudioClick = { audio ->
                    activeTrack = audio
                    musicPlayer.setMediaItem(MediaItem.fromUri(audio.uri))
                    musicPlayer.prepare()
                    musicPlayer.play()
                    showFullMusicSheet = true
                },
                onEncryptAudio = { a ->
                    if (!vaultManager.hasAllFilesAccess()) {
                        openAllFilesPermission(context)
                    } else {
                        vaultManager.encryptInPlace(a.path, VaultMediaType.AUDIO)
                        refreshAll()
                    }
                }
            )
            2 -> VaultScreen(
                vaultManager = vaultManager,
                allVideos = videos,
                allAudios = audios,
                allImages = images,
                onRefreshMedia = { refreshAll() },
                onPlayVaultVideo = { item ->
                    activeVideo = Uri.fromFile(File(item.encryptedPath)) to item.originalName
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
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("我的", color = TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlass(RoundedCornerShape(22.dp))
                        .clickable { openAllFilesPermission(context) }
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, tint = GlassCyan)
                    Spacer(Modifier.width(12.dp))
                    Text("所有文件访问权限", color = TextPrimary, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Text(
                        if (vaultManager.hasAllFilesAccess()) "已开启" else "去开启",
                        color = if (vaultManager.hasAllFilesAccess()) GlassCyan else GlassRose,
                        fontSize = 13.sp
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlass(RoundedCornerShape(22.dp))
                        .clickable { refreshAll() }
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = GlassViolet)
                    Spacer(Modifier.width(12.dp))
                    Text("重新扫描本地媒体", color = TextPrimary, fontWeight = FontWeight.Medium)
                }
            }
        }

        // 底部液态玻璃悬浮 Mini 音乐条 + 悬浮胶囊导航栏
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            activeTrack?.let { track ->
                val saved = remember(track.id, tagVersion) { tagMatcher.getSavedTag(track.id) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlass(RoundedCornerShape(24.dp), alphaTop = 0.22f, alphaBottom = 0.10f)
                        .clickable { showFullMusicSheet = true }
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = saved?.coverUrl ?: track.albumArtUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(42.dp).clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(saved?.title?.ifBlank { track.title } ?: track.title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(saved?.artist?.ifBlank { track.artist } ?: track.artist, color = TextSecondary, fontSize = 12.sp, maxLines = 1)
                    }
                    IconButton(onClick = { if (musicPlayer.isPlaying) musicPlayer.pause() else musicPlayer.play() }) {
                        Icon(
                            if (isMusicPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }
                }
            }

            // 液态玻璃悬浮胶囊底栏
            val navItems = listOf(
                Triple(0, "视频", Icons.Default.PlayCircle),
                Triple(1, "音乐", Icons.Default.MusicNote),
                Triple(2, "私密", Icons.Default.Lock),
                Triple(3, "我的", Icons.Default.Person)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(RoundedCornerShape(30.dp), alphaTop = 0.22f, alphaBottom = 0.09f)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                navItems.forEach { (idx, label, icon) ->
                    val selected = currentTab == idx
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(22.dp))
                            .then(if (selected) Modifier.liquidGlass(RoundedCornerShape(22.dp), alphaTop = 0.24f, alphaBottom = 0.10f, elevation = 0.dp) else Modifier)
                            .clickable { currentTab = idx }
                            .padding(horizontal = 18.dp, vertical = 7.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(icon, contentDescription = null, tint = if (selected) GlassCyan else TextSecondary, modifier = Modifier.size(22.dp))
                        Text(label, color = if (selected) GlassCyan else TextSecondary, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
    }
}
