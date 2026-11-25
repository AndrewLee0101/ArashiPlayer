package com.arashiplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import coil.compose.AsyncImage
import com.arashiplayer.data.*
import com.arashiplayer.music.OnlineTagMatcher
import com.arashiplayer.ui.theme.LocalArashiTokens
import com.arashiplayer.ui.theme.arashiSurface

@Composable
fun FavoritesScreen(
    videos: List<LocalVideo>,
    audios: List<LocalAudio>,
    favoritesStore: FavoritesStore,
    favVersion: Int,
    tagVersion: Int,
    onToggleFav: (String) -> Unit,
    onVideoClick: (LocalVideo, List<LocalVideo>) -> Unit,
    onAudioClick: (LocalAudio) -> Unit
) {
    val context = LocalContext.current
    val tokens = LocalArashiTokens.current
    val matcher = remember { OnlineTagMatcher(context) }
    var subTab by remember { mutableIntStateOf(0) } // 0=视频 1=音乐

    val favVideos = remember(videos, favVersion) { videos.filter { favoritesStore.isFavorite(it.path) } }
    val favAudios = remember(audios, favVersion) { audios.filter { favoritesStore.isFavorite(it.path) } }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(14.dp))
        Text("喜欢", color = tokens.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("视频 (${favVideos.size})", "音乐 (${favAudios.size})").forEachIndexed { idx, label ->
                val active = subTab == idx
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .then(if (active) Modifier.background(tokens.accent) else Modifier.arashiSurface(RoundedCornerShape(18.dp), elevation = 2.dp))
                        .clickable { subTab = idx }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(label, color = if (active) Color.White else tokens.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        if (subTab == 0) {
            if (favVideos.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("空", color = tokens.textSecondary)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 175.dp)) {
                    items(favVideos, key = { it.id }) { v ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .arashiSurface(RoundedCornerShape(22.dp))
                                .clickable { onVideoClick(v, favVideos) }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(92.dp)
                                    .height(58.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF1A202E)),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = v.uri,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier.size(24.dp).clip(CircleShape).background(Color(0x88000000)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                }
                                if (v.durationMs > 0L) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(4.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0x88000000))
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    ) {
                                        Text(formatDuration(v.durationMs), color = Color.White, fontSize = 9.sp)
                                    }
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(v.title, color = tokens.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                                Spacer(Modifier.height(3.dp))
                                Text(formatFileSize(v.sizeBytes), color = tokens.textSecondary, fontSize = 12.sp)
                            }
                            IconButton(onClick = { onToggleFav(v.path) }) {
                                Icon(Icons.Default.Favorite, contentDescription = null, tint = Color(0xFFFF4D7D))
                            }
                        }
                    }
                }
            }
        } else {
            if (favAudios.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("空", color = tokens.textSecondary)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 175.dp)) {
                    items(favAudios, key = { "${it.id}_$tagVersion" }) { a ->
                        val saved = remember(a.id, tagVersion) { matcher.getSavedTag(a.id) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .arashiSurface(RoundedCornerShape(22.dp))
                                .clickable { onAudioClick(a) }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = saved?.coverUrl ?: a.albumArtUri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(58.dp).clip(RoundedCornerShape(14.dp)).background(tokens.surfaceCard)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(saved?.title?.ifBlank { a.title } ?: a.title, color = tokens.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                                Spacer(Modifier.height(3.dp))
                                Text(saved?.artist?.ifBlank { a.artist } ?: a.artist, color = tokens.textSecondary, fontSize = 12.sp, maxLines = 1)
                            }
                            IconButton(onClick = { onToggleFav(a.path) }) {
                                Icon(Icons.Default.Favorite, contentDescription = null, tint = Color(0xFFFF4D7D))
                            }
                        }
                    }
                }
            }
        }
    }
}
