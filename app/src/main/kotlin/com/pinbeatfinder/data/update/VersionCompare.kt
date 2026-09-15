package com.pinbeatfinder.data.update

/** Compares "v0.9.0"-style tags numerically; non-numeric suffixes are ignored. */
object VersionCompare {
    fun parse(tag: String): List<Int> =
        tag.trim().removePrefix("v").removePrefix("V")
            .split('.')
            .map { part -> part.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }

    /** Positive when [a] is newer than [b]. */
    fun compare(a: String, b: String): Int {
        val pa = parse(a); val pb = parse(b)
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val d = (pa.getOrNull(i) ?: 0) - (pb.getOrNull(i) ?: 0)
            if (d != 0) return d
        }
        return 0
    }

    fun isNewer(candidate: String, current: String): Boolean = compare(candidate, current) > 0
}
