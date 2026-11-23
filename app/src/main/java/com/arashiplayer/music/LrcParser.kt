package com.arashiplayer.music

import com.arashiplayer.data.LyricLine

object LrcParser {
    private val lineRegex = Regex("""\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?](.*)""")

    fun parse(lrcContent: String?): List<LyricLine> {
        if (lrcContent.isNullOrBlank()) return emptyList()
        val lines = mutableListOf<LyricLine>()
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
                        lines.add(LyricLine(min * 60_000 + sec * 1_000 + ms, text))
                    }
                }
            }
        }
        return lines.sortedBy { it.timestampMs }
    }
}
