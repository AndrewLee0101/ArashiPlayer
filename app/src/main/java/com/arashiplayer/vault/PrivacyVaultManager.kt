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
enum class VaultLockType { NONE, PIN, PATTERN }

data class VaultItem(
    val id: String,
    val originalPath: String,
    val encryptedPath: String,
    val originalName: String,
    val mediaType: VaultMediaType,
    val sizeBytes: Long
)

class PrivacyVaultManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("arashi_vault_v2", Context.MODE_PRIVATE)
    private val indexKey = "vault_items_json"
    private val lockTypeKey = "vault_lock_type"
    private val pinHashKey = "vault_pin_hash"
    private val patternHashKey = "vault_pattern_hash"
    private val biometricKey = "vault_biometric_enabled"

    fun hasAllFilesAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    fun getLockType(): VaultLockType {
        val name = prefs.getString(lockTypeKey, VaultLockType.NONE.name) ?: VaultLockType.NONE.name
        return runCatching { VaultLockType.valueOf(name) }.getOrDefault(VaultLockType.NONE)
    }

    fun isBiometricEnabled(): Boolean = prefs.getBoolean(biometricKey, false)

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(biometricKey, enabled).apply()
    }

    fun setPin(pin: String) {
        prefs.edit()
            .putString(pinHashKey, sha256(pin))
            .putString(lockTypeKey, VaultLockType.PIN.name)
            .apply()
    }

    fun verifyPin(pin: String): Boolean {
        val saved = prefs.getString(pinHashKey, null) ?: return true
        return saved == sha256(pin)
    }

    fun setPattern(nodes: List<Int>) {
        val encoded = nodes.joinToString("-")
        prefs.edit()
            .putString(patternHashKey, sha256(encoded))
            .putString(lockTypeKey, VaultLockType.PATTERN.name)
            .apply()
    }

    fun verifyPattern(nodes: List<Int>): Boolean {
        val saved = prefs.getString(patternHashKey, null) ?: return true
        return saved == sha256(nodes.joinToString("-"))
    }

    fun clearLock() {
        prefs.edit()
            .putString(lockTypeKey, VaultLockType.NONE.name)
            .putBoolean(biometricKey, false)
            .apply()
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
     * 原地重命名改后缀加密（零额外空间占用）
     */
    fun encryptInPlace(sourcePath: String, mediaType: VaultMediaType): Result<VaultItem> {
        val sourceFile = File(sourcePath)
        if (!sourceFile.exists()) return Result.failure(IllegalArgumentException("文件不存在"))
        val parentDir = sourceFile.parentFile ?: return Result.failure(IllegalStateException("目录无效"))

        val nomedia = File(parentDir, ".nomedia")
        if (!nomedia.exists()) runCatching { nomedia.createNewFile() }

        val id = System.currentTimeMillis().toString(16) + "_" + (100..999).random()
        val hiddenFile = File(parentDir, ".arashi_$id.arashi_enc")
        if (!sourceFile.renameTo(hiddenFile)) {
            return Result.failure(SecurityException("PERMISSION_REQUIRED"))
        }

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

    fun decryptInPlace(item: VaultItem): Result<File> {
        val encFile = File(item.encryptedPath)
        if (!encFile.exists()) return Result.failure(IllegalArgumentException("文件不存在"))
        val targetFile = File(item.originalPath)
        if (!encFile.renameTo(targetFile)) {
            return Result.failure(SecurityException("PERMISSION_REQUIRED"))
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
        val digest = MessageDigest.getInstance("SHA-256").digest("arashi_v2_$input".toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
