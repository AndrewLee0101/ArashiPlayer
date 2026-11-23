package com.arashiplayer

import android.Manifest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import com.arashiplayer.data.LocalAudio
import com.arashiplayer.data.LocalVideo
import com.arashiplayer.data.MediaStoreRepository
import com.arashiplayer.music.AppleMusicPlayerSheet
import com.arashiplayer.ui.screens.HomeScreen
import com.arashiplayer.ui.screens.VaultScreen
import com.arashiplayer.ui.theme.*
import com.arashiplayer.vault.PrivacyVaultManager
import com.arashiplayer.video.VideoPlayerScreen
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
    val repo = remember { MediaStoreRepository(context) }
    val vaultManager = remember { PrivacyVaultManager(context) }
    val musicPlayer = remember { ExoPlayer.Builder(context).build() }

    DisposableEffect(musicPlayer) {
        onDispose { musicPlayer.release() }
    }

    var videos by remember { mutableStateOf<List<LocalVideo>>(emptyList()) }
    var audios by remember { mutableStateOf<List<LocalAudio>>(emptyList()) }
    var currentTab by remember { mutableIntStateOf(0) }

    var activeVideo by remember { mutableStateOf<Pair<Uri, String>?>(null) }
    var activeTrack by remember { mutableStateOf<LocalAudio?>(null) }
    var isMusicPlaying by remember { mutableStateOf(false) }
    var showFullMusicSheet by remember { mutableStateOf(false) }

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
    ) { }

    LaunchedEffect(Unit) {
        permLauncher.launch(permissions)
        videos = repo.loadLocalVideos()
        audios = repo.loadLocalAudios()
    }

    // 全屏视频播放页
    activeVideo?.let { (uri, title) ->
        VideoPlayerScreen(
            videoUri = uri,
            videoTitle = title,
            onBack = { activeVideo = null }
        )
        return
    }

    // 苹果风全屏音乐播放页
    if (showFullMusicSheet && activeTrack != null) {
        val current = activeTrack!!
        AppleMusicPlayerSheet(
            track = current,
            isPlaying = isMusicPlaying,
            onTogglePlay = {
                if (musicPlayer.isPlaying) {
                    musicPlayer.pause()
                    isMusicPlaying = false
                } else {
                    musicPlayer.play()
                    isMusicPlaying = true
                }
            },
            onNext = {
                val idx = audios.indexOfFirst { it.id == current.id }
                if (audios.isNotEmpty()) {
                    val next = audios[(idx + 1) % audios.size]
                    activeTrack = next
                    musicPlayer.setMediaItem(MediaItem.fromUri(next.uri))
                    musicPlayer.prepare()
                    musicPlayer.play()
                    isMusicPlaying = true
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
                    isMusicPlaying = true
                }
            },
            onClose = { showFullMusicSheet = false }
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize().background(ObsidianBg)) {
        when (currentTab) {
            0, 1 -> HomeScreen(
                videos = videos,
                audios = audios,
                onVideoClick = { activeVideo = it.uri to it.title },
                onAudioClick = { audio ->
                    activeTrack = audio
                    musicPlayer.setMediaItem(MediaItem.fromUri(audio.uri))
                    musicPlayer.prepare()
                    musicPlayer.play()
                    isMusicPlaying = true
                    showFullMusicSheet = true
                },
                onOpenVault = { currentTab = 2 }
            )
            2 -> VaultScreen(
                vaultManager = vaultManager,
                onPlayVaultVideo = { item ->
                    activeVideo = Uri.fromFile(File(item.encryptedPath)) to item.originalName
                }
            )
            else -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("岚播放器 v1.0 · com.arashiplayer", color = TextSecondary)
            }
        }

        // 底部悬浮苹果风磨砂 Mini 音乐条 + 底部导航栏
        Column(modifier = Modifier.align(Alignment.BottomCenter)) {
            activeTrack?.let { track ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(SurfaceGlass)
                        .clickable { showFullMusicSheet = true }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = track.albumArtUri,
                        contentDescription = track.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceCard)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(track.title, color = TextPrimary, fontSize = 14.sp, maxLines = 1)
                        Text(track.artist, color = TextSecondary, fontSize = 12.sp, maxLines = 1)
                    }
                    IconButton(onClick = {
                        if (musicPlayer.isPlaying) {
                            musicPlayer.pause()
                            isMusicPlaying = false
                        } else {
                            musicPlayer.play()
                            isMusicPlaying = true
                        }
                    }) {
                        Icon(
                            imageVector = if (isMusicPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "播放/暂停",
                            tint = Color.White
                        )
                    }
                }
            }

            NavigationBar(containerColor = Color(0xE6121520)) {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    icon = { Icon(Icons.Default.PlayCircle, contentDescription = "视频") },
                    label = { Text("视频") }
                )
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    icon = { Icon(Icons.Default.MusicNote, contentDescription = "音乐") },
                    label = { Text("音乐") }
                )
                NavigationBarItem(
                    selected = currentTab == 2,
                    onClick = { currentTab = 2 },
                    icon = { Icon(Icons.Default.Lock, contentDescription = "私密") },
                    label = { Text("私密") }
                )
                NavigationBarItem(
                    selected = currentTab == 3,
                    onClick = { currentTab = 3 },
                    icon = { Icon(Icons.Default.Person, contentDescription = "我的") },
                    label = { Text("我的") }
                )
            }
        }
    }
}
