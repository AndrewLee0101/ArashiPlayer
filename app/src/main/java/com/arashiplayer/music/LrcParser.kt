package com.arashiplayer.music

import com.arashiplayer.data.LyricLine

object LrcParser {
    private val lineRegex = Regex("""\[(\d{2}):(\d{2})(?:\.(\d{2,3}))?](.*)""")

    fun parse(lrcContent: String?): List<LyricLine> {
        if (lrcContent.isNullOrBlank()) return emptyList()
        val lines = mutableListOf<LyricLine>()
        lrcContent.lineSequence().forEach { raw ->
            val match = lineRegex.find(raw.trim())
            if (match != null) {
                val min = match.groupValues[1].toLongOrNull() ?: 0L
                val sec = match.groupValues[2].toLongOrNull() ?: 0L
                val msStr = match.groupValues[3]
                val ms = when (msStr.length) {
                    2 -> (msStr.toLongOrNull() ?: 0L) * 10
                    3 -> msStr.toLongOrNull() ?: 0L
                    else -> 0L
                }
                val text = match.groupValues[4].trim()
                if (text.isNotEmpty()) {
                    lines.add(LyricLine(min * 60_000 + sec * 1_000 + ms, text))
                }
            }
        }
        return lines.sortedBy { it.timestampMs }
    }
}
