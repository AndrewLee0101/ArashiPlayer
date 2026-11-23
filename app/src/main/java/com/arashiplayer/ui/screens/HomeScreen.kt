package com.arashiplayer.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import com.arashiplayer.music.OnlineTagMatcher
import com.arashiplayer.ui.theme.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoLibraryScreen(
    videos: List<LocalVideo>,
    searchQuery: String,
    onVideoClick: (LocalVideo) -> Unit,
    onEncryptVideo: (LocalVideo) -> Unit,
    onOpenVault: () -> Unit
) {
    val tokens = LocalArashiTokens.current
    var filterIdx by remember { mutableIntStateOf(0) } // 0=全部 1=4K 2=文件夹 3=最近
    var selectedForAction by remember { mutableStateOf<LocalVideo?>(null) }

    val filteredVideos = remember(videos, filterIdx, searchQuery) {
        val base = if (searchQuery.isBlank()) videos else videos.filter { it.title.contains(searchQuery, ignoreCase = true) }
        when (filterIdx) {
            1 -> base.filter { it.height >= 2160 || it.width >= 3840 }
            2 -> base.sortedBy { it.folderName }
            3 -> base.take(12)
            else -> base
        }
    }

    Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 175.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "岚播放器",
                        color = tokens.textPrimary,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onOpenVault, modifier = Modifier.arashiSurface(CircleShape)) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = tokens.textPrimary)
                    }
                }
            }

            // 筛选胶囊
            item {
                val pills = listOf("全部", "4K", "文件夹", "最近")
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    pills.forEachIndexed { idx, label ->
                        val active = idx == filterIdx
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .then(
                                    if (active) Modifier.background(tokens.textPrimary)
                                    else Modifier.arashiSurface(RoundedCornerShape(18.dp), elevation = 2.dp)
                                )
                                .clickable { filterIdx = idx }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = label,
                                color = if (active) tokens.background else tokens.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            // Bento Hero 大卡片
            if (filteredVideos.isNotEmpty()) {
                val hero = filteredVideos.first()
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .height(210.dp)
                            .arashiSurface(RoundedCornerShape(28.dp))
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
                                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xAA000000))))
                        )
                        Box(
                            modifier = Modifier
                                .size(58.dp)
                                .align(Alignment.Center)
                                .clip(CircleShape)
                                .background(Color(0x99FFFFFF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF151822), modifier = Modifier.size(34.dp))
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(14.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x88000000))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("${hero.title} · ${hero.resolutionBadge}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            // Bento 双列卡片网格
            val rest = if (filteredVideos.size > 1) filteredVideos.drop(1) else emptyList()
            val rows = rest.chunked(2)
            items(rows) { pair ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    pair.forEach { video ->
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .arashiSurface(RoundedCornerShape(24.dp))
                                .combinedClickable(
                                    onClick = { onVideoClick(video) },
                                    onLongClick = { selectedForAction = video }
                                )
                                .padding(8.dp)
                        ) {
                            AsyncImage(
                                model = video.uri,
                                contentDescription = video.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1.05f)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(Color(0xFF1A202E))
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = video.title,
                                    color = tokens.textPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = formatDuration(video.durationMs),
                                    color = tokens.textSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        // 长按视频操作弹窗
        selectedForAction?.let { video ->
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0x88000000)).clickable { selectedForAction = null },
                contentAlignment = Alignment.BottomCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .arashiSurface(RoundedCornerShape(28.dp))
                        .clickable(enabled = false) {}
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(video.title, color = tokens.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .arashiSurface(RoundedCornerShape(18.dp), elevation = 2.dp)
                            .clickable { onEncryptVideo(video); selectedForAction = null }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = tokens.accent)
                        Spacer(Modifier.width(12.dp))
                        Text("添加到加密空间", color = tokens.textPrimary, fontWeight = FontWeight.SemiBold)
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
    searchQuery: String,
    tagVersion: Int,
    onAudioClick: (LocalAudio) -> Unit,
    onEncryptAudio: (LocalAudio) -> Unit
) {
    val context = LocalContext.current
    val tokens = LocalArashiTokens.current
    val matcher = remember { OnlineTagMatcher(context) }
    var selectedAudio by remember { mutableStateOf<LocalAudio?>(null) }

    val filtered = remember(audios, searchQuery) {
        if (searchQuery.isBlank()) audios
        else audios.filter { it.title.contains(searchQuery, true) || it.artist.contains(searchQuery, true) }
    }

    Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 175.dp)
        ) {
            item {
                Text(
                    text = "音乐",
                    color = tokens.textPrimary,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
                )
            }

            if (filtered.isNotEmpty()) {
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(filtered.take(10), key = { "card_${it.id}_$tagVersion" }) { audio ->
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
                                    modifier = Modifier.size(136.dp).arashiSurface(RoundedCornerShape(22.dp))
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(saved?.title?.ifBlank { audio.title } ?: audio.title, color = tokens.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text(saved?.artist?.ifBlank { audio.artist } ?: audio.artist, color = tokens.textSecondary, fontSize = 12.sp, maxLines = 1)
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }

            items(filtered, key = { "row_${it.id}_$tagVersion" }) { audio ->
                val saved = remember(audio.id, tagVersion) { matcher.getSavedTag(audio.id) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 5.dp)
                        .arashiSurface(RoundedCornerShape(20.dp))
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
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(tokens.surfaceCard)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(saved?.title?.ifBlank { audio.title } ?: audio.title, color = tokens.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                        Text(saved?.artist?.ifBlank { audio.artist } ?: audio.artist, color = tokens.textSecondary, fontSize = 12.sp, maxLines = 1)
                    }
                    Text(formatDuration(audio.durationMs), color = tokens.textSecondary, fontSize = 12.sp)
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
                        .arashiSurface(RoundedCornerShape(28.dp))
                        .clickable(enabled = false) {}
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(audio.title, color = tokens.textPrimary, fontWeight = FontWeight.Bold, maxLines = 1)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .arashiSurface(RoundedCornerShape(18.dp), elevation = 2.dp)
                            .clickable { onEncryptAudio(audio); selectedAudio = null }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = tokens.accent)
                        Spacer(Modifier.width(12.dp))
                        Text("添加到加密空间", color = tokens.textPrimary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
