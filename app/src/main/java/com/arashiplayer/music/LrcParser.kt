package com.arashiplayer.music

import com.arashiplayer.data.LyricLine
import kotlin.math.abs

object LrcParser {
    private val lineRegex = Regex("""\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?](.*)""")

    private fun parseRaw(lrcContent: String?): List<Pair<Long, String>> {
        if (lrcContent.isNullOrBlank()) return emptyList()
        val list = mutableListOf<Pair<Long, String>>()
        lrcContent.lineSequence().forEach { raw ->
            val trimmed = raw.trim()
            val matches = lineRegex.findAll(trimmed).toList()
            if (matches.isNotEmpty()) {
                val text = trimmed.substringAfterLast(']').trim()
                if (text.isNotEmpty()) {
                    matches.forEach { m ->
                        val min = m.groupValues[1].toLongOrNull() ?: 0L
                        val sec = m.groupValues[2].toLongOrNull() ?: 0L
                        val msStr = m.groupValues[3]
                        val ms = when (msStr.length) {
                            1 -> (msStr.toLongOrNull() ?: 0L) * 100
                            2 -> (msStr.toLongOrNull() ?: 0L) * 10
                            3 -> msStr.toLongOrNull() ?: 0L
                            else -> 0L
                        }
                        list.add((min * 60_000 + sec * 1_000 + ms) to text)
                    }
                }
            }
        }
        return list.sortedBy { it.first }
    }

    fun parseMultilingual(
        originalLrc: String?,
        translatedLrc: String? = null,
        phoneticLrc: String? = null
    ): List<LyricLine> {
        val orig = parseRaw(originalLrc)
        val trans = parseRaw(translatedLrc)
        val roma = parseRaw(phoneticLrc)

        return orig.map { (ts, text) ->
            val matchedTrans = trans.minByOrNull { abs(it.first - ts) }
                ?.takeIf { abs(it.first - ts) <= 600L }?.second
            val matchedRoma = roma.minByOrNull { abs(it.first - ts) }
                ?.takeIf { abs(it.first - ts) <= 600L }?.second
            LyricLine(ts, text, matchedTrans, matchedRoma)
        }
    }
}
