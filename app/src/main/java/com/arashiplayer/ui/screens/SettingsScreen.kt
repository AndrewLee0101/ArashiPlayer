package com.arashiplayer.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arashiplayer.R
import com.arashiplayer.ui.theme.*
import com.arashiplayer.vault.PrivacyVaultManager

private const val AUTHOR_XHS_URL = "https://xhslink.cn/o/8vAvNu748Fw"
private const val AUTHOR_WECHAT = "Naive_guya"
private const val APP_VERSION_NAME = "v5.0.0"

@Composable
fun SettingsScreen(
    themeState: ArashiThemeState,
    onThemeChange: (ArashiThemeState) -> Unit,
    vaultManager: PrivacyVaultManager,
    onRefreshMedia: () -> Unit,
    onOpenHiddenVault: () -> Unit
) {
    val context = LocalContext.current
    val tokens = LocalArashiTokens.current
    var showThemeStudio by remember { mutableStateOf(false) }
    var showRewardQr by remember { mutableStateOf(false) }
    var showVaultLockSettings by remember { mutableStateOf(false) }

    // 连续 3 击版本号开启隐藏隐私空间
    var versionTapCount by remember { mutableIntStateOf(0) }
    var lastVersionTapMs by remember { mutableLongStateOf(0L) }

    if (showThemeStudio) {
        BackHandler { showThemeStudio = false }
        ThemeStudioScreen(
            themeState = themeState,
            onThemeChange = onThemeChange,
            onBack = { showThemeStudio = false }
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            contentPadding = PaddingValues(bottom = 175.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                Text("设置", color = tokens.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }

            // 分组 1：外观主题、隐私解锁设置、存储权限、重新扫描
            item {
                Column(modifier = Modifier.fillMaxWidth().arashiSurface().padding(vertical = 4.dp)) {
                    SettingsRow(
                        icon = Icons.Default.Palette,
                        iconTint = tokens.accent,
                        title = "外观与主题",
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(if (themeState.style == UiStyleMode.MATERIAL_YOU) "Material You" else "液态玻璃 · 可调节", color = tokens.textSecondary, fontSize = 12.sp)
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = tokens.textSecondary)
                            }
                        },
                        onClick = { showThemeStudio = true }
                    )
                    HorizontalDivider(color = tokens.textPrimary.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 18.dp))
                    SettingsRow(
                        icon = Icons.Default.Security,
                        iconTint = tokens.accent,
                        title = "隐私解锁设置",
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("密码 · 手势 · 指纹", color = tokens.textSecondary, fontSize = 12.sp)
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = tokens.textSecondary)
                            }
                        },
                        onClick = { showVaultLockSettings = true }
                    )
                    HorizontalDivider(color = tokens.textPrimary.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 18.dp))
                    SettingsRow(
                        icon = Icons.Default.FolderOpen,
                        iconTint = tokens.accent,
                        title = "存储与权限",
                        trailing = {
                            val granted = vaultManager.hasAllFilesAccess()
                            Text(if (granted) "已开启" else "去开启", color = if (granted) tokens.accent else Color(0xFFFF2442), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        },
                        onClick = { openAllFilesPermission(context) }
                    )
                    HorizontalDivider(color = tokens.textPrimary.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 18.dp))
                    SettingsRow(
                        icon = Icons.Default.Refresh,
                        iconTint = tokens.accent,
                        title = "重新扫描媒体",
                        trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = tokens.textSecondary) },
                        onClick = onRefreshMedia
                    )
                }
            }

            item {
                Text("关于", color = tokens.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 6.dp, top = 2.dp))
            }

            // 分组 2：关于（图标 + 版本号 3 击进隐私空间 + 作者微信 + 红框小红书 + 赞赏作者）
            item {
                Column(modifier = Modifier.fillMaxWidth().arashiSurface().padding(vertical = 8.dp)) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.app_logo_icon),
                            contentDescription = "岚播放器",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(16.dp))
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("岚播放器", color = tokens.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(APP_VERSION_NAME, color = tokens.textSecondary, fontSize = 12.sp)
                    }

                    HorizontalDivider(color = tokens.textPrimary.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 18.dp))

                    // 3 击版本号开启隐私空间
                    SettingsRow(
                        icon = Icons.Default.Info,
                        iconTint = tokens.accent,
                        title = "版本号",
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(APP_VERSION_NAME, color = tokens.textSecondary, fontSize = 14.sp)
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = tokens.textSecondary)
                            }
                        },
                        onClick = {
                            val now = System.currentTimeMillis()
                            if (now - lastVersionTapMs <= 1200L) {
                                versionTapCount += 1
                            } else {
                                versionTapCount = 1
                            }
                            lastVersionTapMs = now
                            if (versionTapCount >= 3) {
                                versionTapCount = 0
                                onOpenHiddenVault()
                            }
                        }
                    )

                    HorizontalDivider(color = tokens.textPrimary.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 18.dp))

                    // 作者微信（点击一键复制）
                    SettingsRow(
                        icon = Icons.Default.PersonOutline,
                        iconTint = Color(0xFF07C160),
                        title = "作者微信",
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(AUTHOR_WECHAT, color = tokens.textSecondary, fontSize = 14.sp)
                                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = tokens.textSecondary, modifier = Modifier.size(16.dp))
                            }
                        },
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("WeChat", AUTHOR_WECHAT))
                            Toast.makeText(context, "已复制微信号: $AUTHOR_WECHAT", Toast.LENGTH_SHORT).show()
                        }
                    )

                    HorizontalDivider(color = tokens.textPrimary.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 18.dp))

                    // 联系作者（红框小红书）
                    SettingsRow(
                        icon = Icons.Default.ChatBubbleOutline,
                        iconTint = Color(0xFFFF2442),
                        title = "联系作者",
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .border(1.5.dp, Color(0xFFFF2442), RoundedCornerShape(6.dp))
                                        .background(Color(0x14FF2442))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("小红书", color = Color(0xFFFF2442), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = tokens.textSecondary)
                            }
                        },
                        onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AUTHOR_XHS_URL))) } }
                    )

                    HorizontalDivider(color = tokens.textPrimary.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 18.dp))

                    // 赞赏作者
                    SettingsRow(
                        icon = Icons.Default.Favorite,
                        iconTint = Color(0xFFD9A326),
                        title = "赞赏作者",
                        trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = tokens.textSecondary) },
                        onClick = { showRewardQr = true }
                    )
                }
            }
        }

        if (showVaultLockSettings) {
            BackHandler { showVaultLockSettings = false }
            VaultSecurityDialog(vaultManager = vaultManager, onDismiss = { showVaultLockSettings = false })
        }

        if (showRewardQr) {
            BackHandler { showRewardQr = false }
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0x99000000)).clickable { showRewardQr = false },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp).arashiSurface(RoundedCornerShape(28.dp))
                        .clickable(enabled = false) {}.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("赞赏作者", color = tokens.textPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(14.dp))
                    Image(
                        painter = painterResource(id = R.drawable.author_reward_qr),
                        contentDescription = "赞赏码",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                    )
                    Spacer(Modifier.height(14.dp))
                    TextButton(onClick = { showRewardQr = false }) {
                        Text("关闭", color = tokens.accent, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    trailing: @Composable () -> Unit,
    onClick: () -> Unit
) {
    val tokens = LocalArashiTokens.current
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 18.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(14.dp))
        Text(title, color = tokens.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun ThemeStudioScreen(
    themeState: ArashiThemeState,
    onThemeChange: (ArashiThemeState) -> Unit,
    onBack: () -> Unit
) {
    val tokens = LocalArashiTokens.current
    var showCustomHue by remember { mutableStateOf(false) }
    var customHue by remember { mutableFloatStateOf(210f) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                IconButton(onClick = onBack, modifier = Modifier.arashiSurface(CircleShape)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = tokens.textPrimary)
                }
                Spacer(Modifier.width(12.dp))
                Text("外观与主题", color = tokens.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
        }

        item {
            Text("界面风格", color = tokens.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StylePreviewCard("液态玻璃", themeState.style == UiStyleMode.LIQUID_GLASS, Brush.linearGradient(listOf(Color(0xFFE3EEF8), Color(0xFFC5DAF0))), Modifier.weight(1f)) {
                    onThemeChange(themeState.copy(style = UiStyleMode.LIQUID_GLASS))
                }
                StylePreviewCard("Material You", themeState.style == UiStyleMode.MATERIAL_YOU, Brush.linearGradient(listOf(Color(0xFFE5ECD8), Color(0xFFD3DFC5))), Modifier.weight(1f)) {
                    onThemeChange(themeState.copy(style = UiStyleMode.MATERIAL_YOU))
                }
            }
        }

        item {
            Text("深浅模式", color = tokens.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth().arashiSurface(RoundedCornerShape(22.dp)).padding(5.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(DarkModePref.LIGHT to "浅色", DarkModePref.DARK to "深色", DarkModePref.SYSTEM to "跟随系统").forEach { (pref, label) ->
                    val active = themeState.darkMode == pref
                    Box(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(18.dp))
                            .background(if (active) tokens.accent else Color.Transparent)
                            .clickable { onThemeChange(themeState.copy(darkMode = pref)) }.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, color = if (active) Color.White else tokens.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        item {
            Text("主题配色", color = tokens.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            Column(modifier = Modifier.fillMaxWidth().arashiSurface().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    ThemePresets.take(4).forEach { preset ->
                        ColorSwatchItem(preset.name, preset.accent, themeState.customAccentArgb == null && themeState.presetId == preset.id) {
                            onThemeChange(themeState.copy(presetId = preset.id, customAccentArgb = null, dynamicMonet = false))
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    ThemePresets.drop(4).forEach { preset ->
                        ColorSwatchItem(preset.name, preset.accent, themeState.customAccentArgb == null && themeState.presetId == preset.id) {
                            onThemeChange(themeState.copy(presetId = preset.id, customAccentArgb = null, dynamicMonet = false))
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier.size(44.dp).clip(CircleShape)
                                .background(Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)))
                                .clickable { showCustomHue = !showCustomHue },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("自定义", color = tokens.textSecondary, fontSize = 12.sp)
                    }
                }
                if (showCustomHue) {
                    val previewColor = Color.hsv(customHue, 0.72f, 0.92f)
                    Slider(
                        value = customHue,
                        onValueChange = {
                            customHue = it
                            onThemeChange(themeState.copy(customAccentArgb = Color.hsv(it, 0.72f, 0.92f).toArgb(), dynamicMonet = false))
                        },
                        valueRange = 0f..360f,
                        colors = SliderDefaults.colors(thumbColor = previewColor, activeTrackColor = previewColor)
                    )
                }
            }
        }

        // 液态玻璃可调节参数（通透度 / 高光描边 / 圆角）+ Material You 动态取色
        item {
            Column(modifier = Modifier.fillMaxWidth().arashiSurface().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("液态玻璃细节调节", color = tokens.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("玻璃通透度", color = tokens.textPrimary, fontSize = 14.sp)
                    Text("${(themeState.glassTransparency * 100).toInt()}%", color = tokens.textSecondary, fontSize = 13.sp)
                }
                Slider(value = themeState.glassTransparency, onValueChange = { onThemeChange(themeState.copy(glassTransparency = it)) }, valueRange = 0.2f..1.0f)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("高光描边强度", color = tokens.textPrimary, fontSize = 14.sp)
                    Text("${(themeState.glassHighlight * 100).toInt()}%", color = tokens.textSecondary, fontSize = 13.sp)
                }
                Slider(value = themeState.glassHighlight, onValueChange = { onThemeChange(themeState.copy(glassHighlight = it)) }, valueRange = 0.1f..1.0f)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("卡片圆角", color = tokens.textPrimary, fontSize = 14.sp)
                    Text("${themeState.cornerRadiusDp.toInt()} dp", color = tokens.textSecondary, fontSize = 13.sp)
                }
                Slider(value = themeState.cornerRadiusDp, onValueChange = { onThemeChange(themeState.copy(cornerRadiusDp = it)) }, valueRange = 12f..32f)

                HorizontalDivider(color = tokens.textPrimary.copy(alpha = 0.08f))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("动态取色 (Material You)", color = tokens.textPrimary, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Switch(
                        checked = themeState.dynamicMonet,
                        onCheckedChange = { onThemeChange(themeState.copy(dynamicMonet = it, style = if (it) UiStyleMode.MATERIAL_YOU else themeState.style)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun StylePreviewCard(title: String, selected: Boolean, previewBrush: Brush, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val tokens = LocalArashiTokens.current
    Column(
        modifier = modifier.arashiSurface(RoundedCornerShape(22.dp))
            .then(if (selected) Modifier.border(2.dp, tokens.accent, RoundedCornerShape(22.dp)) else Modifier)
            .clickable { onClick() }.padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(16.dp)).background(previewBrush))
        Spacer(Modifier.height(8.dp))
        Text(title, color = tokens.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
private fun ColorSwatchItem(name: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    val tokens = LocalArashiTokens.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape)
                .then(if (selected) Modifier.border(3.dp, tokens.textPrimary, CircleShape) else Modifier)
                .padding(3.dp).clip(CircleShape).background(color).clickable { onClick() }
        )
        Spacer(Modifier.height(4.dp))
        Text(name, color = tokens.textSecondary, fontSize = 12.sp)
    }
}
