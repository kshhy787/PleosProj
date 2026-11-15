package app.gildongmu.core

/**
 * One game of 끝말잇기: keeps the words used and chooses the host's words. Pure; the engine runs the
 * turns, says the words and listens for the players'.
 *
 * The host only says words from [words], the bundled list, so it never makes one up. The players
 * may say any word: the host cannot tell a real word from an invented one, so [check] judges only
 * the rules it can hear. Before the first word, any word may open the game.
 *
 * [seed] gives each game its own words and keeps it repeatable for the demo and the tests: the
 * host picks the word at index `seed.mod(n)` of its n candidates, in list order, and adds 31 to
 * the seed at each later move, so one seed per game also varies the words within the game.
 */
class WordChainGame(words: List<String>, private val seed: Int) {

    private companion object {
        /**
         * Added to the seed at each host move after the first. A prime shares no factor with most
         * candidate counts, so the index really moves from one move to the next.
         */
        const val MOVE_STEP = 31
    }

    /** What the host makes of a player's word. */
    enum class Verdict {
        /** Follows the last word and has not been said in this game. */
        VALID,

        /** Starts with neither the last word's final syllable nor its 두음법칙 form. */
        WRONG_START,

        /** Already said in this game, by the host or by a player. */
        ALREADY_USED,

        /** Nothing the game can judge: nothing heard, one syllable, or something other than Hangul. */
        NOT_A_WORD
    }

    // A listed word that the host would not accept from a player is no use to the host either.
    private val listed = words.filter { isWord(it) }.distinct()
    private val byStart = listed.groupBy { it.first() }
    private val said = LinkedHashSet<String>()
    private var hostMoves = 0

    /** Every word said in this game so far, the host's and the players', in the order they were said. */
    val used: List<String> get() = said.toList()

    /** The word the next one must follow; null until somebody has said one. */
    var last: String? = null
        private set

    /**
     * Records and returns the host's next word: an unused listed word that follows [last]. Null when
     * there is none, and then the players have won.
     *
     * The host prefers a word that still leaves an unused listed word to answer it with, so a game
     * does not end only because the host picked a word nobody can follow.
     */
    fun hostMove(): String? {
        val allowed = listed.filter { it !in said && followsLast(it) }
        if (allowed.isEmpty()) return null
        val candidates = allowed.filter { leavesAnAnswer(it) }.ifEmpty { allowed }
        val word = candidates[(seed + MOVE_STEP.toLong() * hostMoves).mod(candidates.size)]
        hostMoves++
        record(word)
        return word
    }

    /** Judges a player's word, as [Understanding.wordOf] heard it, without recording anything. */
    fun check(word: String?): Verdict = when {
        word == null || !isWord(word) -> Verdict.NOT_A_WORD
        word in said -> Verdict.ALREADY_USED
        !followsLast(word) -> Verdict.WRONG_START
        else -> Verdict.VALID
    }

    /** Records a player's word. Only a word that [check] finds [Verdict.VALID] may be played. */
    fun play(word: String) {
        require(check(word) == Verdict.VALID) { "\"$word\" may not follow \"$last\" in this game" }
        record(word)
    }

    /**
     * Up to [max] unused listed words that could follow [last], in list order: hints for the speech
     * recogniser, and the example the host gives when a player is stuck ("예를 들면 …").
     */
    fun hints(max: Int = 3): List<String> =
        listed.asSequence().filter { it !in said && followsLast(it) }.take(max.coerceAtLeast(0)).toList()

    /**
     * The last syllable of [last], which the host names when it hands over the turn ("차로 시작하는
     * 말!"). A word may also start with its 두음법칙 form: after 달력 this is 력, and 역사 follows.
     */
    fun nextSyllable(): Char? = last?.lastOrNull()

    private fun followsLast(word: String): Boolean {
        val previous = last ?: return true
        return WordList.follows(previous, word)
    }

    /** Whether, once [word] has been said, an unused listed word could still follow it. */
    private fun leavesAnAnswer(word: String): Boolean =
        WordList.nextStarts(word).any { start -> byStart[start].orEmpty().any { it != word && it !in said } }

    private fun record(word: String) {
        said.add(word)
        last = word
    }

    private fun isWord(word: String) = word.length >= 2 && word.all { it in '가'..'힣' }
}
