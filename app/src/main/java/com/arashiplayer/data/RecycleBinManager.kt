package com.arashiplayer.data

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class TrashMediaType { VIDEO, AUDIO }

data class TrashItem(
    val id: String,
    val originalPath: String,
    val trashPath: String,
    val originalName: String,
    val mediaType: TrashMediaType,
    val durationMs: Long,
    val sizeBytes: Long,
    val deletedAtMs: Long
)

/**
 * 回收站管理器（v6.0）：
 * - 软删除：将媒体文件移入同目录隐藏隔离区 `.arashi_trash/`（内含 `.nomedia`）并从 MediaStore 移除
 * - 恢复：将文件还原至原路径并触发 MediaScanner 重新索引
 * - 彻底清理 / 清空：永久删除磁盘文件与索引记录
 */
class RecycleBinManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("arashi_recycle_bin_v6", Context.MODE_PRIVATE)
    private val indexKey = "trash_items_json"

    fun listTrashItems(): List<TrashItem> {
        val raw = prefs.getString(indexKey, "[]") ?: "[]"
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        val items = mutableListOf<TrashItem>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val trashPath = obj.optString("trashPath")
            if (trashPath.isNotEmpty() && File(trashPath).exists()) {
                val type = runCatching { TrashMediaType.valueOf(obj.optString("mediaType")) }
                    .getOrDefault(TrashMediaType.VIDEO)
                items.add(
                    TrashItem(
                        id = obj.optString("id"),
                        originalPath = obj.optString("originalPath"),
                        trashPath = trashPath,
                        originalName = obj.optString("originalName"),
                        mediaType = type,
                        durationMs = obj.optLong("durationMs", 0L),
                        sizeBytes = obj.optLong("sizeBytes", 0L),
                        deletedAtMs = obj.optLong("deletedAtMs", 0L)
                    )
                )
            }
        }
        return items.sortedByDescending { it.deletedAtMs }
    }

    fun moveToTrash(
        sourcePath: String,
        sourceUri: Uri?,
        originalName: String,
        mediaType: TrashMediaType,
        durationMs: Long,
        sizeBytes: Long
    ): Result<TrashItem> {
        val sourceFile = File(sourcePath)
        if (!sourceFile.exists()) return Result.failure(IllegalArgumentException("FILE_NOT_FOUND"))
        val parentDir = sourceFile.parentFile ?: return Result.failure(IllegalStateException("NO_PARENT"))

        val trashDir = File(parentDir, ".arashi_trash")
        if (!trashDir.exists() && !trashDir.mkdirs()) {
            return Result.failure(SecurityException("MKDIR_FAILED"))
        }
        val nomedia = File(trashDir, ".nomedia")
        if (!nomedia.exists()) runCatching { nomedia.createNewFile() }

        val id = System.currentTimeMillis().toString(16) + "_" + (100..999).random()
        val dstFile = File(trashDir, "${id}_${sourceFile.name}")
        if (!sourceFile.renameTo(dstFile)) {
            return Result.failure(SecurityException("PERMISSION_REQUIRED"))
        }

        if (sourceUri != null) {
            runCatching { context.contentResolver.delete(sourceUri, null, null) }
        } else {
            removeFromMediaStore(sourceFile.absolutePath, mediaType)
        }

        val item = TrashItem(
            id = id,
            originalPath = sourceFile.absolutePath,
            trashPath = dstFile.absolutePath,
            originalName = originalName.ifBlank { sourceFile.name },
            mediaType = mediaType,
            durationMs = durationMs,
            sizeBytes = if (sizeBytes > 0L) sizeBytes else dstFile.length(),
            deletedAtMs = System.currentTimeMillis()
        )
        saveItems(listTrashItems() + item)
        return Result.success(item)
    }

    fun restoreItem(item: TrashItem): Result<File> {
        val trashFile = File(item.trashPath)
        if (!trashFile.exists()) {
            saveItems(listTrashItems().filterNot { it.id == item.id })
            return Result.failure(IllegalArgumentException("FILE_NOT_FOUND"))
        }
        var targetFile = File(item.originalPath)
        targetFile.parentFile?.let { if (!it.exists()) it.mkdirs() }
        if (targetFile.exists()) {
            val base = targetFile.nameWithoutExtension
            val ext = targetFile.extension
            val suffix = if (ext.isNotEmpty()) "${base}_restored.$ext" else "${base}_restored"
            targetFile = File(targetFile.parentFile, suffix)
        }
        if (!trashFile.renameTo(targetFile)) {
            return Result.failure(SecurityException("PERMISSION_REQUIRED"))
        }
        saveItems(listTrashItems().filterNot { it.id == item.id })
        MediaScannerConnection.scanFile(context, arrayOf(targetFile.absolutePath), null, null)
        return Result.success(targetFile)
    }

    fun permanentlyDelete(item: TrashItem): Boolean {
        val file = File(item.trashPath)
        val ok = if (file.exists()) runCatching { file.delete() }.getOrDefault(false) else true
        if (ok) {
            saveItems(listTrashItems().filterNot { it.id == item.id })
        }
        return ok
    }

    fun emptyTrash(): Int {
        val items = listTrashItems()
        var count = 0
        items.forEach { item ->
            val f = File(item.trashPath)
            if (!f.exists() || runCatching { f.delete() }.getOrDefault(false)) {
                count++
            }
        }
        saveItems(emptyList())
        return count
    }

    private fun removeFromMediaStore(path: String, type: TrashMediaType) {
        val uri = when (type) {
            TrashMediaType.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            TrashMediaType.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        runCatching { context.contentResolver.delete(uri, "${MediaStore.MediaColumns.DATA}=?", arrayOf(path)) }
    }

    private fun saveItems(items: List<TrashItem>) {
        val arr = JSONArray()
        items.forEach {
            arr.put(JSONObject().apply {
                put("id", it.id)
                put("originalPath", it.originalPath)
                put("trashPath", it.trashPath)
                put("originalName", it.originalName)
                put("mediaType", it.mediaType.name)
                put("durationMs", it.durationMs)
                put("sizeBytes", it.sizeBytes)
                put("deletedAtMs", it.deletedAtMs)
            })
        }
        prefs.edit().putString(indexKey, arr.toString()).apply()
    }
}
