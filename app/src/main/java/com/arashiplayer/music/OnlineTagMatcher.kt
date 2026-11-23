package com.arashiplayer.music

import android.content.Context
import android.util.Base64
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

enum class MusicTagSource(val label: String) {
    NETEASE("网易云"),
    QQ("QQ音乐"),
    KUGOU("酷狗音乐"),
    ITUNES("iTunes")
}

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

    suspend fun searchCandidates(
        keyword: String,
        source: MusicTagSource = MusicTagSource.NETEASE
    ): List<OnlineTagResult> = withContext(Dispatchers.IO) {
        val cleanKeyword = keyword
            .replace(Regex("""\.(mp3|flac|wav|m4a|aac|ogg)$""", RegexOption.IGNORE_CASE), "")
            .replace("<unknown>", "")
            .trim()
        if (cleanKeyword.isBlank()) return@withContext emptyList()

        when (source) {
            MusicTagSource.NETEASE -> searchNetEase(cleanKeyword)
            MusicTagSource.QQ -> searchQQMusic(cleanKeyword)
            MusicTagSource.KUGOU -> searchKugou(cleanKeyword)
            MusicTagSource.ITUNES -> searchItunes(cleanKeyword)
        }
    }

    suspend fun fetchLyricsForCandidate(candidate: OnlineTagResult): OnlineTagResult = withContext(Dispatchers.IO) {
        when (candidate.source) {
            MusicTagSource.NETEASE.label -> {
                val triple = fetchNetEaseMultilingualLyrics(candidate.songId)
                if (!triple.first.isNullOrBlank()) {
                    return@withContext candidate.copy(
                        syncedLyrics = triple.first,
                        translatedLyrics = triple.second,
                        phoneticLyrics = triple.third
                    )
                }
            }
            MusicTagSource.QQ.label -> {
                val pair = fetchQQMusicLyrics(candidate.songId)
                if (!pair.first.isNullOrBlank()) {
                    return@withContext candidate.copy(
                        syncedLyrics = pair.first,
                        translatedLyrics = pair.second
                    )
                }
            }
            MusicTagSource.KUGOU.label -> {
                val lrc = fetchKugouLyrics(candidate.title, candidate.songId)
                if (!lrc.isNullOrBlank()) {
                    return@withContext candidate.copy(syncedLyrics = lrc)
                }
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
                    val songs = JSONObject(resp.body?.string().orEmpty())
                        .optJSONObject("result")?.optJSONArray("songs") ?: return@use
                    for (i in 0 until songs.length()) {
                        val s = songs.getJSONObject(i)
                        val id = s.optLong("id").toString()
                        val name = s.optString("name")
                        val arArr = s.optJSONArray("ar")
                        val artist = if (arArr != null && arArr.length() > 0) arArr.getJSONObject(0).optString("name") else ""
                        val al = s.optJSONObject("al")
                        val album = al?.optString("name") ?: ""
                        val picUrl = (al?.optString("picUrl") ?: "").replace("http://", "https://").ifBlank { null }?.let { "$it?param=600y600" }
                        results.add(OnlineTagResult(id, name, artist, album, picUrl, null, source = MusicTagSource.NETEASE.label))
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

    private fun searchQQMusic(keyword: String): List<OnlineTagResult> {
        val results = mutableListOf<OnlineTagResult>()
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        runCatching {
            val req = Request.Builder()
                .url("https://c.y.qq.com/soso/fcgi-bin/client_search_cp?p=1&n=8&w=$encoded&format=json")
                .header("Referer", "https://y.qq.com/")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val list = JSONObject(resp.body?.string().orEmpty())
                        .optJSONObject("data")?.optJSONObject("song")?.optJSONArray("list") ?: return@use
                    for (i in 0 until list.length()) {
                        val s = list.getJSONObject(i)
                        val mid = s.optString("songmid")
                        val name = s.optString("songname")
                        val singers = s.optJSONArray("singer")
                        val artist = if (singers != null && singers.length() > 0) singers.getJSONObject(0).optString("name") else ""
                        val album = s.optString("albumname")
                        val albumMid = s.optString("albummid")
                        val picUrl = if (albumMid.isNotBlank()) "https://y.gtimg.cn/music/photo_new/T002R500x500M000$albumMid.jpg" else null
                        results.add(OnlineTagResult(mid, name, artist, album, picUrl, null, source = MusicTagSource.QQ.label))
                    }
                }
            }
        }
        return results
    }

    private fun fetchQQMusicLyrics(songMid: String): Pair<String?, String?> {
        return runCatching {
            val req = Request.Builder()
                .url("https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg?songmid=$songMid&format=json&nobase64=1")
                .header("Referer", "https://y.qq.com/")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use Pair(null, null)
                val root = JSONObject(resp.body?.string().orEmpty())
                val orig = root.optString("lyric").ifBlank { null }
                val trans = root.optString("trans").ifBlank { null }
                Pair(orig, trans)
            }
        }.getOrDefault(Pair(null, null))
    }

    private fun searchKugou(keyword: String): List<OnlineTagResult> {
        val results = mutableListOf<OnlineTagResult>()
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        runCatching {
            val req = Request.Builder()
                .url("http://mobilecdn.kugou.com/api/v3/search/song?format=json&keyword=$encoded&page=1&pagesize=8")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val info = JSONObject(resp.body?.string().orEmpty())
                        .optJSONObject("data")?.optJSONArray("info") ?: return@use
                    for (i in 0 until info.length()) {
                        val s = info.getJSONObject(i)
                        val hash = s.optString("hash")
                        val name = s.optString("songname")
                        val artist = s.optString("singername")
                        val album = s.optString("album_name")
                        results.add(OnlineTagResult(hash, name, artist, album, null, null, source = MusicTagSource.KUGOU.label))
                    }
                }
            }
        }
        return results
    }

    private fun fetchKugouLyrics(keyword: String, hash: String): String? {
        return runCatching {
            val encoded = URLEncoder.encode(keyword, "UTF-8")
            val searchReq = Request.Builder()
                .url("http://krcs.kugou.com/search?ver=1&man=yes&client=mobi&keyword=$encoded&hash=$hash")
                .build()
            var id = ""
            var accesskey = ""
            client.newCall(searchReq).execute().use { resp ->
                if (!resp.isSuccessful) return@use
                val arr = JSONObject(resp.body?.string().orEmpty()).optJSONArray("candidates") ?: return@use
                if (arr.length() > 0) {
                    val first = arr.getJSONObject(0)
                    id = first.optString("id")
                    accesskey = first.optString("accesskey")
                }
            }
            if (id.isBlank() || accesskey.isBlank()) return@runCatching null
            val dlReq = Request.Builder()
                .url("http://krcs.kugou.com/download?ver=1&client=pc&id=$id&accesskey=$accesskey&fmt=lrc&charset=utf8")
                .build()
            client.newCall(dlReq).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val b64 = JSONObject(resp.body?.string().orEmpty()).optString("content")
                if (b64.isBlank()) null else String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8)
            }
        }.getOrNull()
    }

    private fun searchItunes(keyword: String): List<OnlineTagResult> {
        val results = mutableListOf<OnlineTagResult>()
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        runCatching {
            val req = Request.Builder().url("https://itunes.apple.com/search?term=$encoded&media=music&limit=6").build()
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
                                source = MusicTagSource.ITUNES.label
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
            val req = Request.Builder().url("https://lrclib.net/api/search?q=$encoded").header("User-Agent", "ArashiPlayer/5.1").build()
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
