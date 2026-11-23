package com.arashiplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arashiplayer.ui.theme.*
import com.arashiplayer.vault.PrivacyVaultManager
import com.arashiplayer.vault.VaultItem
import com.arashiplayer.vault.VaultMediaType

@Composable
fun VaultScreen(
    vaultManager: PrivacyVaultManager,
    onPlayVaultVideo: (VaultItem) -> Unit
) {
    var unlocked by remember { mutableStateOf(!vaultManager.hasPin()) }
    var pinInput by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var items by remember { mutableStateOf(vaultManager.listVaultItems()) }

    if (!unlocked) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianBg)
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(16.dp))
            Text("私密空间", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("仅修改后缀隐藏，零额外空间占用", color = TextSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = pinInput,
                onValueChange = { pinInput = it },
                label = { Text("输入访问密码") },
                singleLine = true
            )
            errorMsg?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = ApplePink, fontSize = 12.sp)
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = {
                if (vaultManager.verifyPin(pinInput)) {
                    unlocked = true
                    items = vaultManager.listVaultItems()
                } else {
                    errorMsg = "密码错误"
                }
            }) {
                Text("解锁")
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Text("私密空间", color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("视频 / 音频 / 图片原地后缀加密，不显示在系统相册", color = TextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))

        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无加密文件", color = TextSecondary)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(SurfaceCard)
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val icon = when (item.mediaType) {
                            VaultMediaType.VIDEO -> Icons.Default.Movie
                            VaultMediaType.AUDIO -> Icons.Default.MusicNote
                            VaultMediaType.IMAGE -> Icons.Default.Image
                        }
                        Icon(icon, contentDescription = null, tint = AccentCyan)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.originalName, color = TextPrimary, maxLines = 1)
                            Text("${item.mediaType.name} · 零额外空间", color = TextSecondary, fontSize = 12.sp)
                        }
                        if (item.mediaType == VaultMediaType.VIDEO) {
                            IconButton(onClick = { onPlayVaultVideo(item) }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "播放", tint = Color.White)
                            }
                        }
                        IconButton(onClick = {
                            vaultManager.decryptInPlace(item)
                            items = vaultManager.listVaultItems()
                        }) {
                            Icon(Icons.Default.LockOpen, contentDescription = "恢复", tint = AccentViolet)
                        }
                    }
                }
            }
        }
    }
}
