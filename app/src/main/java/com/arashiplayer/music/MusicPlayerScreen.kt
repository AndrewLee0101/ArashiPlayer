package com.arashiplayer.music

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.arashiplayer.data.LocalAudio
import com.arashiplayer.data.LyricLine
import com.arashiplayer.ui.theme.AccentCyan
import com.arashiplayer.ui.theme.ApplePink
import com.arashiplayer.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * 苹果风（Apple Music 风格）全屏音乐播放页：
 * - 动态渐变氛围背景 + 大圆角浮雕专辑封面
 * - 支持一键联网匹配高清封面与时间轴 LRC 歌词（参考「音乐标签」）
 */
@Composable
fun AppleMusicPlayerSheet(
    track: LocalAudio,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val matcher = remember { OnlineTagMatcher() }
    var onlineCover by remember(track.id) { mutableStateOf<String?>(null) }
    var lyrics by remember(track.id) { mutableStateOf<List<LyricLine>>(emptyList()) }
    var showLyrics by remember { mutableStateOf(false) }
    var matchingStatus by remember(track.id) { mutableStateOf<String?>(null) }

    val coverSize by animateDpAsState(targetValue = if (isPlaying) 296.dp else 256.dp, label = "coverScale")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF1D243B), Color(0xFF101320), Color(0xFF0A0B10))
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp, vertical = 16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 顶部下拉指示条与在线匹配按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "收起", tint = Color.White)
                }
                TextButton(
                    onClick = {
                        scope.launch {
                            matchingStatus = "正在联网匹配封面与歌词..."
                            val res = matcher.searchOnlineMetadata(track.title, track.artist)
                            if (res != null) {
                                onlineCover = res.coverUrl
                                lyrics = LrcParser.parse(res.syncedLyrics)
                                matchingStatus = "已匹配高清封面与歌词"
                            } else {
                                matchingStatus = "未找到匹配信息"
                            }
                        }
                    }
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("匹配封面/歌词", color = AccentCyan, fontSize = 13.sp)
                }
            }

            matchingStatus?.let {
                Text(it, color = TextSecondary, fontSize = 12.sp)
            }

            Spacer(Modifier.height(16.dp))

            // 专辑封面 / 滚动歌词切换区
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (showLyrics) {
                    if (lyrics.isEmpty()) {
                        Text("暂无歌词，点击右上角「匹配封面/歌词」获取", color = TextSecondary)
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(18.dp),
                            contentPadding = PaddingValues(vertical = 24.dp)
                        ) {
                            items(lyrics) { line ->
                                Text(
                                    text = line.text,
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    lineHeight = 28.sp
                                )
                            }
                        }
                    }
                } else {
                    AsyncImage(
                        model = onlineCover ?: track.albumArtUri,
                        contentDescription = track.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(coverSize)
                            .shadow(24.dp, RoundedCornerShape(24.dp))
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xFF23283D))
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // 歌曲信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(track.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Spacer(Modifier.height(4.dp))
                    Text("${track.artist} · ${track.album}", color = TextSecondary, fontSize = 15.sp, maxLines = 1)
                }
                IconButton(onClick = { showLyrics = !showLyrics }) {
                    Icon(
                        Icons.Default.Lyrics,
                        contentDescription = "切换歌词",
                        tint = if (showLyrics) ApplePink else Color.White
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // 苹果风播放控制组
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPrev, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "上一首", tint = Color.White, modifier = Modifier.size(38.dp))
                }
                IconButton(
                    onClick = onTogglePlay,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "播放/暂停",
                        tint = Color.Black,
                        modifier = Modifier.size(40.dp)
                    )
                }
                IconButton(onClick = onNext, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Default.SkipNext, contentDescription = "下一首", tint = Color.White, modifier = Modifier.size(38.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
