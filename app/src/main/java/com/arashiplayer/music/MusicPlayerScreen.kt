package com.arashiplayer.music

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import com.arashiplayer.data.LocalAudio
import com.arashiplayer.data.LyricLine
import com.arashiplayer.data.OnlineTagResult
import com.arashiplayer.data.formatDuration
import com.arashiplayer.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun AppleMusicPlayerSheet(
    track: LocalAudio,
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    onSeekTo: (Long) -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
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
    var showLyrics by remember { mutableStateOf(false) }
    var showTranslation by remember { mutableStateOf(true) }
    var showPhonetics by remember { mutableStateOf(true) }
    var showSearchDialog by remember { mutableStateOf(false) }
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
    val lyricListState = rememberLazyListState()
    LaunchedEffect(activeLyricIndex, showLyrics) {
        if (showLyrics && lyrics.isNotEmpty()) {
            runCatching { lyricListState.animateScrollToItem((activeLyricIndex - 2).coerceAtLeast(0)) }
        }
    }

    val coverSize by animateDpAsState(targetValue = if (isPlaying) 276.dp else 242.dp, label = "cover")

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
            // 顶部 visionOS 分离式悬浮玻璃胶囊
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose, modifier = Modifier.size(44.dp).visionGlassCapsule(CircleShape)) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Color.White)
                }
                Row(
                    modifier = Modifier
                        .visionGlassCapsule(RoundedCornerShape(22.dp))
                        .clickable {
                            showSearchDialog = true
                            scope.launch {
                                isSearching = true
                                candidates = matcher.searchCandidates(searchKeyword)
                                isSearching = false
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("匹配标签", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(Modifier.height(14.dp))

            // 封面 / 双语+标音滚动歌词区
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (showLyrics) {
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
                                        .clickable { onSeekTo(line.timestampMs) },
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    // 标音（日语罗马音 / 汉语拼音）
                                    if (showPhonetics && !line.phonetic.isNullOrBlank()) {
                                        Text(
                                            text = line.phonetic,
                                            color = if (active) Color(0xFF7BE3FF) else Color.White.copy(alpha = 0.35f),
                                            fontSize = if (active) 13.sp else 11.sp,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(Modifier.height(2.dp))
                                    }
                                    // 原词
                                    Text(
                                        text = line.text,
                                        color = if (active) Color.White else Color.White.copy(alpha = 0.42f),
                                        fontSize = if (active) 22.sp else 17.sp,
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                        lineHeight = 30.sp,
                                        textAlign = TextAlign.Center
                                    )
                                    // 外语中文翻译
                                    if (showTranslation && !line.translation.isNullOrBlank()) {
                                        Spacer(Modifier.height(3.dp))
                                        Text(
                                            text = line.translation,
                                            color = if (active) Color.White.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.34f),
                                            fontSize = if (active) 15.sp else 13.sp,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
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
                }
            }

            Spacer(Modifier.height(12.dp))

            // 歌名 + 音 / 译 / 词 液态玻璃切换胶囊
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(savedTag?.title?.ifBlank { track.title } ?: track.title, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Spacer(Modifier.height(2.dp))
                    Text(savedTag?.artist?.ifBlank { track.artist } ?: track.artist, color = Color.White.copy(alpha = 0.65f), fontSize = 14.sp, maxLines = 1)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (showLyrics) {
                        LyricPillButton("音", showPhonetics) { showPhonetics = !showPhonetics }
                        LyricPillButton("译", showTranslation) { showTranslation = !showTranslation }
                    }
                    LyricPillButton("词", showLyrics) { showLyrics = !showLyrics }
                }
            }

            Spacer(Modifier.height(14.dp))

            // 底部 visionOS 悬浮液态玻璃播放控制胶囊
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .visionGlassCapsule(RoundedCornerShape(32.dp))
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatDuration(currentPositionMs), color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
                    Slider(
                        value = if (durationMs > 0L) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f,
                        onValueChange = { onSeekTo((it * durationMs).toLong()) },
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier.weight(1f).height(26.dp).padding(horizontal = 8.dp)
                    )
                    Text(formatDuration(durationMs), color = Color.White.copy(alpha = 0.65f), fontSize = 11.sp)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onPrev) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                    IconButton(onClick = onTogglePlay) {
                        Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(38.dp))
                    }
                    IconButton(onClick = onNext) {
                        Icon(Icons.Default.SkipNext, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
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
                    Text("匹配封面、翻译与标音歌词", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = searchKeyword,
                            onValueChange = { searchKeyword = it },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                scope.launch {
                                    isSearching = true
                                    candidates = matcher.searchCandidates(searchKeyword)
                                    isSearching = false
                                }
                            },
                            modifier = Modifier.visionGlassCapsule(CircleShape)
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null, tint = Color.White)
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
                                    AsyncImage(
                                        model = item.coverUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(46.dp).clip(RoundedCornerShape(10.dp))
                                    )
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
private fun LyricPillButton(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (active) Color(0x447BE3FF) else Color(0x28FFFFFF))
            .border(0.9.dp, if (active) Color(0xFF7BE3FF) else Color.White.copy(alpha = 0.28f), RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 13.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
