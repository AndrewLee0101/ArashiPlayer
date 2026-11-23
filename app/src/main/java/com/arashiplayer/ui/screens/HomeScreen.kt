package com.arashiplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.arashiplayer.data.LocalAudio
import com.arashiplayer.data.LocalVideo
import com.arashiplayer.ui.theme.*

@Composable
fun HomeScreen(
    videos: List<LocalVideo>,
    audios: List<LocalAudio>,
    onVideoClick: (LocalVideo) -> Unit,
    onAudioClick: (LocalAudio) -> Unit,
    onOpenVault: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF15263B), ObsidianBg),
                    radius = 900f
                )
            )
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 140.dp)
    ) {
        // 顶部 Apple 风格大标题栏
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
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceGlass)
                ) {
                    Icon(Icons.Default.Shield, contentDescription = "私密空间", tint = AccentCyan)
                }
            }
        }

        // 继续播放 Hero 卡片
        if (videos.isNotEmpty()) {
            val hero = videos.first()
            item {
                Text(
                    text = "继续播放",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .height(190.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(SurfaceCard)
                        .clickable { onVideoClick(hero) }
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
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color(0xCC0A0B10))
                                )
                            )
                    )
                    Icon(
                        imageVector = Icons.Default.PlayCircleFilled,
                        contentDescription = "播放",
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier
                            .size(56.dp)
                            .align(Alignment.Center)
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp)
                    ) {
                        Text(hero.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(formatDuration(hero.durationMs), color = TextSecondary, fontSize = 12.sp)
                    }
                }
            }
        }

        // 本地视频横向卡片流
        item {
            Spacer(Modifier.height(22.dp))
            Text(
                text = "本地视频 (${videos.size})",
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(videos) { video ->
                    Column(
                        modifier = Modifier
                            .width(172.dp)
                            .clickable { onVideoClick(video) }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(104.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(SurfaceCard)
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
                                    .padding(8.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.White.copy(alpha = 0.9f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(video.title, color = TextPrimary, fontSize = 14.sp, maxLines = 1)
                    }
                }
            }
        }

        // 本地音乐 Apple Music 专辑网格
        item {
            Spacer(Modifier.height(22.dp))
            Text(
                text = "本地音乐 (${audios.size})",
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(audios) { audio ->
                    Column(
                        modifier = Modifier
                            .width(126.dp)
                            .clickable { onAudioClick(audio) }
                    ) {
                        AsyncImage(
                            model = audio.albumArtUri,
                            contentDescription = audio.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(126.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(SurfaceCard)
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(audio.title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                        Text(audio.artist, color = TextSecondary, fontSize = 12.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
