package com.pinbeatfinder.core.util

/** Character range of [query] inside [text] for bold highlighting, or null if it does not occur literally. */
object MatchHighlighter {
    fun range(text: String, query: String): IntRange? {
        val q = query.trim()
        if (q.isEmpty()) return null
        val idx = text.indexOf(q, ignoreCase = true)
        if (idx < 0) return null
        return idx until idx + q.length
    }
}
