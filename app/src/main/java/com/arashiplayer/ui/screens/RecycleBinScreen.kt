package com.arashiplayer.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arashiplayer.data.*
import com.arashiplayer.ui.theme.LocalArashiTokens
import com.arashiplayer.ui.theme.arashiSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RecycleBinScreen(
    recycleBinManager: RecycleBinManager,
    onRefreshMedia: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val tokens = LocalArashiTokens.current

    BackHandler { onBack() }

    var trashItems by remember { mutableStateOf(recycleBinManager.listTrashItems()) }
    var filterTab by remember { mutableIntStateOf(0) } // 0=全部 1=视频 2=音频
    var showEmptyConfirm by remember { mutableStateOf(false) }
    var pendingPermDelete by remember { mutableStateOf<TrashItem?>(null) }

    val filtered = remember(trashItems, filterTab) {
        when (filterTab) {
            1 -> trashItems.filter { it.mediaType == TrashMediaType.VIDEO }
            2 -> trashItems.filter { it.mediaType == TrashMediaType.AUDIO }
            else -> trashItems
        }
    }

    Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.arashiSurface(CircleShape)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = tokens.textPrimary)
                }
                Spacer(Modifier.width(10.dp))
                Text("回收站", color = tokens.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (trashItems.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .arashiSurface(RoundedCornerShape(16.dp), elevation = 2.dp)
                            .clickable { showEmptyConfirm = true }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text("清空", color = Color(0xFFFF2442), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("全部", "视频", "音频").forEachIndexed { idx, label ->
                    val selected = idx == filterTab
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .then(if (selected) Modifier.background(tokens.accent) else Modifier.arashiSurface(RoundedCornerShape(18.dp), elevation = 2.dp))
                            .clickable { filterTab = idx }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(label, color = if (selected) Color.White else tokens.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = tokens.textSecondary.copy(alpha = 0.55f), modifier = Modifier.size(44.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("回收站为空", color = tokens.textSecondary, fontSize = 14.sp)
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 140.dp)) {
                    items(filtered, key = { it.id }) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .arashiSurface(RoundedCornerShape(22.dp))
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TrashMediaThumbnail(item = item)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.originalName, color = tokens.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                                Spacer(Modifier.height(3.dp))
                                val dateStr = remember(item.deletedAtMs) {
                                    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(item.deletedAtMs))
                                }
                                Text("${formatFileSize(item.sizeBytes)} · 删除于 $dateStr", color = tokens.textSecondary, fontSize = 11.sp, maxLines = 1)
                            }
                            IconButton(onClick = {
                                val res = recycleBinManager.restoreItem(item)
                                if (res.isSuccess) {
                                    trashItems = recycleBinManager.listTrashItems()
                                    onRefreshMedia()
                                    Toast.makeText(context, "已恢复「${item.originalName}」", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "恢复失败，请检查文件权限", Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                Icon(Icons.Default.RestoreFromTrash, contentDescription = "恢复", tint = tokens.accent)
                            }
                            IconButton(onClick = { pendingPermDelete = item }) {
                                Icon(Icons.Default.DeleteForever, contentDescription = "彻底清理", tint = Color(0xFFFF2442))
                            }
                        }
                    }
                }
            }
        }

        pendingPermDelete?.let { target ->
            AlertDialog(
                onDismissRequest = { pendingPermDelete = null },
                title = { Text("彻底清理", fontWeight = FontWeight.Bold) },
                text = { Text("确定要永久删除「${target.originalName}」吗？删除后将无法恢复。") },
                confirmButton = {
                    TextButton(onClick = {
                        recycleBinManager.permanentlyDelete(target)
                        trashItems = recycleBinManager.listTrashItems()
                        pendingPermDelete = null
                        Toast.makeText(context, "已彻底清理", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("彻底删除", color = Color(0xFFFF2442), fontWeight = FontWeight.SemiBold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingPermDelete = null }) {
                        Text("取消")
                    }
                }
            )
        }

        if (showEmptyConfirm) {
            AlertDialog(
                onDismissRequest = { showEmptyConfirm = false },
                title = { Text("清空回收站", fontWeight = FontWeight.Bold) },
                text = { Text("确定彻底清理回收站中的全部 ${trashItems.size} 项内容吗？此操作不可恢复。") },
                confirmButton = {
                    TextButton(onClick = {
                        recycleBinManager.emptyTrash()
                        trashItems = emptyList()
                        showEmptyConfirm = false
                        Toast.makeText(context, "回收站已清空", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("全部清理", color = Color(0xFFFF2442), fontWeight = FontWeight.SemiBold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEmptyConfirm = false }) {
                        Text("取消")
                    }
                }
            )
        }
    }
}

@Composable
private fun TrashMediaThumbnail(item: TrashItem) {
    val tokens = LocalArashiTokens.current
    val previewBitmap by produceState<Bitmap?>(initialValue = null, item.trashPath) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(item.trashPath)
                val bmp = if (item.mediaType == TrashMediaType.VIDEO) {
                    retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?: retriever.frameAtTime
                } else {
                    retriever.embeddedPicture?.let { bytes -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
                }
                retriever.release()
                bmp
            }.getOrNull()
        }
    }

    Box(
        modifier = Modifier
            .width(if (item.mediaType == TrashMediaType.VIDEO) 92.dp else 58.dp)
            .height(58.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(tokens.accent.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        if (previewBitmap != null) {
            Image(
                bitmap = previewBitmap!!.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                if (item.mediaType == TrashMediaType.VIDEO) Icons.Default.Movie else Icons.Default.MusicNote,
                contentDescription = null,
                tint = tokens.accent
            )
        }
        if (item.mediaType == TrashMediaType.VIDEO) {
            Box(
                modifier = Modifier.size(24.dp).clip(CircleShape).background(Color(0x88000000)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
            if (item.durationMs > 0L) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x88000000))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(formatDuration(item.durationMs), color = Color.White, fontSize = 9.sp)
                }
            }
        }
    }
}
