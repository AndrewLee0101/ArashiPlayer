package com.arashiplayer.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import coil.compose.AsyncImage
import com.arashiplayer.data.LocalAudio
import com.arashiplayer.data.LocalImage
import com.arashiplayer.data.LocalVideo
import com.arashiplayer.data.formatFileSize
import com.arashiplayer.ui.theme.*
import com.arashiplayer.vault.*
import java.io.File

@Composable
fun VaultScreen(
    vaultManager: PrivacyVaultManager,
    allVideos: List<LocalVideo>,
    allAudios: List<LocalAudio>,
    allImages: List<LocalImage>,
    onRefreshMedia: () -> Unit,
    onPlayVaultVideo: (VaultItem) -> Unit,
    onPlayVaultAudio: (VaultItem) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val tokens = LocalArashiTokens.current

    var lockType by remember { mutableStateOf(vaultManager.getLockType()) }
    var unlocked by remember { mutableStateOf(lockType == VaultLockType.NONE) }
    var pinInput by remember { mutableStateOf("") }
    var authError by remember { mutableStateOf<String?>(null) }

    var items by remember { mutableStateOf(vaultManager.listVaultItems()) }
    var filterTab by remember { mutableIntStateOf(0) }
    var showSecuritySheet by remember { mutableStateOf(false) }
    var showImportSheet by remember { mutableStateOf(false) }
    var viewingImage by remember { mutableStateOf<VaultItem?>(null) }

    LaunchedEffect(unlocked) {
        if (!unlocked && vaultManager.isBiometricEnabled() && activity != null) {
            triggerBiometric(activity) {
                unlocked = true
                items = vaultManager.listVaultItems()
            }
        }
    }

    if (!unlocked) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(68.dp).arashiSurface(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = tokens.accent, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text("私密空间", color = tokens.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(22.dp))

            if (lockType == VaultLockType.PATTERN) {
                PatternLockGrid(
                    onPatternComplete = { nodes ->
                        if (vaultManager.verifyPattern(nodes)) {
                            unlocked = true
                            authError = null
                            items = vaultManager.listVaultItems()
                        } else {
                            authError = "手势错误"
                        }
                    }
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(4) { i ->
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(if (i < pinInput.length) tokens.accent else tokens.textPrimary.copy(alpha = 0.22f))
                        )
                    }
                }
                Spacer(Modifier.height(22.dp))
                PinKeypad(
                    onDigit = { d ->
                        if (pinInput.length < 4) {
                            pinInput += d
                            if (pinInput.length == 4) {
                                if (vaultManager.verifyPin(pinInput)) {
                                    unlocked = true
                                    pinInput = ""
                                    authError = null
                                    items = vaultManager.listVaultItems()
                                } else {
                                    authError = "密码错误"
                                    pinInput = ""
                                }
                            }
                        }
                    },
                    onBackspace = { if (pinInput.isNotEmpty()) pinInput = pinInput.dropLast(1) }
                )
            }

            authError?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = Color(0xFFFF2442), fontSize = 13.sp)
            }

            if (vaultManager.isBiometricEnabled() && activity != null) {
                Spacer(Modifier.height(18.dp))
                IconButton(
                    onClick = {
                        triggerBiometric(activity) {
                            unlocked = true
                            items = vaultManager.listVaultItems()
                        }
                    },
                    modifier = Modifier.size(54.dp).arashiSurface(CircleShape)
                ) {
                    Icon(Icons.Default.Fingerprint, contentDescription = null, tint = tokens.accent, modifier = Modifier.size(30.dp))
                }
            }
        }
        return
    }

    val filtered = remember(items, filterTab) {
        when (filterTab) {
            1 -> items.filter { it.mediaType == VaultMediaType.VIDEO }
            2 -> items.filter { it.mediaType == VaultMediaType.AUDIO }
            3 -> items.filter { it.mediaType == VaultMediaType.IMAGE }
            else -> items
        }
    }

    Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("私密空间", color = tokens.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(
                    onClick = {
                        if (!vaultManager.hasAllFilesAccess()) openAllFilesPermission(context)
                        else showImportSheet = true
                    },
                    modifier = Modifier.arashiSurface(CircleShape)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = tokens.accent)
                }
                Spacer(Modifier.width(10.dp))
                IconButton(
                    onClick = { showSecuritySheet = true },
                    modifier = Modifier.arashiSurface(CircleShape)
                ) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = tokens.textPrimary)
                }
            }

            val tabs = listOf("全部", "视频", "音频", "图片")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                tabs.forEachIndexed { idx, label ->
                    val selected = idx == filterTab
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .then(
                                if (selected) Modifier.background(tokens.accent)
                                else Modifier.arashiSurface(RoundedCornerShape(18.dp), elevation = 2.dp)
                            )
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
                    Text("空", color = tokens.textSecondary)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 160.dp)
                ) {
                    items(filtered, key = { it.id }) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .arashiSurface(RoundedCornerShape(22.dp))
                                .clickable {
                                    when (item.mediaType) {
                                        VaultMediaType.VIDEO -> onPlayVaultVideo(item)
                                        VaultMediaType.AUDIO -> onPlayVaultAudio(item)
                                        VaultMediaType.IMAGE -> viewingImage = item
                                    }
                                }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (item.mediaType == VaultMediaType.IMAGE) {
                                AsyncImage(
                                    model = File(item.encryptedPath),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                                )
                            } else {
                                Box(
                                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(tokens.accent.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        if (item.mediaType == VaultMediaType.VIDEO) Icons.Default.Movie else Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = tokens.accent
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.originalName, color = tokens.textPrimary, fontWeight = FontWeight.Medium, maxLines = 1)
                                Text(formatFileSize(item.sizeBytes), color = tokens.textSecondary, fontSize = 12.sp)
                            }
                            IconButton(onClick = {
                                if (!vaultManager.hasAllFilesAccess()) {
                                    openAllFilesPermission(context)
                                } else {
                                    vaultManager.decryptInPlace(item)
                                    items = vaultManager.listVaultItems()
                                    onRefreshMedia()
                                }
                            }) {
                                Icon(Icons.Default.LockOpen, contentDescription = null, tint = tokens.accent)
                            }
                        }
                    }
                }
            }
        }

        viewingImage?.let { img ->
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black).clickable { viewingImage = null },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = File(img.encryptedPath),
                    contentDescription = img.originalName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        if (showImportSheet) {
            VaultImportSheet(
                videos = allVideos,
                audios = allAudios,
                images = allImages,
                onEncrypt = { path, type ->
                    val res = vaultManager.encryptInPlace(path, type)
                    if (res.isFailure) openAllFilesPermission(context)
                    else {
                        items = vaultManager.listVaultItems()
                        onRefreshMedia()
                    }
                },
                onDismiss = { showImportSheet = false }
            )
        }

        if (showSecuritySheet) {
            VaultSecurityDialog(
                vaultManager = vaultManager,
                onUpdated = { lockType = vaultManager.getLockType() },
                onDismiss = { showSecuritySheet = false }
            )
        }
    }
}

@Composable
private fun VaultImportSheet(
    videos: List<LocalVideo>,
    audios: List<LocalAudio>,
    images: List<LocalImage>,
    onEncrypt: (String, VaultMediaType) -> Unit,
    onDismiss: () -> Unit
) {
    val tokens = LocalArashiTokens.current
    var tab by remember { mutableIntStateOf(0) }
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0x88000000)).clickable { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(480.dp)
                .padding(12.dp)
                .arashiSurface(RoundedCornerShape(28.dp))
                .clickable(enabled = false) {}
                .padding(18.dp)
        ) {
            Text("添加到加密空间", color = tokens.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("视频", "图片", "音频").forEachIndexed { idx, label ->
                    Text(
                        text = label,
                        color = if (tab == idx) Color.White else tokens.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (tab == idx) tokens.accent else tokens.textPrimary.copy(alpha = 0.08f))
                            .clickable { tab = idx }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            when (tab) {
                0 -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(videos) { v ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .arashiSurface(RoundedCornerShape(14.dp), elevation = 2.dp)
                                .clickable { onEncrypt(v.path, VaultMediaType.VIDEO); onDismiss() }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Movie, contentDescription = null, tint = tokens.accent)
                            Spacer(Modifier.width(10.dp))
                            Text(v.title, color = tokens.textPrimary, maxLines = 1, modifier = Modifier.weight(1f))
                        }
                    }
                }
                1 -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(images) { img ->
                        AsyncImage(
                            model = img.uri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onEncrypt(img.path, VaultMediaType.IMAGE); onDismiss() }
                        )
                    }
                }
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(audios) { a ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .arashiSurface(RoundedCornerShape(14.dp), elevation = 2.dp)
                                .clickable { onEncrypt(a.path, VaultMediaType.AUDIO); onDismiss() }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.MusicNote, contentDescription = null, tint = tokens.accent)
                            Spacer(Modifier.width(10.dp))
                            Text(a.title, color = tokens.textPrimary, maxLines = 1, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VaultSecurityDialog(
    vaultManager: PrivacyVaultManager,
    onUpdated: () -> Unit,
    onDismiss: () -> Unit
) {
    val tokens = LocalArashiTokens.current
    var mode by remember { mutableStateOf("menu") }
    var newPin by remember { mutableStateOf("") }
    var bioEnabled by remember { mutableStateOf(vaultManager.isBiometricEnabled()) }

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0x88000000)).clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .arashiSurface(RoundedCornerShape(28.dp))
                .clickable(enabled = false) {}
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (mode) {
                "pin" -> {
                    Text("设置 4 位密码", color = tokens.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        repeat(4) { i ->
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(if (i < newPin.length) tokens.accent else tokens.textPrimary.copy(alpha = 0.22f))
                            )
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    PinKeypad(
                        onDigit = { d ->
                            if (newPin.length < 4) {
                                newPin += d
                                if (newPin.length == 4) {
                                    vaultManager.setPin(newPin)
                                    onUpdated()
                                    onDismiss()
                                }
                            }
                        },
                        onBackspace = { if (newPin.isNotEmpty()) newPin = newPin.dropLast(1) }
                    )
                }
                "pattern" -> {
                    Text("设置手势解锁", color = tokens.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    PatternLockGrid(
                        onPatternComplete = { nodes ->
                            vaultManager.setPattern(nodes)
                            onUpdated()
                            onDismiss()
                        }
                    )
                }
                else -> {
                    Text("解锁方式", color = tokens.textPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    SecurityRow(Icons.Default.Pin, "数字密码") { mode = "pin" }
                    Spacer(Modifier.height(10.dp))
                    SecurityRow(Icons.Default.Pattern, "手势解锁") { mode = "pattern" }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .arashiSurface(RoundedCornerShape(16.dp), elevation = 2.dp)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null, tint = tokens.accent)
                        Spacer(Modifier.width(12.dp))
                        Text("指纹解锁", color = tokens.textPrimary, modifier = Modifier.weight(1f))
                        Switch(
                            checked = bioEnabled,
                            onCheckedChange = {
                                bioEnabled = it
                                vaultManager.setBiometricEnabled(it)
                                onUpdated()
                            }
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    SecurityRow(Icons.Default.LockOpen, "关闭密码锁") {
                        vaultManager.clearLock()
                        onUpdated()
                        onDismiss()
                    }
                }
            }
        }
    }
}

@Composable
private fun SecurityRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, onClick: () -> Unit) {
    val tokens = LocalArashiTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .arashiSurface(RoundedCornerShape(16.dp), elevation = 2.dp)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tokens.accent)
        Spacer(Modifier.width(12.dp))
        Text(title, color = tokens.textPrimary, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PinKeypad(onDigit: (String) -> Unit, onBackspace: () -> Unit) {
    val tokens = LocalArashiTokens.current
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "⌫")
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                row.forEach { key ->
                    if (key.isEmpty()) {
                        Spacer(Modifier.size(64.dp))
                    } else {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .arashiSurface(CircleShape, elevation = 3.dp)
                                .clickable { if (key == "⌫") onBackspace() else onDigit(key) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(key, color = tokens.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

private fun triggerBiometric(activity: FragmentActivity, onSuccess: () -> Unit) {
    val bm = BiometricManager.from(activity)
    if (bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) != BiometricManager.BIOMETRIC_SUCCESS) return
    val executor = ContextCompat.getMainExecutor(activity)
    val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            onSuccess()
        }
    })
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle("指纹解锁")
        .setNegativeButtonText("取消")
        .build()
    prompt.authenticate(info)
}

fun openAllFilesPermission(context: android.content.Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        runCatching {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
