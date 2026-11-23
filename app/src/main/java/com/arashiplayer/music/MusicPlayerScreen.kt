package com.arashiplayer.music

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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

    var savedTag by remember(track.id) { mutableStateOf(matcher.getSavedTag(track.id)) }
    val lyrics: List<LyricLine> = remember(savedTag) { LrcParser.parse(savedTag?.syncedLyrics) }
    var showLyrics by remember { mutableStateOf(false) }
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

    val coverSize by animateDpAsState(targetValue = if (isPlaying) 288.dp else 248.dp, label = "cover")

    ArashiBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose, modifier = Modifier.arashiSurface(CircleShape)) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = tokens.textPrimary)
                }
                Row(
                    modifier = Modifier
                        .arashiSurface(RoundedCornerShape(20.dp))
                        .clickable {
                            showSearchDialog = true
                            scope.launch {
                                isSearching = true
                                candidates = matcher.searchCandidates(searchKeyword)
                                isSearching = false
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = tokens.accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("匹配标签", color = tokens.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(14.dp))

            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (showLyrics) {
                    if (lyrics.isEmpty()) {
                        Text("暂无歌词", color = tokens.textSecondary)
                    } else {
                        LazyColumn(
                            state = lyricListState,
                            verticalArrangement = Arrangement.spacedBy(18.dp),
                            contentPadding = PaddingValues(vertical = 48.dp)
                        ) {
                            itemsIndexed(lyrics) { idx, line ->
                                val active = idx == activeLyricIndex
                                Text(
                                    text = line.text,
                                    color = if (active) tokens.textPrimary else tokens.textSecondary.copy(alpha = 0.5f),
                                    fontSize = if (active) 22.sp else 18.sp,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                    lineHeight = 30.sp,
                                    modifier = Modifier.fillMaxWidth().clickable { onSeekTo(line.timestampMs) }
                                )
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
                            .shadow(22.dp, RoundedCornerShape(28.dp))
                            .clip(RoundedCornerShape(28.dp))
                            .background(tokens.surfaceCard)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(savedTag?.title?.ifBlank { track.title } ?: track.title, color = tokens.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Spacer(Modifier.height(4.dp))
                    Text(savedTag?.artist?.ifBlank { track.artist } ?: track.artist, color = tokens.textSecondary, fontSize = 15.sp, maxLines = 1)
                }
                IconButton(onClick = { showLyrics = !showLyrics }, modifier = Modifier.arashiSurface(CircleShape)) {
                    Icon(Icons.Default.Lyrics, contentDescription = null, tint = if (showLyrics) tokens.accent else tokens.textPrimary)
                }
            }

            Spacer(Modifier.height(10.dp))

            Slider(
                value = if (durationMs > 0L) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f,
                onValueChange = { onSeekTo((it * durationMs).toLong()) },
                colors = SliderDefaults.colors(thumbColor = tokens.accent, activeTrackColor = tokens.accent)
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatDuration(currentPositionMs), color = tokens.textSecondary, fontSize = 12.sp)
                Text(formatDuration(durationMs), color = tokens.textSecondary, fontSize = 12.sp)
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPrev, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = null, tint = tokens.textPrimary, modifier = Modifier.size(38.dp))
                }
                IconButton(
                    onClick = onTogglePlay,
                    modifier = Modifier.size(72.dp).clip(CircleShape).background(tokens.accent)
                ) {
                    Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
                }
                IconButton(onClick = onNext, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Default.SkipNext, contentDescription = null, tint = tokens.textPrimary, modifier = Modifier.size(38.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
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
                        .arashiSurface(RoundedCornerShape(28.dp))
                        .clickable(enabled = false) {}
                        .padding(18.dp)
                ) {
                    Text("匹配封面与歌词", color = tokens.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
                            modifier = Modifier.arashiSurface(CircleShape)
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null, tint = tokens.accent)
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
                                        .arashiSurface(RoundedCornerShape(16.dp))
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
                                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp))
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.title, color = tokens.textPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                        Text("${item.artist} · ${item.source}", color = tokens.textSecondary, fontSize = 12.sp, maxLines = 1)
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
