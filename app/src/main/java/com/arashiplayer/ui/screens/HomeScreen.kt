package com.arashiplayer.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import com.arashiplayer.data.*
import com.arashiplayer.music.OnlineTagMatcher
import com.arashiplayer.ui.theme.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoLibraryScreen(
    videos: List<LocalVideo>,
    searchQuery: String,
    favoritesStore: FavoritesStore,
    favVersion: Int,
    onFavoriteChanged: () -> Unit,
    onToggleSearch: () -> Unit,
    onVideoClick: (LocalVideo, List<LocalVideo>) -> Unit,
    onRenameVideo: (LocalVideo, String) -> Unit,
    onDeleteVideos: (List<LocalVideo>) -> Unit,
    onEncryptVideos: (List<LocalVideo>) -> Unit
) {
    val tokens = LocalArashiTokens.current
    var layoutMode by remember { mutableStateOf(VideoLayoutMode.FOLDER_ROW) }
    var activeFolder by remember { mutableStateOf<String?>(null) }
    var multiSelectMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var renameTarget by remember { mutableStateOf<LocalVideo?>(null) }

    val baseVideos = remember(videos, searchQuery, activeFolder) {
        val q = if (searchQuery.isBlank()) videos else videos.filter { it.title.contains(searchQuery, true) }
        if (activeFolder != null) q.filter { it.folderName == activeFolder } else q
    }

    if (multiSelectMode) {
        BackHandler { multiSelectMode = false; selectedIds = emptySet() }
    } else if (activeFolder != null) {
        BackHandler { activeFolder = null }
    }

    Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 简洁极简顶栏（含列表内全选按钮、行/列/网格切换、搜索、私密锁）
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (activeFolder != null) {
                    IconButton(onClick = { activeFolder = null }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = tokens.textPrimary)
                    }
                    Text(activeFolder!!, color = tokens.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
                } else {
                    Text("岚播放器", color = tokens.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                }

                // 列表内「全选」胶囊
                Box(
                    modifier = Modifier
                        .arashiSurface(RoundedCornerShape(16.dp), elevation = 2.dp)
                        .clickable {
                            if (!multiSelectMode) {
                                multiSelectMode = true
                                selectedIds = baseVideos.map { it.id }.toSet()
                            } else if (selectedIds.size < baseVideos.size) {
                                selectedIds = baseVideos.map { it.id }.toSet()
                            } else {
                                multiSelectMode = false
                                selectedIds = emptySet()
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (multiSelectMode && selectedIds.size == baseVideos.size) "取消" else "全选",
                        color = tokens.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(Modifier.width(6.dp))

                // 行 / 列 / 网格 切换按钮
                IconButton(onClick = {
                    layoutMode = when (layoutMode) {
                        VideoLayoutMode.FOLDER_ROW -> VideoLayoutMode.COLUMN_LIST
                        VideoLayoutMode.COLUMN_LIST -> VideoLayoutMode.GRID
                        VideoLayoutMode.GRID -> VideoLayoutMode.FOLDER_ROW
                    }
                }) {
                    val icon = when (layoutMode) {
                        VideoLayoutMode.FOLDER_ROW -> Icons.Default.ViewCarousel
                        VideoLayoutMode.COLUMN_LIST -> Icons.Default.ViewList
                        VideoLayoutMode.GRID -> Icons.Default.GridView
                    }
                    Icon(icon, contentDescription = null, tint = tokens.textPrimary)
                }

                IconButton(onClick = onToggleSearch) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = tokens.textPrimary)
                }

            }

            // 批量操作条（全选/多选时在顶部出现）
            if (multiSelectMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                        .arashiSurface(RoundedCornerShape(18.dp), elevation = 2.dp)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("已选 ${selectedIds.size} 项", color = tokens.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("喜欢", color = tokens.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable {
                            baseVideos.filter { it.id in selectedIds }.forEach { favoritesStore.toggleFavorite(it.path) }
                            onFavoriteChanged(); multiSelectMode = false; selectedIds = emptySet()
                        })
                        Text("加密", color = tokens.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable {
                            val chosen = baseVideos.filter { it.id in selectedIds }
                            onEncryptVideos(chosen)
                            multiSelectMode = false; selectedIds = emptySet()
                        })
                        Text("删除", color = Color(0xFFFF2442), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable {
                            val chosen = baseVideos.filter { it.id in selectedIds }
                            onDeleteVideos(chosen)
                            multiSelectMode = false; selectedIds = emptySet()
                        })
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 175.dp)
            ) {
                if (layoutMode == VideoLayoutMode.FOLDER_ROW && activeFolder == null) {
                    val grouped = baseVideos.groupBy { it.folderName }.entries.toList()
                    items(grouped, key = { it.key }) { (folder, folderVideos) ->
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { activeFolder = folder }
                                    .padding(horizontal = 20.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(folder, color = tokens.textPrimary, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Text("${folderVideos.size}", color = tokens.textSecondary, fontSize = 14.sp)
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = tokens.textSecondary, modifier = Modifier.size(16.dp))
                            }
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                items(folderVideos, key = { "${it.id}_$favVersion" }) { video ->
                                    VideoPosterCard(
                                        video = video,
                                        widthDp = 176,
                                        isFav = favoritesStore.isFavorite(video.path),
                                        multiSelectMode = multiSelectMode,
                                        isSelected = video.id in selectedIds,
                                        onClick = {
                                            if (multiSelectMode) {
                                                selectedIds = if (video.id in selectedIds) selectedIds - video.id else selectedIds + video.id
                                            } else onVideoClick(video, folderVideos)
                                        },
                                        onRename = { renameTarget = video },
                                        onDelete = { onDeleteVideos(listOf(video)) },
                                        onToggleFav = { favoritesStore.toggleFavorite(video.path); onFavoriteChanged() },
                                        onEncrypt = { onEncryptVideos(listOf(video)) }
                                    )
                                }
                            }
                        }
                    }
                } else if (layoutMode == VideoLayoutMode.GRID) {
                    val rows = baseVideos.chunked(2)
                    items(rows) { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 7.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            pair.forEach { video ->
                                Box(modifier = Modifier.weight(1f)) {
                                    VideoPosterCard(
                                        video = video,
                                        widthDp = 0,
                                        isFav = favoritesStore.isFavorite(video.path),
                                        multiSelectMode = multiSelectMode,
                                        isSelected = video.id in selectedIds,
                                        onClick = {
                                            if (multiSelectMode) {
                                                selectedIds = if (video.id in selectedIds) selectedIds - video.id else selectedIds + video.id
                                            } else onVideoClick(video, baseVideos)
                                        },
                                        onRename = { renameTarget = video },
                                        onDelete = { onDeleteVideos(listOf(video)) },
                                        onToggleFav = { favoritesStore.toggleFavorite(video.path); onFavoriteChanged() },
                                        onEncrypt = { onEncryptVideos(listOf(video)) }
                                    )
                                }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                } else {
                    // 单列列表模式
                    items(baseVideos, key = { "col_${it.id}_$favVersion" }) { video ->
                        VideoListRowItem(
                            video = video,
                            isFav = favoritesStore.isFavorite(video.path),
                            multiSelectMode = multiSelectMode,
                            isSelected = video.id in selectedIds,
                            onClick = {
                                if (multiSelectMode) {
                                    selectedIds = if (video.id in selectedIds) selectedIds - video.id else selectedIds + video.id
                                } else onVideoClick(video, baseVideos)
                            },
                            onRename = { renameTarget = video },
                            onDelete = { onDeleteVideos(listOf(video)) },
                            onToggleFav = { favoritesStore.toggleFavorite(video.path); onFavoriteChanged() },
                            onEncrypt = { onEncryptVideos(listOf(video)) }
                        )
                    }
                }
            }
        }

        // 重命名对话框
        renameTarget?.let { v ->
            RenameDialog(
                initialName = v.title.substringBeforeLast('.'),
                onConfirm = { newName -> onRenameVideo(v, newName); renameTarget = null },
                onDismiss = { renameTarget = null }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VideoPosterCard(
    video: LocalVideo,
    widthDp: Int,
    isFav: Boolean,
    multiSelectMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onToggleFav: () -> Unit,
    onEncrypt: () -> Unit
) {
    val tokens = LocalArashiTokens.current
    var menuExpanded by remember { mutableStateOf(false) }

    Box(modifier = if (widthDp > 0) Modifier.width(widthDp.dp) else Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { if (!multiSelectMode) menuExpanded = true }
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
                    .shadow(6.dp, RoundedCornerShape(18.dp), ambientColor = Color(0x18000000), spotColor = Color(0x22000000))
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF1A202E))
            ) {
                AsyncImage(
                    model = video.uri,
                    contentDescription = video.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(7.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x88000000))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(formatDuration(video.durationMs), color = Color.White, fontSize = 10.sp)
                }
                if (isFav) {
                    Icon(
                        Icons.Default.Favorite,
                        contentDescription = null,
                        tint = Color(0xFFFF4D7D),
                        modifier = Modifier.align(Alignment.TopEnd).padding(7.dp).size(16.dp)
                    )
                }
                if (multiSelectMode) {
                    Icon(
                        if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (isSelected) tokens.accent else Color.White,
                        modifier = Modifier.align(Alignment.TopStart).padding(7.dp).size(20.dp)
                    )
                }
            }
            Spacer(Modifier.height(7.dp))
            Text(video.title, color = tokens.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(formatFileSize(video.sizeBytes), color = tokens.textSecondary, fontSize = 11.sp)
        }

        // 视频旁悬浮四项菜单（重命名 / 删除 / 喜欢 / 加密）
        ItemContextMenu(
            expanded = menuExpanded,
            isFav = isFav,
            onDismiss = { menuExpanded = false },
            onRename = { menuExpanded = false; onRename() },
            onDelete = { menuExpanded = false; onDelete() },
            onToggleFav = { menuExpanded = false; onToggleFav() },
            onEncrypt = { menuExpanded = false; onEncrypt() }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VideoListRowItem(
    video: LocalVideo,
    isFav: Boolean,
    multiSelectMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onToggleFav: () -> Unit,
    onEncrypt: () -> Unit
) {
    val tokens = LocalArashiTokens.current
    var menuExpanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .arashiSurface(RoundedCornerShape(18.dp), elevation = 2.dp)
                .combinedClickable(onClick = onClick, onLongClick = { if (!multiSelectMode) menuExpanded = true })
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (multiSelectMode) {
                Icon(
                    if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (isSelected) tokens.accent else tokens.textSecondary,
                    modifier = Modifier.padding(end = 10.dp).size(20.dp)
                )
            }
            AsyncImage(
                model = video.uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(104.dp).height(62.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF1A202E))
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(video.title, color = tokens.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2)
                Spacer(Modifier.height(3.dp))
                Text("${formatDuration(video.durationMs)} · ${formatFileSize(video.sizeBytes)}", color = tokens.textSecondary, fontSize = 11.sp)
            }
            if (isFav) {
                Icon(Icons.Default.Favorite, contentDescription = null, tint = Color(0xFFFF4D7D), modifier = Modifier.size(16.dp))
            }
        }
        ItemContextMenu(
            expanded = menuExpanded,
            isFav = isFav,
            onDismiss = { menuExpanded = false },
            onRename = { menuExpanded = false; onRename() },
            onDelete = { menuExpanded = false; onDelete() },
            onToggleFav = { menuExpanded = false; onToggleFav() },
            onEncrypt = { menuExpanded = false; onEncrypt() }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MusicLibraryScreen(
    audios: List<LocalAudio>,
    searchQuery: String,
    tagVersion: Int,
    favoritesStore: FavoritesStore,
    favVersion: Int,
    onFavoriteChanged: () -> Unit,
    onToggleSearch: () -> Unit,
    onAudioClick: (LocalAudio) -> Unit,
    onRenameAudio: (LocalAudio, String) -> Unit,
    onDeleteAudios: (List<LocalAudio>) -> Unit,
    onEncryptAudios: (List<LocalAudio>) -> Unit
) {
    val context = LocalContext.current
    val tokens = LocalArashiTokens.current
    val matcher = remember { OnlineTagMatcher(context) }
    var multiSelectMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var renameTarget by remember { mutableStateOf<LocalAudio?>(null) }

    val filtered = remember(audios, searchQuery) {
        if (searchQuery.isBlank()) audios
        else audios.filter { it.title.contains(searchQuery, true) || it.artist.contains(searchQuery, true) }
    }

    if (multiSelectMode) {
        BackHandler { multiSelectMode = false; selectedIds = emptySet() }
    }

    Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("音乐", color = tokens.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .arashiSurface(RoundedCornerShape(16.dp), elevation = 2.dp)
                        .clickable {
                            if (!multiSelectMode) {
                                multiSelectMode = true
                                selectedIds = filtered.map { it.id }.toSet()
                            } else if (selectedIds.size < filtered.size) {
                                selectedIds = filtered.map { it.id }.toSet()
                            } else {
                                multiSelectMode = false
                                selectedIds = emptySet()
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(if (multiSelectMode && selectedIds.size == filtered.size) "取消" else "全选", color = tokens.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
                IconButton(onClick = onToggleSearch) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = tokens.textPrimary)
                }
            }

            if (multiSelectMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                        .arashiSurface(RoundedCornerShape(18.dp), elevation = 2.dp)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("已选 ${selectedIds.size} 首", color = tokens.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("喜欢", color = tokens.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable {
                            filtered.filter { it.id in selectedIds }.forEach { favoritesStore.toggleFavorite(it.path) }
                            onFavoriteChanged(); multiSelectMode = false; selectedIds = emptySet()
                        })
                        Text("加密", color = tokens.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable {
                            onEncryptAudios(filtered.filter { it.id in selectedIds })
                            multiSelectMode = false; selectedIds = emptySet()
                        })
                        Text("删除", color = Color(0xFFFF2442), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable {
                            onDeleteAudios(filtered.filter { it.id in selectedIds })
                            multiSelectMode = false; selectedIds = emptySet()
                        })
                    }
                }
            }

            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 175.dp)) {
                items(filtered, key = { "row_${it.id}_${tagVersion}_$favVersion" }) { audio ->
                    val saved = remember(audio.id, tagVersion) { matcher.getSavedTag(audio.id) }
                    val isFav = favoritesStore.isFavorite(audio.path)
                    var menuExpanded by remember { mutableStateOf(false) }

                    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .arashiSurface(RoundedCornerShape(18.dp), elevation = 2.dp)
                                .combinedClickable(
                                    onClick = {
                                        if (multiSelectMode) {
                                            selectedIds = if (audio.id in selectedIds) selectedIds - audio.id else selectedIds + audio.id
                                        } else onAudioClick(audio)
                                    },
                                    onLongClick = { if (!multiSelectMode) menuExpanded = true }
                                )
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (multiSelectMode) {
                                Icon(
                                    if (audio.id in selectedIds) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (audio.id in selectedIds) tokens.accent else tokens.textSecondary,
                                    modifier = Modifier.padding(end = 10.dp).size(20.dp)
                                )
                            }
                            AsyncImage(
                                model = saved?.coverUrl ?: audio.albumArtUri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(tokens.surfaceCard)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(saved?.title?.ifBlank { audio.title } ?: audio.title, color = tokens.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                                Text(saved?.artist?.ifBlank { audio.artist } ?: audio.artist, color = tokens.textSecondary, fontSize = 12.sp, maxLines = 1)
                            }
                            if (isFav) {
                                Icon(Icons.Default.Favorite, contentDescription = null, tint = Color(0xFFFF4D7D), modifier = Modifier.padding(end = 6.dp).size(15.dp))
                            }
                            Text(formatDuration(audio.durationMs), color = tokens.textSecondary, fontSize = 12.sp)
                        }

                        ItemContextMenu(
                            expanded = menuExpanded,
                            isFav = isFav,
                            onDismiss = { menuExpanded = false },
                            onRename = { menuExpanded = false; renameTarget = audio },
                            onDelete = { menuExpanded = false; onDeleteAudios(listOf(audio)) },
                            onToggleFav = { menuExpanded = false; favoritesStore.toggleFavorite(audio.path); onFavoriteChanged() },
                            onEncrypt = { menuExpanded = false; onEncryptAudios(listOf(audio)) }
                        )
                    }
                }
            }
        }

        renameTarget?.let { a ->
            RenameDialog(
                initialName = a.title,
                onConfirm = { newName -> onRenameAudio(a, newName); renameTarget = null },
                onDismiss = { renameTarget = null }
            )
        }
    }
}

/**
 * 锚定在条目旁边的悬浮四项菜单（重命名 / 删除 / 喜欢 / 加密）
 */
@Composable
private fun ItemContextMenu(
    expanded: Boolean,
    isFav: Boolean,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onToggleFav: () -> Unit,
    onEncrypt: () -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("重命名") },
            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
            onClick = onRename
        )
        DropdownMenuItem(
            text = { Text("删除") },
            leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null) },
            onClick = onDelete
        )
        DropdownMenuItem(
            text = { Text(if (isFav) "取消喜欢" else "喜欢") },
            leadingIcon = { Icon(if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = null) },
            onClick = onToggleFav
        )
        DropdownMenuItem(
            text = { Text("加密") },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            onClick = onEncrypt
        )
    }
}

@Composable
private fun RenameDialog(initialName: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名") },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onConfirm(name.trim()) }) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
