package app.gildongmu.core

/**
 * Splits text into sentences and estimates how long it takes to say.
 *
 * The host speaks one sentence at a time. That is what lets it stop cleanly for a navigation
 * prompt and pick the sentence up again afterwards, and it gives the screen one caption line at a
 * time instead of a paragraph.
 */
object Speech {

    private val sentenceEnd = Regex("(?<=[.!?…])\\s+|\\n+")

    fun sentences(text: String): List<String> =
        text.trim().split(sentenceEnd).map { it.trim() }.filter { it.isNotEmpty() }

    /**
     * Rough speaking time in seconds. Korean speech synthesis runs at about five syllables a
     * second; digits take longer than one syllable each to read out, and every sentence ends with
     * a short pause.
     */
    fun estimateSeconds(text: String, syllablesPerSecond: Double = 5.2): Double {
        var syllables = 0.0
        for (c in text) {
            syllables += when {
                c in '0'..'9' -> 1.6
                c.isLetter() -> 1.0
                else -> 0.0
            }
        }
        val pauses = sentences(text).size * 0.35
        return syllables / syllablesPerSecond + pauses
    }
}
