package com.arche.threply.ime.pinyin

import com.arche.threply.ime.rime.RimeFallbackLexicon

class PinyinComposer {
    private val rawBuffer = StringBuilder()

    fun push(ch: Char) {
        if (ch in 'a'..'z') {
            rawBuffer.append(ch)
        }
    }

    fun pop() {
        if (rawBuffer.isNotEmpty()) {
            rawBuffer.deleteCharAt(rawBuffer.lastIndex)
        }
    }

    fun clear() {
        rawBuffer.clear()
    }

    fun currentRaw(): String = rawBuffer.toString()

    fun candidates(page: Int = 0): List<String> {
        val raw = currentRaw()
        if (raw.isBlank()) return emptyList()
        val results = RimeFallbackLexicon.lookup(raw, limit = 8, page = page)
        return results.ifEmpty { listOf(raw) }
    }
}
