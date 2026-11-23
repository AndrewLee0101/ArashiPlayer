package com.arashiplayer.music

import android.content.Context
import com.arashiplayer.data.OnlineTagResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 多源在线封面与歌词匹配器（参考「音乐标签」）：
 * 优先直连网易云音乐公开接口（国内直连快、中/日/英曲库全），
 * 自动兜底 LRCLIB + iTunes，并支持本地持久化缓存。
 */
class OnlineTagMatcher(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val prefs = context.getSharedPreferences("arashi_music_tags", Context.MODE_PRIVATE)

    fun getSavedTag(trackId: Long): OnlineTagResult? {
        val raw = prefs.getString("tag_$trackId", null) ?: return null
        return runCatching {
            val obj = JSONObject(raw)
            OnlineTagResult(
                songId = obj.optString("songId"),
                title = obj.optString("title"),
                artist = obj.optString("artist"),
                album = obj.optString("album"),
                coverUrl = obj.optString("coverUrl").ifBlank { null },
                syncedLyrics = obj.optString("syncedLyrics").ifBlank { null },
                source = obj.optString("source")
            )
        }.getOrNull()
    }

    fun saveTag(trackId: Long, tag: OnlineTagResult) {
        val obj = JSONObject().apply {
            put("songId", tag.songId)
            put("title", tag.title)
            put("artist", tag.artist)
            put("album", tag.album)
            put("coverUrl", tag.coverUrl ?: "")
            put("syncedLyrics", tag.syncedLyrics ?: "")
            put("source", tag.source)
        }
        prefs.edit().putString("tag_$trackId", obj.toString()).apply()
    }

    suspend fun searchCandidates(keyword: String): List<OnlineTagResult> = withContext(Dispatchers.IO) {
        val cleanKeyword = keyword
            .replace(Regex("\.(mp3|flac|wav|m4a|aac|ogg)$", RegexOption.IGNORE_CASE), "")
            .replace("<unknown>", "")
            .trim()
        if (cleanKeyword.isBlank()) return@withContext emptyList()

        val list = mutableListOf<OnlineTagResult>()
        // 1. 网易云音乐公开搜索（国内稳定快速）
        list.addAll(searchNetEase(cleanKeyword))

        // 2. 如果网易云无结果，使用 iTunes + LRCLIB 兜底
        if (list.isEmpty()) {
            list.addAll(searchItunesAndLrclib(cleanKeyword))
        }
        list
    }

    suspend fun fetchLyricsForCandidate(candidate: OnlineTagResult): OnlineTagResult = withContext(Dispatchers.IO) {
        if (!candidate.syncedLyrics.isNullOrBlank()) return@withContext candidate
        if (candidate.source == "网易云" && candidate.songId.isNotBlank()) {
            val lrc = fetchNetEaseLyrics(candidate.songId)
            if (!lrc.isNullOrBlank()) return@withContext candidate.copy(syncedLyrics = lrc)
        }
        val fallbackLrc = fetchLrclib("${candidate.title} ${candidate.artist}")
        candidate.copy(syncedLyrics = fallbackLrc)
    }

    private fun searchNetEase(keyword: String): List<OnlineTagResult> {
        val results = mutableListOf<OnlineTagResult>()
        runCatching {
            val body = FormBody.Builder()
                .add("s", keyword)
                .add("type", "1")
                .add("limit", "8")
                .add("offset", "0")
                .build()
            val req = Request.Builder()
                .url("https://music.163.com/api/cloudsearch/pc")
                .header("Referer", "https://music.163.com/")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .post(body)
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val root = JSONObject(resp.body?.string().orEmpty())
                    val songs = root.optJSONObject("result")?.optJSONArray("songs") ?: return@use
                    for (i in 0 until songs.length()) {
                        val s = songs.getJSONObject(i)
                        val id = s.optLong("id").toString()
                        val name = s.optString("name")
                        val arArr = s.optJSONArray("ar")
                        val artist = if (arArr != null && arArr.length() > 0) {
                            arArr.getJSONObject(0).optString("name")
                        } else ""
                        val al = s.optJSONObject("al")
                        val album = al?.optString("name") ?: ""
                        val rawPic = al?.optString("picUrl") ?: ""
                        val picUrl = rawPic.replace("http://", "https://")
                            .ifBlank { null }
                            ?.let { "$it?param=600y600" }
                        results.add(
                            OnlineTagResult(
                                songId = id,
                                title = name,
                                artist = artist,
                                album = album,
                                coverUrl = picUrl,
                                syncedLyrics = null,
                                source = "网易云"
                            )
                        )
                    }
                }
            }
        }
        return results
    }

    private fun fetchNetEaseLyrics(songId: String): String? {
        return runCatching {
            val req = Request.Builder()
                .url("https://music.163.com/api/song/lyric?id=$songId&lv=1&kv=1&tv=-1")
                .header("Referer", "https://music.163.com/")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val root = JSONObject(resp.body?.string().orEmpty())
                root.optJSONObject("lrc")?.optString("lyric")?.ifBlank { null }
            }
        }.getOrNull()
    }

    private fun searchItunesAndLrclib(keyword: String): List<OnlineTagResult> {
        val results = mutableListOf<OnlineTagResult>()
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        runCatching {
            val req = Request.Builder()
                .url("https://itunes.apple.com/search?term=$encoded&media=music&limit=5")
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val arr = JSONObject(resp.body?.string().orEmpty()).optJSONArray("results") ?: return@use
                    for (i in 0 until arr.length()) {
                        val item = arr.getJSONObject(i)
                        results.add(
                            OnlineTagResult(
                                songId = item.optLong("trackId").toString(),
                                title = item.optString("trackName"),
                                artist = item.optString("artistName"),
                                album = item.optString("collectionName"),
                                coverUrl = item.optString("artworkUrl100")
                                    .replace("100x100bb", "600x600bb")
                                    .ifBlank { null },
                                syncedLyrics = null,
                                source = "iTunes"
                            )
                        )
                    }
                }
            }
        }
        return results
    }

    private fun fetchLrclib(keyword: String): String? {
        val encoded = URLEncoder.encode(keyword.trim(), "UTF-8")
        return runCatching {
            val req = Request.Builder()
                .url("https://lrclib.net/api/search?q=$encoded")
                .header("User-Agent", "ArashiPlayer/2.0")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val arr = JSONArray(resp.body?.string().orEmpty())
                if (arr.length() == 0) return@use null
                val first = arr.getJSONObject(0)
                first.optString("syncedLyrics")
                    .ifBlank { first.optString("plainLyrics") }
                    .ifBlank { null }
            }
        }.getOrNull()
    }
}
