package com.arashiplayer.vault

import android.content.Context
import android.media.MediaScannerConnection
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

enum class VaultMediaType { VIDEO, AUDIO, IMAGE }

data class VaultItem(
    val id: String,
    val originalPath: String,
    val encryptedPath: String,
    val originalName: String,
    val mediaType: VaultMediaType,
    val sizeBytes: Long
)

/**
 * 零空间占用私密加密管理器（仿 QQ 影音思路）：
 * - 不复制文件字节、不产生额外存储占用，直接原地重命名修改后缀为 `.arashi_enc` 并加前缀 `.` 隐藏
 * - 在所在目录自动写入 `.nomedia` 防止系统相册及其他媒体软件扫描
 * - 同步移除 Android MediaStore 索引，确保系统相册/音乐库立即隐藏
 * - 支持视频、音频、图片三类文件；解密时原地恢复原文件名并重新触发媒体扫描
 */
class PrivacyVaultManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("arashi_vault_prefs", Context.MODE_PRIVATE)
    private val indexKey = "vault_items_json"
    private val pinHashKey = "vault_pin_sha256"

    fun hasPin(): Boolean = !prefs.getString(pinHashKey, null).isNullOrEmpty()

    fun setPin(pin: String) {
        prefs.edit().putString(pinHashKey, sha256(pin)).apply()
    }

    fun verifyPin(pin: String): Boolean {
        val saved = prefs.getString(pinHashKey, null) ?: return true
        return saved == sha256(pin)
    }

    fun listVaultItems(): List<VaultItem> {
        val raw = prefs.getString(indexKey, "[]") ?: "[]"
        val arr = JSONArray(raw)
        val items = mutableListOf<VaultItem>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val encPath = obj.getString("encryptedPath")
            if (File(encPath).exists()) {
                items.add(
                    VaultItem(
                        id = obj.getString("id"),
                        originalPath = obj.getString("originalPath"),
                        encryptedPath = encPath,
                        originalName = obj.getString("originalName"),
                        mediaType = VaultMediaType.valueOf(obj.getString("mediaType")),
                        sizeBytes = obj.optLong("sizeBytes", 0L)
                    )
                )
            }
        }
        return items
    }

    /**
     * 原地重命名加密（O(1) 速度，零额外空间占用）
     */
    fun encryptInPlace(sourceFile: File, mediaType: VaultMediaType): Result<VaultItem> {
        if (!sourceFile.exists()) return Result.failure(IllegalArgumentException("文件不存在"))
        val parentDir = sourceFile.parentFile ?: return Result.failure(IllegalStateException("无法定位目录"))

        // 1. 写入 .nomedia 确保系统相册与第三方软件不扫描
        val nomedia = File(parentDir, ".nomedia")
        if (!nomedia.exists()) {
            runCatching { nomedia.createNewFile() }
        }

        // 2. 构造隐藏文件名与自定义后缀（原地 renameTo，不拷贝任何字节）
        val id = System.currentTimeMillis().toString(16)
        val hiddenFile = File(parentDir, ".arashi_${id}.arashi_enc")
        val renamed = sourceFile.renameTo(hiddenFile)
        if (!renamed) {
            return Result.failure(SecurityException("重命名失败，请授予「所有文件访问权限」"))
        }

        // 3. 从系统 MediaStore 删除旧记录，使其立即从系统相册/音乐库消失
        removeFromMediaStore(sourceFile.absolutePath, mediaType)

        val item = VaultItem(
            id = id,
            originalPath = sourceFile.absolutePath,
            encryptedPath = hiddenFile.absolutePath,
            originalName = sourceFile.name,
            mediaType = mediaType,
            sizeBytes = hiddenFile.length()
        )
        saveItems(listVaultItems() + item)
        return Result.success(item)
    }

    /**
     * 原地恢复文件名并重新通知系统相册扫描
     */
    fun decryptInPlace(item: VaultItem): Result<File> {
        val encFile = File(item.encryptedPath)
        if (!encFile.exists()) return Result.failure(IllegalArgumentException("加密文件已丢失"))
        val targetFile = File(item.originalPath)
        val renamed = encFile.renameTo(targetFile)
        if (!renamed) {
            return Result.failure(SecurityException("恢复文件失败，请检查存储权限"))
        }

        saveItems(listVaultItems().filterNot { it.id == item.id })
        MediaScannerConnection.scanFile(context, arrayOf(targetFile.absolutePath), null, null)
        return Result.success(targetFile)
    }

    private fun removeFromMediaStore(path: String, type: VaultMediaType) {
        val uri = when (type) {
            VaultMediaType.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            VaultMediaType.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            VaultMediaType.IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        runCatching {
            context.contentResolver.delete(uri, "${MediaStore.MediaColumns.DATA}=?", arrayOf(path))
        }
    }

    private fun saveItems(items: List<VaultItem>) {
        val arr = JSONArray()
        items.forEach {
            arr.put(JSONObject().apply {
                put("id", it.id)
                put("originalPath", it.originalPath)
                put("encryptedPath", it.encryptedPath)
                put("originalName", it.originalName)
                put("mediaType", it.mediaType.name)
                put("sizeBytes", it.sizeBytes)
            })
        }
        prefs.edit().putString(indexKey, arr.toString()).apply()
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest("arashi_salt_$input".toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
