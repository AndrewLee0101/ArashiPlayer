package com.arashiplayer.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.arashiplayer.data.LocalAudio
import com.arashiplayer.data.LocalVideo
import com.arashiplayer.data.formatDuration
import com.arashiplayer.data.formatFileSize
import com.arashiplayer.music.OnlineTagMatcher
import com.arashiplayer.ui.theme.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoLibraryScreen(
    videos: List<LocalVideo>,
    onVideoClick: (LocalVideo) -> Unit,
    onEncryptVideo: (LocalVideo) -> Unit,
    onOpenVault: () -> Unit
) {
    var selectedForAction by remember { mutableStateOf<LocalVideo?>(null) }

    Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 160.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "岚播放器",
                        color = TextPrimary,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onOpenVault,
                        modifier = Modifier.liquidGlass(CircleShape)
                    ) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = GlassCyan)
                    }
                }
            }

            if (videos.isNotEmpty()) {
                val hero = videos.first()
                item {
                    Text(
                        text = "继续播放",
                        color = TextPrimary,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .height(200.dp)
                            .liquidGlass(RoundedCornerShape(26.dp))
                            .combinedClickable(
                                onClick = { onVideoClick(hero) },
                                onLongClick = { selectedForAction = hero }
                            )
                    ) {
                        AsyncImage(
                            model = hero.uri,
                            contentDescription = hero.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC060812))))
                        )
                        Box(
                            modifier = Modifier
                                .size(58.dp)
                                .align(Alignment.Center)
                                .liquidGlass(CircleShape, alphaTop = 0.32f, alphaBottom = 0.14f),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp))
                        }
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(16.dp)
                        ) {
                            Text(hero.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text("${hero.resolutionBadge} · ${formatDuration(hero.durationMs)}", color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(22.dp))
                Text(
                    text = "本地视频",
                    color = TextPrimary,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }

            items(videos, key = { it.id }) { video ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                        .liquidGlass(RoundedCornerShape(22.dp))
                        .combinedClickable(
                            onClick = { onVideoClick(video) },
                            onLongClick = { selectedForAction = video }
                        )
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(126.dp)
                            .height(76.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF141828))
                    ) {
                        AsyncImage(
                            model = video.uri,
                            contentDescription = video.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Text(
                            text = video.resolutionBadge,
                            color = Color.Black,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(6.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White.copy(alpha = 0.88f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(video.title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 2)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${formatDuration(video.durationMs)} · ${formatFileSize(video.sizeBytes)}",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // 长按视频操作菜单（移入加密空间）
        selectedForAction?.let { video ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x88000000))
                    .clickable { selectedForAction = null },
                contentAlignment = Alignment.BottomCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .liquidGlass(RoundedCornerShape(28.dp), alphaTop = 0.25f, alphaBottom = 0.12f)
                        .clickable(enabled = false) {}
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(video.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .liquidGlass(RoundedCornerShape(18.dp))
                            .clickable {
                                onEncryptVideo(video)
                                selectedForAction = null
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = GlassCyan)
                        Spacer(Modifier.width(12.dp))
                        Text("添加到加密空间", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MusicLibraryScreen(
    audios: List<LocalAudio>,
    tagVersion: Int,
    onAudioClick: (LocalAudio) -> Unit,
    onEncryptAudio: (LocalAudio) -> Unit
) {
    val context = LocalContext.current
    val matcher = remember { OnlineTagMatcher(context) }
    var selectedAudio by remember { mutableStateOf<LocalAudio?>(null) }

    Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 160.dp)
        ) {
            item {
                Text(
                    text = "音乐",
                    color = TextPrimary,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                )
            }

            if (audios.isNotEmpty()) {
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(audios.take(10), key = { "card_${it.id}_$tagVersion" }) { audio ->
                            val saved = remember(audio.id, tagVersion) { matcher.getSavedTag(audio.id) }
                            Column(
                                modifier = Modifier
                                    .width(136.dp)
                                    .combinedClickable(
                                        onClick = { onAudioClick(audio) },
                                        onLongClick = { selectedAudio = audio }
                                    )
                            ) {
                                AsyncImage(
                                    model = saved?.coverUrl ?: audio.albumArtUri,
                                    contentDescription = audio.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(136.dp)
                                        .liquidGlass(RoundedCornerShape(22.dp))
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(saved?.title?.ifBlank { audio.title } ?: audio.title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                                Text(saved?.artist?.ifBlank { audio.artist } ?: audio.artist, color = TextSecondary, fontSize = 12.sp, maxLines = 1)
                            }
                        }
                    }
                    Spacer(Modifier.height(22.dp))
                }
            }

            items(audios, key = { "row_${it.id}_$tagVersion" }) { audio ->
                val saved = remember(audio.id, tagVersion) { matcher.getSavedTag(audio.id) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 5.dp)
                        .liquidGlass(RoundedCornerShape(20.dp))
                        .combinedClickable(
                            onClick = { onAudioClick(audio) },
                            onLongClick = { selectedAudio = audio }
                        )
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = saved?.coverUrl ?: audio.albumArtUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(50.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF1A2034))
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(saved?.title?.ifBlank { audio.title } ?: audio.title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                        Text(saved?.artist?.ifBlank { audio.artist } ?: audio.artist, color = TextSecondary, fontSize = 12.sp, maxLines = 1)
                    }
                    Text(formatDuration(audio.durationMs), color = TextSecondary, fontSize = 12.sp)
                }
            }
        }

        selectedAudio?.let { audio ->
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0x88000000)).clickable { selectedAudio = null },
                contentAlignment = Alignment.BottomCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .liquidGlass(RoundedCornerShape(28.dp), alphaTop = 0.25f, alphaBottom = 0.12f)
                        .clickable(enabled = false) {}
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(audio.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .liquidGlass(RoundedCornerShape(18.dp))
                            .clickable {
                                onEncryptAudio(audio)
                                selectedAudio = null
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = GlassCyan)
                        Spacer(Modifier.width(12.dp))
                        Text("添加到加密空间", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
