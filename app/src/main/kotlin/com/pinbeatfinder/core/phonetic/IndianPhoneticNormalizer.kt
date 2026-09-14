package com.pinbeatfinder.core.phonetic

import java.text.Normalizer
import java.util.Locale

/**
 * Pre-normaliser for Indian place names written in Latin script.
 *
 * Indian toponyms have no canonical Roman spelling: the same village is "Rampur", "Rampoor" and
 * "Raampur" on three different registers. Double Metaphone was designed for English/European
 * spellings and does not know that "bh" and "b" are the same aspirated stop, or that "ee"/"i"
 * and "oo"/"u" are interchangeable long vowels in transliteration. This normaliser collapses
 * those transliteration-level variants *before* the phonetic encoder runs, so the encoder sees
 * a single canonical form.
 *
 * The output is lowercase ASCII with single spaces between tokens. It is deterministic and
 * pure, so it is safe to call on both write (pre-computing keys) and read (querying) paths.
 */
object IndianPhoneticNormalizer {

    /**
     * Ordered replacement rules. Order matters: multi-letter clusters (e.g. "chh") must be
     * handled before their shorter substrings ("ch", "h").
     */
    private val rules: List<Pair<Regex, String>> = listOf(
        // --- aspirated consonants: the "h" carries no phonetic information for matching ----
        Regex("chh") to "ch",
        Regex("bh") to "b",
        Regex("dh") to "d",
        Regex("gh") to "g",
        Regex("jh") to "j",
        Regex("kh") to "k",
        Regex("ph") to "f",
        Regex("th") to "t",
        Regex("sh") to "s",   // Shivaji / Sivaji, Shahpur / Sahpur

        // --- interchangeable consonants in transliteration --------------------------------
        Regex("w") to "v",     // Vishwas / Vishvas
        Regex("z") to "j",     // Zila / Jila
        Regex("q") to "k",     // Qasba / Kasba
        Regex("x") to "ks",
        Regex("ck") to "k",
        Regex("c(?=[^ehiy]|$)") to "k",   // Cuttack -> kuttack (hard c); "ch"/"ce"/"ci" untouched

        // --- long vowel digraphs -> single vowel --------------------------------------------
        Regex("ee") to "i",
        Regex("ii") to "i",
        Regex("oo") to "u",
        Regex("uu") to "u",
        Regex("aa") to "a",
        Regex("ou") to "u",    // Gourav / Gurav
        Regex("au") to "o",    // Gaurav / Gorav

        // --- "y" as a vowel glide between vowels is spelling noise (Dayal / Dyal) ----------
        Regex("(?<=[aeiou])y(?=[aeiou])") to "",

        // --- doubled consonants carry no extra information (Kallan / Kalan) ----------------
        Regex("([bcdfgjklmnprstv])\\1") to "$1",

        // --- common toponym suffix variants (Rampura / Rampur, Kanchipuram / Kanchipur) ----
        Regex("(?<=..)pura$") to "pur",
        Regex("(?<=..)puram$") to "pur",
        Regex("(?<=..)gad$") to "garh",
        Regex("(?<=..)nagr$") to "nagar",
    )

    /** Word-level aliases that are spelled too differently for rule-based folding. */
    private val tokenAliases: Map<String, String> = mapOf(
        "po" to "",          // "Rampur PO" -> "Rampur"
        "bo" to "",
        "so" to "",
        "vill" to "",
        "village" to "",
        "gram" to "",
        "gaon" to "gaon",
        "gaun" to "gaon",
        "ganv" to "gaon",
        "nagar" to "nagar",
        "nagr" to "nagar",
        "pura" to "pur",     // Rampura / Rampur
        "poor" to "pur",
        "puram" to "pur",
        "gadh" to "garh",
        "gad" to "garh",
    )

    /**
     * Normalises [input] into canonical lowercase ASCII tokens separated by a single space.
     * Returns an empty string when nothing alphabetic survives.
     */
    fun normalize(input: String): String {
        if (input.isBlank()) return ""
        val ascii = Normalizer.normalize(input, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")          // strip combining diacritics (ā -> a)
            .lowercase(Locale.ROOT)
            .replace(Regex("\\b([a-z])\\.\\s*([a-z])\\b\\.?"), "$1$2")  // "B.O." -> "bo"
            .replace(Regex("[^a-z ]+"), " ")        // punctuation/digits become separators
        return ascii.split(' ')
            .asSequence()
            .filter { it.length > 1 }               // single letters carry no phonetic signal
            .map { token -> tokenAliases[token] ?: token }
            .filter { it.isNotEmpty() }
            .map(::normalizeToken)
            .filter { it.isNotEmpty() }
            .joinToString(" ")
    }

    /** Tokenised view of [normalize], convenient for per-token phonetic encoding. */
    fun tokens(input: String): List<String> =
        normalize(input).split(' ').filter { it.isNotEmpty() }

    private fun normalizeToken(token: String): String {
        var t = token
        for ((regex, replacement) in rules) t = regex.replace(t, replacement)
        return t
    }
}
