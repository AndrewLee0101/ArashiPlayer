package com.arashiplayer.music

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.arashiplayer.data.*
import com.arashiplayer.ui.theme.*
import kotlinx.coroutines.launch

/**
 * 参考 QQ 音乐 + visionOS 液态玻璃风格的音乐播放页：
 * - 单击专辑封面直接切换全屏滚动歌词，再点歌词切回封面
 * - 封面下方自带双行歌词预览 + 音（标音）/ 译（外语翻译）开关
 * - 底部液态玻璃控制胶囊含播放模式（顺序/单曲/随机）、上一首、播放/暂停、下一首、喜欢爱心
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppleMusicPlayerSheet(
    track: LocalAudio,
    isPlaying: Boolean,
    playMode: MusicPlayMode,
    isFavorite: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    onSeekTo: (Long) -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onCyclePlayMode: () -> Unit,
    onToggleFavorite: () -> Unit,
    lyricTextColor: Color = Color.White,
    lyricHighlightColor: Color = Color(0xFF64E8FF),
    onTagSaved: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val tokens = LocalArashiTokens.current
    val scope = rememberCoroutineScope()
    val matcher = remember { OnlineTagMatcher(context) }

    BackHandler { onClose() }

    var savedTag by remember(track.id) { mutableStateOf(matcher.getSavedTag(track.id)) }
    val lyrics: List<LyricLine> = remember(savedTag) {
        LrcParser.parseMultilingual(
            originalLrc = savedTag?.syncedLyrics,
            translatedLrc = savedTag?.translatedLyrics,
            phoneticLrc = savedTag?.phoneticLyrics
        )
    }
    var showFullLyrics by remember { mutableStateOf(false) }
    var showTranslation by remember { mutableStateOf(true) }
    var showPhonetics by remember { mutableStateOf(true) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var selectedSource by remember { mutableStateOf(MusicTagSource.NETEASE) }
    var searchKeyword by remember(track.id) {
        mutableStateOf("${track.title} ${if (track.artist == "<unknown>") "" else track.artist}".trim())
    }
    var candidates by remember { mutableStateOf<List<OnlineTagResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    LaunchedEffect(track.id) {
        if (savedTag == null) {
            val found = matcher.searchCandidates(searchKeyword).firstOrNull()
            if (found != null) {
                val withLyrics = matcher.fetchLyricsForCandidate(found)
                matcher.saveTag(track.id, withLyrics)
                savedTag = withLyrics
                onTagSaved()
            }
        }
    }

    val activeLyricIndex = remember(currentPositionMs, lyrics) {
        lyrics.indexOfLast { it.timestampMs <= currentPositionMs }.coerceAtLeast(0)
    }
    val currentLine = lyrics.getOrNull(activeLyricIndex)
    val lyricListState = rememberLazyListState()
    LaunchedEffect(activeLyricIndex, showFullLyrics) {
        if (showFullLyrics && lyrics.isNotEmpty()) {
            runCatching { lyricListState.animateScrollToItem((activeLyricIndex - 2).coerceAtLeast(0)) }
        }
    }

    val coverSize by animateDpAsState(targetValue = if (isPlaying) 272.dp else 242.dp, label = "cover")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF192438), Color(0xFF121826), Color(0xFF0A0D16))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 顶部：返回圆钮 + 居中歌名歌手 + 匹配标签胶囊
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose, modifier = Modifier.size(42.dp).visionGlassCapsule(CircleShape)) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Color.White)
                }
                Column(
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(savedTag?.title?.ifBlank { track.title } ?: track.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(savedTag?.artist?.ifBlank { track.artist } ?: track.artist, color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp, maxLines = 1)
                }
                Box(
                    modifier = Modifier
                        .visionGlassCapsule(RoundedCornerShape(20.dp))
                        .clickable {
                            showSearchDialog = true
                            scope.launch {
                                isSearching = true
                                candidates = matcher.searchCandidates(searchKeyword, selectedSource)
                                isSearching = false
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text("匹配标签", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(Modifier.height(14.dp))

            // 中间区：单击封面切换全屏歌词，单击歌词切回封面
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clickable { showFullLyrics = !showFullLyrics },
                contentAlignment = Alignment.Center
            ) {
                if (showFullLyrics) {
                    if (lyrics.isEmpty()) {
                        Text("暂无歌词", color = Color.White.copy(alpha = 0.6f))
                    } else {
                        LazyColumn(
                            state = lyricListState,
                            verticalArrangement = Arrangement.spacedBy(22.dp),
                            contentPadding = PaddingValues(vertical = 48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            itemsIndexed(lyrics) { idx, line ->
                                val active = idx == activeLyricIndex
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = { showFullLyrics = false },
                                            onLongClick = { onSeekTo(line.timestampMs) }
                                        ),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    if (showPhonetics && !line.phonetic.isNullOrBlank()) {
                                        Text(
                                            text = line.phonetic,
                                            color = if (active) Color(0xFF7BE3FF) else Color.White.copy(alpha = 0.35f),
                                            fontSize = if (active) 13.sp else 11.sp,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(Modifier.height(2.dp))
                                    }
                                    Text(
                                        text = line.text,
                                        color = if (active) lyricHighlightColor else lyricTextColor.copy(alpha = 0.45f),
                                        fontSize = if (active) 22.sp else 17.sp,
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                        lineHeight = 30.sp,
                                        textAlign = TextAlign.Center
                                    )
                                    if (showTranslation && !line.translation.isNullOrBlank()) {
                                        Spacer(Modifier.height(3.dp))
                                        Text(
                                            text = line.translation,
                                            color = if (active) lyricHighlightColor.copy(alpha = 0.86f) else lyricTextColor.copy(alpha = 0.36f),
                                            fontSize = if (active) 15.sp else 13.sp,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        AsyncImage(
                            model = savedTag?.coverUrl ?: track.albumArtUri,
                            contentDescription = track.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(coverSize)
                                .shadow(24.dp, RoundedCornerShape(30.dp))
                                .clip(RoundedCornerShape(30.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.38f), RoundedCornerShape(30.dp))
                                .background(Color(0xFF1D2434))
                        )
                        Spacer(Modifier.height(24.dp))
                        // QQ 音乐风格封面下方双行实时歌词预览
                        if (currentLine != null) {
                            if (showPhonetics && !currentLine.phonetic.isNullOrBlank()) {
                                Text(currentLine.phonetic, color = Color(0xFF7BE3FF), fontSize = 12.sp, maxLines = 1, textAlign = TextAlign.Center)
                                Spacer(Modifier.height(2.dp))
                            }
                            Text(currentLine.text, color = lyricHighlightColor, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, textAlign = TextAlign.Center)
                            if (showTranslation && !currentLine.translation.isNullOrBlank()) {
                                Spacer(Modifier.height(3.dp))
                                Text(currentLine.translation, color = lyricTextColor.copy(alpha = 0.72f), fontSize = 13.sp, maxLines = 1, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }

            // 右对齐「音 | 译」紧凑胶囊开关
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.visionGlassCapsule(RoundedCornerShape(16.dp)).padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    SmallToggleChip("音", showPhonetics) { showPhonetics = !showPhonetics }
                    SmallToggleChip("译", showTranslation) { showTranslation = !showTranslation }
                }
            }

            Spacer(Modifier.height(10.dp))

            // 进度条
            Slider(
                value = if (durationMs > 0L) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f,
                onValueChange = { onSeekTo((it * durationMs).toLong()) },
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.24f)
                ),
                modifier = Modifier.height(24.dp)
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatDuration(currentPositionMs), color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp)
                Text(formatDuration(durationMs), color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
            }

            Spacer(Modifier.height(12.dp))

            // 底部液态玻璃播放控制胶囊（模式 / 上一首 / 播放暂停 / 下一首 / 喜欢）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .visionGlassCapsule(RoundedCornerShape(34.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onCyclePlayMode) {
                    val modeIcon = when (playMode) {
                        MusicPlayMode.ORDER -> Icons.Default.Repeat
                        MusicPlayMode.SINGLE_LOOP -> Icons.Default.RepeatOne
                        MusicPlayMode.SHUFFLE -> Icons.Default.Shuffle
                    }
                    Icon(modeIcon, contentDescription = null, tint = Color.White.copy(alpha = 0.85f))
                }
                IconButton(onClick = onPrev) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
                }
                IconButton(
                    onClick = onTogglePlay,
                    modifier = Modifier.size(58.dp).visionGlassCapsule(CircleShape)
                ) {
                    Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp))
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.Default.SkipNext, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
                }
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        tint = if (isFavorite) Color(0xFFFF4D7D) else Color.White.copy(alpha = 0.85f)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        }

        if (showSearchDialog) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0x88000000)).clickable { showSearchDialog = false },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                        .heightIn(max = 520.dp)
                        .visionGlassCapsule(RoundedCornerShape(28.dp))
                        .clickable(enabled = false) {}
                        .padding(18.dp)
                ) {
                    Text("匹配标签", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = searchKeyword, onValueChange = { searchKeyword = it }, singleLine = true, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                scope.launch {
                                    isSearching = true
                                    candidates = matcher.searchCandidates(searchKeyword, selectedSource)
                                    isSearching = false
                                }
                            },
                            modifier = Modifier.visionGlassCapsule(CircleShape)
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null, tint = Color.White)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MusicTagSource.entries.forEach { src ->
                            val active = src == selectedSource
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (active) Color(0x66FFFFFF) else Color(0x22FFFFFF))
                                    .border(0.8.dp, if (active) Color.White else Color.White.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                                    .clickable {
                                        selectedSource = src
                                        scope.launch {
                                            isSearching = true
                                            candidates = matcher.searchCandidates(searchKeyword, src)
                                            isSearching = false
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(src.label, color = Color.White, fontSize = 12.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    if (isSearching) {
                        Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = tokens.accent)
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(candidates) { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .visionGlassCapsule(RoundedCornerShape(16.dp))
                                        .clickable {
                                            scope.launch {
                                                val full = matcher.fetchLyricsForCandidate(item)
                                                matcher.saveTag(track.id, full)
                                                savedTag = full
                                                onTagSaved()
                                                showSearchDialog = false
                                            }
                                        }
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(model = item.coverUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)))
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                        Text("${item.artist} · ${item.source}", color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallToggleChip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) Color(0x55FFFFFF) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(label, color = if (active) Color.White else Color.White.copy(alpha = 0.55f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
