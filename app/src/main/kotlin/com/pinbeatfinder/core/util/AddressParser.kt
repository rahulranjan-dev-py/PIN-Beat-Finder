package com.pinbeatfinder.core.util

/**
 * Picks the useful parts out of a pasted address line so the search can work from a whole
 * article address ("Vill Ambona, PO Baliapur, Dist Dhanbad, Jharkhand 828201") instead of a
 * single village name typed by hand.
 *
 * Pure text processing: a 6-digit PIN if present, and the words that could be a locality or
 * office name once the usual address words (vill, PO, dist, near, road…) and numbers (house,
 * phone) are dropped. Nothing here touches stored data.
 */
object AddressParser {
    /**
     * What a pasted address yielded. [candidates] are search terms, best first; [office] is the
     * word that followed "PO" / "Post", when there was one.
     */
    data class Parsed(val pincode: String?, val candidates: List<String>, val isAddress: Boolean, val office: String? = null) {
        val isEmpty: Boolean get() = pincode == null && candidates.isEmpty()
    }

    /** Words that describe the address rather than name a place. */
    private val STOPWORDS = setOf(
        "vill", "village", "vil", "villg", "gram", "mauza", "mouza", "tola", "basti", "colony", "nagar",
        "po", "post", "office", "ps", "thana", "dist", "distt", "district", "dt", "state", "pin", "pincode", "code",
        "near", "nr", "via", "at", "opp", "opposite", "behind", "tehsil", "tahsil", "taluk", "taluka", "block",
        "mandal", "road", "rd", "lane", "street", "st", "marg", "chowk", "care", "of", "house", "hno", "no", "ward",
        "the", "and", "to", "from", "mr", "mrs", "ms", "shri", "smt", "sri", "kumari", "km", "india", "bharat",
        "bo", "so", "ho", "gpo", "sub", "branch", "head", "phone", "mob", "mobile", "ph", "contact", "address", "add",
        "flat", "floor", "plot", "sector", "phase", "apartment", "apt", "building", "bldg", "room", "front",
    )

    /** Words after which the next word is very likely the locality or the office. */
    private val LOCALITY_KEYWORDS = setOf("vill", "village", "vil", "villg", "gram", "mauza", "mouza", "at")
    private val OFFICE_KEYWORDS = setOf("po", "post", "ps")

    private val PIN_REGEX = Regex("""(?<!\d)([1-9]\d{2})\s?(\d{3})(?!\d)""")
    private val SPLIT_REGEX = Regex("""[\s,;:()\[\]/\\|]+""")

    fun parse(raw: String): Parsed {
        val text = raw.replace('\n', ' ').replace('\r', ' ').trim()
        if (text.isEmpty()) return Parsed(null, emptyList(), false)

        val pinMatch = PIN_REGEX.find(text)
        val pincode = pinMatch?.let { it.groupValues[1] + it.groupValues[2] }
        val withoutPin = if (pinMatch == null) text else text.removeRange(pinMatch.range)

        val rawTokens = withoutPin.split(SPLIT_REGEX).map { it.trim('.', '-', '\'', '"', '#', '&') }.filter { it.isNotEmpty() }
        val separators = text.count { it == ',' || it == ';' || it == '\n' }
        var stopwordCount = 0

        data class Word(val text: String, val afterLocality: Boolean, val afterOffice: Boolean)
        val words = ArrayList<Word>()
        var afterLocality = false
        var afterOffice = false
        for (token in rawTokens) {
            val key = token.lowercase().replace(".", "")
            when {
                key in STOPWORDS -> {
                    stopwordCount++
                    afterLocality = key in LOCALITY_KEYWORDS
                    afterOffice = key in OFFICE_KEYWORDS
                }
                key.length < 2 || key.any { it.isDigit() } || !key.any { it.isLetter() } -> {
                    afterLocality = false; afterOffice = false
                }
                else -> {
                    words += Word(token, afterLocality, afterOffice)
                    afterLocality = false; afterOffice = false
                }
            }
        }

        val isAddress = (pincode != null && words.isNotEmpty()) ||
            (rawTokens.size >= 3 && (stopwordCount > 0 || separators > 0)) ||
            rawTokens.size >= 5
        if (!isAddress) return Parsed(pincode.takeIf { words.isEmpty() && rawTokens.size <= 1 }, emptyList(), false)

        // Best first: the word after "Vill", then after "PO", then neighbouring pairs, then the rest.
        val ordered = LinkedHashSet<String>()
        words.filter { it.afterLocality }.forEach { ordered += it.text }
        words.filter { it.afterOffice }.forEach { ordered += it.text }
        for (i in 0 until words.size - 1) ordered += words[i].text + " " + words[i + 1].text
        words.forEach { ordered += it.text }
        return Parsed(pincode, ordered.take(MAX_CANDIDATES), true, office = words.firstOrNull { it.afterOffice }?.text)
    }

    fun looksLikeAddress(raw: String): Boolean = parse(raw).isAddress

    /** For the All-India tab: the PIN when the address has one, else the post office named after "PO", else the best word. */
    fun onlineQuery(raw: String): String? {
        val p = parse(raw)
        if (!p.isAddress) return null
        return p.pincode ?: p.office ?: p.candidates.firstOrNull()
    }

    private const val MAX_CANDIDATES = 8
}
