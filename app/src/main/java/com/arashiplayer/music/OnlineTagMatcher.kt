package com.arashiplayer.music

import com.arashiplayer.data.OnlineTagResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * 在线封面与歌词匹配器（参考「音乐标签」思路）：
 * 1. 通过 iTunes Search API 获取高分辨率专辑封面 (600x600) 与规范化标签
 * 2. 通过 LRCLIB 开放接口获取时间轴同步 LRC 歌词
 */
class OnlineTagMatcher(private val client: OkHttpClient = OkHttpClient()) {

    suspend fun searchOnlineMetadata(title: String, artist: String): OnlineTagResult? =
        withContext(Dispatchers.IO) {
            val query = URLEncoder.encode("$title $artist".trim(), "UTF-8")
            var coverUrl: String? = null
            var matchedTitle = title
            var matchedArtist = artist
            var matchedAlbum = ""

            // Step 1: Fetch high-res cover from iTunes Search API
            try {
                val itunesReq = Request.Builder()
                    .url("https://itunes.apple.com/search?term=$query&media=music&limit=1")
                    .build()
                client.newCall(itunesReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val root = JSONObject(resp.body?.string().orEmpty())
                        val results = root.optJSONArray("results")
                        if (results != null && results.length() > 0) {
                            val item = results.getJSONObject(0)
                            matchedTitle = item.optString("trackName", title)
                            matchedArtist = item.optString("artistName", artist)
                            matchedAlbum = item.optString("collectionName", "")
                            coverUrl = item.optString("artworkUrl100", "")
                                .replace("100x100bb", "600x600bb")
                                .ifBlank { null }
                        }
                    }
                }
            } catch (_: Exception) {
            }

            // Step 2: Fetch synced LRC lyrics from LRCLIB
            var syncedLyrics: String? = null
            try {
                val lrcReq = Request.Builder()
                    .url("https://lrclib.net/api/search?q=$query")
                    .header("User-Agent", "ArashiPlayer/1.0")
                    .build()
                client.newCall(lrcReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val arr = JSONArray(resp.body?.string().orEmpty())
                        if (arr.length() > 0) {
                            val first = arr.getJSONObject(0)
                            syncedLyrics = first.optString("syncedLyrics")
                                .ifBlank { first.optString("plainLyrics") }
                                .ifBlank { null }
                        }
                    }
                }
            } catch (_: Exception) {
            }

            if (coverUrl == null && syncedLyrics == null) return@withContext null
            OnlineTagResult(
                title = matchedTitle,
                artist = matchedArtist,
                album = matchedAlbum,
                coverUrl = coverUrl,
                syncedLyrics = syncedLyrics
            )
        }
}
