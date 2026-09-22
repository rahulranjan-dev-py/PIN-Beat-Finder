package com.pinbeatfinder.data.update

/**
 * Compares "v0.9.0"-style tags numerically. A pre-release suffix ("v0.9.0-rc1", "v0.9.0-beta.2")
 * sorts before the final version with the same numbers, and pre-releases of the same version
 * compare by their suffix (numeric parts numerically). Build metadata after "+" is ignored.
 */
object VersionCompare {
    private val SUFFIX_TOKEN = Regex("[0-9]+|[a-z]+")
    fun parse(tag: String): List<Int> =
        numbersOf(tag).split('.')
            .map { part -> part.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }

    /** Positive when [a] is newer than [b]. */
    fun compare(a: String, b: String): Int {
        val pa = parse(a); val pb = parse(b)
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val d = (pa.getOrNull(i) ?: 0) - (pb.getOrNull(i) ?: 0)
            if (d != 0) return d
        }
        val sa = suffixOf(a); val sb = suffixOf(b)
        return when {
            sa == sb -> 0
            sa.isEmpty() -> 1   // final release beats any pre-release of the same version
            sb.isEmpty() -> -1
            else -> compareSuffix(sa, sb)
        }
    }

    private fun core(tag: String): String = tag.trim().removePrefix("v").removePrefix("V").substringBefore('+')

    private fun numbersOf(tag: String): String = core(tag).substringBefore('-')

    private fun suffixOf(tag: String): String = core(tag).substringAfter('-', "").lowercase()

    private fun compareSuffix(a: String, b: String): Int {
        // "rc10" is ["rc", "10"], so rc10 > rc9.
        val pa = SUFFIX_TOKEN.findAll(a).map { it.value }.toList()
        val pb = SUFFIX_TOKEN.findAll(b).map { it.value }.toList()
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrNull(i) ?: return -1
            val y = pb.getOrNull(i) ?: return 1
            val xi = x.toIntOrNull(); val yi = y.toIntOrNull()
            val d = if (xi != null && yi != null) xi.compareTo(yi) else x.compareTo(y)
            if (d != 0) return d
        }
        return 0
    }

    fun isNewer(candidate: String, current: String): Boolean = compare(candidate, current) > 0
}
