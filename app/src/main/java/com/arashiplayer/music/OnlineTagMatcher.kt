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

class OnlineTagMatcher(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val prefs = context.getSharedPreferences("arashi_music_tags_v5", Context.MODE_PRIVATE)

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
                translatedLyrics = obj.optString("translatedLyrics").ifBlank { null },
                phoneticLyrics = obj.optString("phoneticLyrics").ifBlank { null },
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
            put("translatedLyrics", tag.translatedLyrics ?: "")
            put("phoneticLyrics", tag.phoneticLyrics ?: "")
            put("source", tag.source)
        }
        prefs.edit().putString("tag_$trackId", obj.toString()).apply()
    }

    suspend fun searchCandidates(keyword: String): List<OnlineTagResult> = withContext(Dispatchers.IO) {
        val cleanKeyword = keyword
            .replace(Regex("""\.(mp3|flac|wav|m4a|aac|ogg)$""", RegexOption.IGNORE_CASE), "")
            .replace("<unknown>", "")
            .trim()
        if (cleanKeyword.isBlank()) return@withContext emptyList()

        val list = mutableListOf<OnlineTagResult>()
        list.addAll(searchNetEase(cleanKeyword))
        if (list.isEmpty()) list.addAll(searchItunes(cleanKeyword))
        list
    }

    suspend fun fetchLyricsForCandidate(candidate: OnlineTagResult): OnlineTagResult = withContext(Dispatchers.IO) {
        if (candidate.source == "网易云" && candidate.songId.isNotBlank()) {
            val triple = fetchNetEaseMultilingualLyrics(candidate.songId)
            if (!triple.first.isNullOrBlank()) {
                return@withContext candidate.copy(
                    syncedLyrics = triple.first,
                    translatedLyrics = triple.second,
                    phoneticLyrics = triple.third
                )
            }
        }
        val fallbackLrc = fetchLrclib("${candidate.title} ${candidate.artist}")
        candidate.copy(syncedLyrics = fallbackLrc)
    }

    private fun searchNetEase(keyword: String): List<OnlineTagResult> {
        val results = mutableListOf<OnlineTagResult>()
        runCatching {
            val body = FormBody.Builder()
                .add("s", keyword).add("type", "1").add("limit", "8").add("offset", "0").build()
            val req = Request.Builder()
                .url("https://music.163.com/api/cloudsearch/pc")
                .header("Referer", "https://music.163.com/")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .post(body).build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val root = JSONObject(resp.body?.string().orEmpty())
                    val songs = root.optJSONObject("result")?.optJSONArray("songs") ?: return@use
                    for (i in 0 until songs.length()) {
                        val s = songs.getJSONObject(i)
                        val id = s.optLong("id").toString()
                        val name = s.optString("name")
                        val arArr = s.optJSONArray("ar")
                        val artist = if (arArr != null && arArr.length() > 0) arArr.getJSONObject(0).optString("name") else ""
                        val al = s.optJSONObject("al")
                        val album = al?.optString("name") ?: ""
                        val picUrl = (al?.optString("picUrl") ?: "").replace("http://", "https://").ifBlank { null }?.let { "$it?param=600y600" }
                        results.add(OnlineTagResult(id, name, artist, album, picUrl, null, source = "网易云"))
                    }
                }
            }
        }
        return results
    }

    private fun fetchNetEaseMultilingualLyrics(songId: String): Triple<String?, String?, String?> {
        return runCatching {
            val req = Request.Builder()
                .url("https://music.163.com/api/song/lyric?id=$songId&lv=1&kv=1&tv=-1&rv=1")
                .header("Referer", "https://music.163.com/")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use Triple(null, null, null)
                val root = JSONObject(resp.body?.string().orEmpty())
                Triple(
                    root.optJSONObject("lrc")?.optString("lyric")?.ifBlank { null },
                    root.optJSONObject("tlyric")?.optString("lyric")?.ifBlank { null },
                    root.optJSONObject("romalrc")?.optString("lyric")?.ifBlank { null }
                )
            }
        }.getOrDefault(Triple(null, null, null))
    }

    private fun searchItunes(keyword: String): List<OnlineTagResult> {
        val results = mutableListOf<OnlineTagResult>()
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        runCatching {
            val req = Request.Builder().url("https://itunes.apple.com/search?term=$encoded&media=music&limit=5").build()
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
                                coverUrl = item.optString("artworkUrl100").replace("100x100bb", "600x600bb").ifBlank { null },
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
            val req = Request.Builder().url("https://lrclib.net/api/search?q=$encoded").header("User-Agent", "ArashiPlayer/5.0").build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val arr = JSONArray(resp.body?.string().orEmpty())
                if (arr.length() == 0) return@use null
                val first = arr.getJSONObject(0)
                first.optString("syncedLyrics").ifBlank { first.optString("plainLyrics") }.ifBlank { null }
            }
        }.getOrNull()
    }
}
