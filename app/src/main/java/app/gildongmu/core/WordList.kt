package app.gildongmu.core

/** Thrown when the 끝말잇기 word list breaks a rule the host relies on. */
class WordListFormatException(message: String) : Exception(message)

/**
 * The words the host may say in 끝말잇기, read from a bundled text file (`assets/games/word-chain.txt`):
 * one word per line, lines starting with `#` are comments.
 *
 * Every word must be at least two Hangul syllables and appear once. The host only answers with
 * words from this list; what the players say is checked by the game, not against the list.
 */
object WordList {

    fun parse(text: String): List<String> {
        val words = ArrayList<String>()
        val seen = HashSet<String>()
        for ((index, raw) in text.lines().withIndex()) {
            val line = raw.trim().removePrefix("﻿")
            if (line.isEmpty() || line.startsWith("#")) continue
            if (line.length < 2 || line.any { it !in '가'..'힣' }) {
                throw WordListFormatException("Word list line ${index + 1}: \"$line\" should be two or more Hangul syllables")
            }
            if (!seen.add(line)) throw WordListFormatException("Word list line ${index + 1}: \"$line\" is listed twice")
            words.add(line)
        }
        if (words.isEmpty()) throw WordListFormatException("Word list is empty")
        return words
    }

    /**
     * The syllables a word following [word] may start with: its last syllable, and that syllable's
     * 두음법칙 form when there is one ("달력" → 력, 역).
     */
    fun nextStarts(word: String): Set<Char> {
        val last = word.lastOrNull() ?: return emptySet()
        return setOfNotNull(last, Korean.initialForm(last))
    }

    /** Whether [next] may follow [previous] in 끝말잇기. */
    fun follows(previous: String, next: String): Boolean =
        next.length >= 2 && next.first() in nextStarts(previous)
}
