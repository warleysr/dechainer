package io.github.warleysr.dechainer.utils

object BlockedWordMatcher {

    private const val SEPARATOR_CLASS = """[\s\._\-*~,/'"\\|`´^]"""

    private val CHAR_MAP = mapOf(
        'a' to "[aA4@áàâãäÁÀÂÃÄ]",
        'b' to "[bB8ß]",
        'c' to "[cCçÇkK<¢]",
        'd' to "[dD]",
        'e' to "[eE3éèêëÉÈÊË&€]",
        'f' to "[fF]",
        'g' to "[gG69]",
        'h' to "[hH#]",
        'i' to "[iI1!|íìîïÍÌÎÏl]",
        'j' to "[jJ]",
        'k' to "[kK]",
        'l' to "[lL1|!iI£]",
        'm' to "[mM]",
        'n' to "[nNñÑ]",
        'o' to "[oO0óòôõöÓÒÔÕÖØ]",
        'p' to "[pP]",
        'q' to "[qQ9]",
        'r' to "[rR]",
        's' to "[sS5$§zZ]",
        't' to "[tT7+†]",
        'u' to "[uUúùûüÚÙÛÜvVµ]",
        'v' to "[vVuU]",
        'w' to "[wW]",
        'x' to "[xX×]",
        'y' to "[yYýÝ¥]",
        'z' to "[zZ2sS]"
    )

    /**
     * Builds a Regex pattern that matches [rawWord] while resisting common obfuscation/bypass techniques:
     * - Spacing / punctuation between characters (e.g. "b uceta", "b.u.c.e.t.a", "b_uceta")
     * - Leetspeak substitutions and homoglyphs (e.g. "cuz1nho", "buc3t4", "p0rn0")
     * - Repeated characters (e.g. "buuuuceta")
     * - Preserves word boundaries using Unicode character classes so that short words (e.g. "cu")
     *   do not cause false positives in safe compound words (e.g. "escudo", "documento").
     */
    fun buildRegex(rawWord: String): Regex {
        val trimmed = rawWord.trim()
        if (trimmed.isEmpty()) {
            return Regex("(?!)") // Matches nothing
        }

        val phraseWords = trimmed.split(Regex("\\s+"))
        val wordPatterns = phraseWords.map { word ->
            val charPatterns = word.map { ch ->
                val lower = ch.lowercaseChar()
                val mapped = CHAR_MAP[lower]
                if (mapped != null) {
                    "$mapped+"
                } else {
                    "${Regex.escape(ch.toString())}+"
                }
            }
            charPatterns.joinToString(separator = "$SEPARATOR_CLASS*")
        }

        val interWordSep = "$SEPARATOR_CLASS+"
        val body = wordPatterns.joinToString(separator = interWordSep)

        val patternString = "(?<![\\p{L}\\p{N}])$body(?![\\p{L}\\p{N}])"
        return Regex(patternString, RegexOption.IGNORE_CASE)
    }
}
