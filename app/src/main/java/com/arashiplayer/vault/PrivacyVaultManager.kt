package com.arashiplayer.vault

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
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
 * 隐私空间管理器（v5.0）：
 * - 数字密码 (PIN)、九宫格手势 (Pattern)、指纹 (Biometric) 三种解锁方式完全独立、互不干扰，
 *   可同时开启多种，解锁时任选其中任意一种通过即可直接进入！
 */
class PrivacyVaultManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("arashi_vault_v5", Context.MODE_PRIVATE)
    private val indexKey = "vault_items_json"
    private val pinEnabledKey = "pin_enabled"
    private val pinHashKey = "pin_hash"
    private val patternEnabledKey = "pattern_enabled"
    private val patternHashKey = "pattern_hash"
    private val biometricEnabledKey = "biometric_enabled"

    fun hasAllFilesAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else true

    fun isPinEnabled(): Boolean = prefs.getBoolean(pinEnabledKey, false) && !prefs.getString(pinHashKey, null).isNullOrEmpty()
    fun isPatternEnabled(): Boolean = prefs.getBoolean(patternEnabledKey, false) && !prefs.getString(patternHashKey, null).isNullOrEmpty()
    fun isBiometricEnabled(): Boolean = prefs.getBoolean(biometricEnabledKey, false)

    fun hasAnyLockEnabled(): Boolean = isPinEnabled() || isPatternEnabled() || isBiometricEnabled()

    fun setPin(pin: String) {
        prefs.edit().putString(pinHashKey, sha256(pin)).putBoolean(pinEnabledKey, true).apply()
    }

    fun disablePin() {
        prefs.edit().putBoolean(pinEnabledKey, false).apply()
    }

    fun verifyPin(pin: String): Boolean {
        val saved = prefs.getString(pinHashKey, null) ?: return false
        return saved == sha256(pin)
    }

    fun setPattern(nodes: List<Int>) {
        prefs.edit().putString(patternHashKey, sha256(nodes.joinToString("-"))).putBoolean(patternEnabledKey, true).apply()
    }

    fun disablePattern() {
        prefs.edit().putBoolean(patternEnabledKey, false).apply()
    }

    fun verifyPattern(nodes: List<Int>): Boolean {
        val saved = prefs.getString(patternHashKey, null) ?: return false
        return saved == sha256(nodes.joinToString("-"))
    }

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(biometricEnabledKey, enabled).apply()
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

    fun encryptInPlace(sourcePath: String, mediaType: VaultMediaType): Result<VaultItem> {
        val sourceFile = File(sourcePath)
        if (!sourceFile.exists()) return Result.failure(IllegalArgumentException("FILE_NOT_FOUND"))
        val parentDir = sourceFile.parentFile ?: return Result.failure(IllegalStateException("NO_PARENT"))

        val nomedia = File(parentDir, ".nomedia")
        if (!nomedia.exists()) runCatching { nomedia.createNewFile() }

        val id = System.currentTimeMillis().toString(16) + "_" + (100..999).random()
        val hiddenFile = File(parentDir, ".arashi_$id.arashi_enc")
        if (!sourceFile.renameTo(hiddenFile)) return Result.failure(SecurityException("PERMISSION_REQUIRED"))

        removeFromMediaStore(sourceFile.absolutePath, mediaType)

        val item = VaultItem(id, sourceFile.absolutePath, hiddenFile.absolutePath, sourceFile.name, mediaType, hiddenFile.length())
        saveItems(listVaultItems() + item)
        return Result.success(item)
    }

    fun decryptInPlace(item: VaultItem): Result<File> {
        val encFile = File(item.encryptedPath)
        if (!encFile.exists()) return Result.failure(IllegalArgumentException("FILE_NOT_FOUND"))
        val targetFile = File(item.originalPath)
        if (!encFile.renameTo(targetFile)) return Result.failure(SecurityException("PERMISSION_REQUIRED"))
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
        runCatching { context.contentResolver.delete(uri, "${MediaStore.MediaColumns.DATA}=?", arrayOf(path)) }
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
        val digest = MessageDigest.getInstance("SHA-256").digest("arashi_v5_$input".toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
