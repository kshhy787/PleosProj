package app.gildongmu.core

/** What a passenger meant, as far as the host needs to know. */
enum class Intent { YES, NO, QUIET, RESUME, QUIZ, STORY, SKIP, NONE }

/**
 * Turns recognised speech into something the host can act on. Speech recognition gives plain text
 * ("응 들르자", "세종대왕이요"), so this is keyword matching tuned for short spoken replies, not
 * language understanding.
 */
object Understanding {

    private val quietWords = listOf("조용", "그만", "쉿", "시끄러", "말하지마", "멈춰")
    private val resumeWords = listOf("다시말해", "이제말해", "말해도돼", "얘기해도돼", "계속해", "다시얘기", "이제괜찮")
    private val quizWords = listOf("퀴즈", "문제내", "문제줘", "문제하나")
    private val skipWords = listOf("건너뛰", "넘어가", "스킵", "다음얘기", "다음이야기", "다른얘기", "다른이야기")
    private val storyWords = listOf("이야기해", "얘기해", "이야기들려", "얘기들려", "이야기하나", "얘기하나", "이야기틀어", "옛날이야기", "옛날얘기")

    // Checked before YES, so "안 들를래" and "아니 괜찮아" are refusals even though they contain "들를".
    private val noWords = listOf("아니", "안돼", "안들", "안가", "안해", "안쉬", "말고", "괜찮", "됐어", "싫", "그냥가", "나중에", "패스", "필요없")
    // One-syllable agreement only counts when it is the whole reply: "어" is yes, "어디야" is not.
    private val yesExact = setOf("응", "응응", "네", "네네", "넵", "넹", "예", "예예", "어", "어어", "웅", "엉", "콜")
    private val yesWords = listOf("그래", "좋아", "좋지", "가자", "들르자", "들러", "들를", "쉬자", "쉬어", "오케이", "부탁", "그러자", "ok", "yes")

    /** Things a passenger can ask for at any time: be quiet, tell a story, start a quiz, skip this. */
    fun commandOf(utterance: String): Intent {
        val u = Korean.compact(utterance)
        return when {
            u.isEmpty() -> Intent.NONE
            resumeWords.any { u.contains(it) } -> Intent.RESUME
            quietWords.any { u.contains(it) } -> Intent.QUIET
            skipWords.any { u.contains(it) } -> Intent.SKIP
            quizWords.any { u.contains(it) } -> Intent.QUIZ
            storyWords.any { u.contains(it) } -> Intent.STORY
            else -> Intent.NONE
        }
    }

    /** A reply to a yes/no question such as "들를까요?". */
    fun yesNoOf(utterance: String): Intent {
        val u = Korean.compact(utterance)
        return when {
            u.isEmpty() -> Intent.NONE
            noWords.any { u.contains(it) } || u == "노" -> Intent.NO
            u in yesExact || Korean.core(utterance) in yesExact || yesWords.any { u.contains(it) } -> Intent.YES
            else -> Intent.NONE
        }
    }

    /**
     * Whether [utterance] answers [question] correctly.
     *
     * If any accepted answer is a number ("832", "10000"), the question is numeric: the utterance
     * must contain exactly that number, however it was said ("832미터", "팔백삼십이", "만 원"), so
     * "8320" or "오만 원" do not pass. Otherwise an answer matches when it appears in what was said
     * ("정답은 세종대왕이요"). Multiple-choice questions also accept the choice number.
     */
    fun isCorrect(utterance: String, question: QuizQuestion): Boolean {
        val compact = Korean.compact(utterance)
        if (compact.isEmpty()) return false

        if (question.choices.isNotEmpty()) {
            val correctIndex = question.choices.indexOfFirst { it in question.answers }
            if (correctIndex >= 0 && saysChoiceNumber(compact, correctIndex + 1)) return true
        }

        val answers = question.answers.map { Korean.compact(it) }.filter { it.isNotEmpty() }
        val numeric = answers.mapNotNull { it.toLongOrNull() }
        if (numeric.isNotEmpty()) {
            val said = Korean.numbersIn(utterance)
            return numeric.any { it in said }
        }

        val core = Korean.core(utterance)
        return answers.any { a ->
            if (a.length == 1) compact == a || core == a else compact.contains(a) || core.contains(a)
        }
    }

    private val ordinals = listOf("첫", "두", "세", "네", "다섯")
    private val sino = listOf("일", "이", "삼", "사", "오")

    /** "2번", "이번", "두 번째". */
    private fun saysChoiceNumber(compact: String, number: Int): Boolean {
        if (number !in 1..5) return false
        return compact.contains("${number}번") ||
            compact.startsWith(sino[number - 1] + "번") ||
            compact.contains(ordinals[number - 1] + "번째")
    }
}
