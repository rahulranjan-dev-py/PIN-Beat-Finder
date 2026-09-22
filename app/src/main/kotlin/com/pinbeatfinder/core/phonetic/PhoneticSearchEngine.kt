package com.pinbeatfinder.core.phonetic

import org.apache.commons.codec.language.DoubleMetaphone
import kotlin.math.max
import kotlin.math.min

/**
 * Phonetic keys stored alongside every directory row so that fuzzy lookups run against
 * indexed columns instead of computing string distances at query time.
 *
 * Both keys are space-separated per-token Double Metaphone codes, e.g. "RMPR KLN" for
 * "Rampur Kalan". Per-token encoding keeps prefix matching meaningful: a query for "Rampur"
 * yields "RMPR", which is a prefix of the stored key and therefore hits the index.
 */
data class PhoneticKeys(val primary: String, val alternate: String) {
    val isEmpty: Boolean get() = primary.isEmpty()

    companion object {
        val EMPTY = PhoneticKeys("", "")
    }
}

/**
 * Double Metaphone based matcher tuned for Indian place names.
 *
 * Responsibilities:
 *  1. [encode] – derive the two phonetic keys persisted with a row (called on insert/update and
 *     when building a query).
 *  2. [score] – rank candidate rows returned by the DAO. The DAO does the cheap, indexed
 *     retrieval (substring + phonetic-prefix); this class orders that small candidate set with
 *     a more expensive similarity measure so the best guess is always first.
 *
 * Thread-safe: the encoder holds no per-call state.
 */
class PhoneticSearchEngine(
    maxCodeLength: Int = DEFAULT_CODE_LENGTH,
) {
    private val metaphone = DoubleMetaphone().apply { maxCodeLen = maxCodeLength }

    /** Encodes free text (a locality name or a search query) into phonetic keys. */
    fun encode(text: String): PhoneticKeys {
        val tokens = IndianPhoneticNormalizer.tokens(text)
        if (tokens.isEmpty()) return PhoneticKeys.EMPTY
        val primary = StringBuilder()
        val alternate = StringBuilder()
        for (token in tokens) {
            val p = metaphone.doubleMetaphone(token, false).orEmpty()
            val a = metaphone.doubleMetaphone(token, true).orEmpty()
            if (p.isEmpty()) continue
            if (primary.isNotEmpty()) {
                primary.append(' ')
                alternate.append(' ')
            }
            primary.append(p)
            alternate.append(a.ifEmpty { p })
        }
        return PhoneticKeys(primary.toString(), alternate.toString())
    }

    /** True when any key of [a] equals any key of [b] (the classic Double Metaphone test). */
    fun phoneticallyEqual(a: PhoneticKeys, b: PhoneticKeys): Boolean =
        !a.isEmpty && !b.isEmpty &&
            (a.primary == b.primary || a.primary == b.alternate ||
                a.alternate == b.primary || a.alternate == b.alternate)

    /**
     * Relevance score in `[0, 1]` for how well [candidate] answers [query].
     *
     * Tiers (higher wins):
     *  - 1.00 exact normalised match (with the same numbers / single letters, if any)
     *  - 0.90 candidate starts with the query
     *  - 0.80 candidate contains the query as a token / substring
     *  - 0.70 phonetically identical (all tokens)
     *  - 0.60 phonetic prefix match (query keys are a prefix of the candidate keys)
     *  - otherwise a blend of Jaro–Winkler similarity on the normalised strings
     *
     * Within a tier, the Jaro–Winkler similarity breaks ties so shorter/closer names come first.
     */
    fun score(query: String, candidate: String): Double {
        val q = IndianPhoneticNormalizer.normalize(query)
        val c = IndianPhoneticNormalizer.normalize(candidate)
        if (q.isEmpty() || c.isEmpty()) return 0.0

        val textual = jaroWinkler(q, c)
        val tieBreak = textual * 0.09 // keeps within-tier ordering below the next tier boundary

        // "Ward 1" and "Ward 2" normalise to the same text; only a matching number is exact.
        if (q == c) return if (IndianPhoneticNormalizer.qualifiersDiffer(query, candidate)) 0.80 + tieBreak else 1.0
        if (c.startsWith(q)) return 0.90 + tieBreak
        if (c.contains(q)) return 0.80 + tieBreak

        val qk = encode(query)
        val ck = encode(candidate)
        if (phoneticallyEqual(qk, ck)) return 0.70 + tieBreak
        if (!qk.isEmpty && (ck.primary.startsWith(qk.primary) || ck.alternate.startsWith(qk.alternate))) {
            return 0.60 + tieBreak
        }
        // Token-level phonetic hit anywhere in a multi-word name ("Kalan" -> "Rampur Kalan").
        val candidateTokens = ck.primary.split(' ')
        if (qk.primary.split(' ').all { it in candidateTokens }) return 0.55 + tieBreak

        return textual * 0.5
    }

    companion object {
        /** Long enough to distinguish multi-syllable names; commons-codec default (4) is too short. */
        const val DEFAULT_CODE_LENGTH = 6

        /**
         * Jaro–Winkler similarity (0..1). Implemented locally rather than pulling commons-text
         * onto the APK for one function.
         */
        fun jaroWinkler(s1: String, s2: String): Double {
            if (s1 == s2) return 1.0
            if (s1.isEmpty() || s2.isEmpty()) return 0.0
            val matchWindow = max(0, max(s1.length, s2.length) / 2 - 1)
            val s1Matches = BooleanArray(s1.length)
            val s2Matches = BooleanArray(s2.length)
            var matches = 0
            for (i in s1.indices) {
                val start = max(0, i - matchWindow)
                val end = min(i + matchWindow + 1, s2.length)
                for (j in start until end) {
                    if (s2Matches[j] || s1[i] != s2[j]) continue
                    s1Matches[i] = true
                    s2Matches[j] = true
                    matches++
                    break
                }
            }
            if (matches == 0) return 0.0
            var transpositions = 0
            var k = 0
            for (i in s1.indices) {
                if (!s1Matches[i]) continue
                while (!s2Matches[k]) k++
                if (s1[i] != s2[k]) transpositions++
                k++
            }
            val m = matches.toDouble()
            val jaro = (m / s1.length + m / s2.length + (m - transpositions / 2.0) / m) / 3.0
            var prefix = 0
            for (i in 0 until min(4, min(s1.length, s2.length))) {
                if (s1[i] == s2[i]) prefix++ else break
            }
            return jaro + prefix * 0.1 * (1 - jaro)
        }
    }
}
