package app.gildongmu.core

/** What a passenger meant, as far as the host needs to know. */
enum class Intent { YES, NO, QUIET, RESUME, QUIZ, STORY, SKIP, FOOD, SIGHT, CAFE, RIDDLE, WORD_CHAIN, NONE }

/**
 * Turns recognised speech into something the host can act on. Speech recognition gives plain text
 * ("응 들르자", "세종대왕이요"), so this is keyword matching tuned for short spoken replies, not
 * language understanding.
 */
object Understanding {

    // Not a bare "조용" or "시끄러" anywhere: "조용한 데" and "시끄러운 곳" describe a place, and a
    // family choosing a spot may say either without asking the host to stop talking.
    private val quietWords = listOf("조용히", "조용해", "조용좀", "조용하게", "그만", "쉿", "시끄러워", "시끄럽", "말하지마", "멈춰")
    // Said last, the bare forms are still the command: "조용!", "아 시끄러".
    private val quietEndings = listOf("조용", "시끄러")
    private val resumeWords = listOf("다시말해", "이제말해", "말해도돼", "얘기해도돼", "계속해", "다시얘기", "이제괜찮")
    private val quizWords = listOf("퀴즈", "문제내", "문제줘", "문제하나")
    private val skipWords = listOf("건너뛰", "넘어가", "스킵", "다음얘기", "다음이야기", "다른얘기", "다른이야기")
    private val storyWords = listOf("이야기해", "얘기해", "이야기들려", "얘기들려", "이야기하나", "얘기하나", "이야기틀어", "옛날이야기", "옛날얘기")
    // "끝말있기" is how many people write it, so a recogniser may too.
    private val wordChainWords = listOf("끝말잇기", "끝말있기", "끝말이어", "끝말놀이")
    private val riddleWords = listOf("수수께끼", "넌센스", "난센스")
    // Coffee with words that on their own ask for food: "커피 맛집" is a good cafe, not a restaurant.
    private val tastyCafeWords = listOf("커피맛집", "카페맛집", "커피맛있", "커피가맛있", "커피먹을")
    // Phrases, never a bare "밥", "먹" or "음식": "비빔밥" is a quiz answer, "음식" a word the host
    // plays in 끝말잇기, and passing snacks around is not a request.
    private val foodWords = listOf(
        "뭐먹", "뭐좀먹", "먹을거", "먹을데", "먹을곳", "먹을만한", "맛집", "맛있는거", "맛있는데",
        "배고프", "배고파", "배고픈", "배가고프", "배가고파", "배가고픈", "밥먹", "밥집", "식당", "음식점", "먹거리"
    )
    private val cafeWords = listOf("카페", "커피", "아메리카노", "라테", "라떼", "음료", "마실거")
    // Wanting to eat without saying what. Checked after the cafe words, so "커피 먹으러 가자" is a cafe.
    private val eatingWords = listOf("먹고싶", "먹으러", "먹고가", "맛있는곳", "맛있는집", "뭐가맛있", "출출")
    private val sightWords = listOf("볼거", "볼만한", "볼데", "볼곳", "구경", "가볼만", "가볼데", "가볼곳", "관광", "명소")

    // Checked before YES, so "안 들를래" and "아니 괜찮아" are refusals even though they contain "들를",
    // and so are "계속 가자", "그냥 쭉 가자" and "다음에 가자" even though they contain "가자".
    private val noWords = listOf(
        "아니", "안돼", "안들", "안가", "안갈", "안해", "안쉬", "말고", "괜찮", "됐어", "싫", "그냥가", "나중에", "패스", "필요없",
        "계속가", "쭉가", "다음에"
    )
    // One-syllable agreement only counts when it is the whole reply: "어" is yes, "어디야" is not.
    private val yesExact = setOf("응", "응응", "네", "네네", "넵", "넹", "예", "예예", "어", "어어", "웅", "엉", "콜")
    private val yesWords = listOf("그래", "좋아", "좋지", "가자", "들르자", "들러", "들를", "쉬자", "쉬어", "오케이", "부탁", "그러자", "ok", "yes")

    /**
     * Things a passenger can ask for at any time: be quiet, skip this, a story, a quiz, riddles,
     * 끝말잇기, or somewhere to eat, to have coffee or to see. The order settles phrases that say
     * two things: "끝말잇기 그만" is QUIET, "넌센스 퀴즈 하자" a riddle, "맛집 얘기해 줘" food, and
     * "커피 맛집" a cafe.
     */
    fun commandOf(utterance: String): Intent {
        val u = Korean.compact(utterance)
        return when {
            u.isEmpty() -> Intent.NONE
            resumeWords.any { u.contains(it) } -> Intent.RESUME
            quietWords.any { u.contains(it) } || quietEndings.any { u.endsWith(it) } -> Intent.QUIET
            skipWords.any { u.contains(it) } -> Intent.SKIP
            wordChainWords.any { u.contains(it) } -> Intent.WORD_CHAIN
            riddleWords.any { u.contains(it) } -> Intent.RIDDLE
            quizWords.any { u.contains(it) } -> Intent.QUIZ
            tastyCafeWords.any { u.contains(it) } -> Intent.CAFE
            foodWords.any { u.contains(it) } -> Intent.FOOD
            cafeWords.any { u.contains(it) } -> Intent.CAFE
            eatingWords.any { u.contains(it) } -> Intent.FOOD
            sightWords.any { u.contains(it) } -> Intent.SIGHT
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

    // "도착" also names the destination, unless it is arriving at a rest stop: see [endOfLastArrival].
    private val destinationWords = listOf("목적지")
    private val beforeWords = listOf("가기전", "도착전", "도착하기전")
    private val hereWords = listOf("근처", "여기", "주변", "가까운")
    // Said right before "도착", these make it arriving at the rest stop: "휴게소 도착하면", "휴게소에 도착해서".
    private val stopArrivals = listOf("휴게소", "휴게소에")

    /**
     * Which part of the trip a request for food or sights is about, or null when it does not say:
     * the destination ("도착해서", or a word of [destinationName] such as "강릉"), the next rest stop
     * ("휴게소") or where the car is now ("근처", "여기").
     *
     * Rest stops are often named after a town, so when the destination and a rest stop are both
     * named, the one said last counts: "강릉대관령휴게소에서" is a stop, "휴게소 말고 강릉 가서" the
     * destination. Something to have before getting there ("도착하기 전에", "강릉 가기 전에") is
     * on the way, not at the destination, and arriving at the rest stop ("휴게소 도착하면") is the
     * rest stop.
     */
    fun areaOf(utterance: String, destinationName: String): SpotArea? {
        val u = Korean.compact(utterance)
        val names = destinationWords + wordsOf(destinationName).filter { it.length >= 2 }
        val destination =
            if (beforeWords.any { u.contains(it) }) -1 else maxOf(endOfLastArrival(u), names.maxOf { endOfLast(u, it) })
        val stop = endOfLast(u, "휴게소")
        return when {
            destination < 0 && stop < 0 -> if (hereWords.any { u.contains(it) }) SpotArea.HERE else null
            destination >= stop -> SpotArea.DESTINATION
            else -> SpotArea.NEXT_STOP
        }
    }

    // Said after a choice, these turn it down: "첫 번째 말고", "경포대는 별로", "1번은 안 갈래",
    // "초당은 나중에", "1번은 됐어", "거기는 너무 멀어". Taking a choice that was turned down would add
    // the one place the passenger refused. "됐" only counts after 은 or 는: "주문진이면 됐어" chooses it.
    private val turnDowns = listOf(
        "말고", "말구", "빼고", "아니", "싫", "별로", "안가", "안갈", "안들", "패스", "필요없", "나중", "다음에", "은됐", "는됐", "멀어"
    )

    /**
     * Which of [options], read out as a numbered list, the passenger chose: its index, or null.
     *
     * A choice is a number up to the size of the list ("2번", "이번", "두 번째", "둘째", "마지막") or a
     * name. A name counts when it is said whole, or by the start of the name or of one of its words
     * when no other option starts the same way: "주문진" picks 주문진수산시장, but "경포" picks
     * nothing when 경포대 and 경포호 are both offered. When several are named the last one said wins
     * ("첫 번째 말고 두 번째"), and one followed by a turn-down such as "말고", "싫어" or "안 갈래" is
     * set aside.
     *
     * Two kinds of mention are also what a family says while it talks the choice over, so they only
     * count in part. An ordinal that is one of [players] ("첫째", "둘째") is a choice only as the
     * whole reply or right before 거, 꺼, 로 or 요 ("둘째 거"): "첫째야, 뭐 먹을래?" talks to a child.
     * And the start of a name that could as well be a short way of saying [destinationName] ("강릉"
     * and "경포" for 강릉 경포해변) picks nothing: "강릉 가서 정하자" is about the town. The whole name,
     * "강릉 중앙시장", still picks the spot.
     */
    fun choiceOf(
        utterance: String,
        options: List<String>,
        players: List<String> = emptyList(),
        destinationName: String = ""
    ): Int? {
        val u = Korean.compact(utterance)
        val said = mentions(u, options, players, destinationName)
        val kept = said.filter { m ->
            val next = said.firstOrNull { it.start >= m.end }?.start ?: u.length
            val after = u.substring(m.end, next)
            turnDowns.none { after.contains(it) }
        }
        // Of two names ending at the same place, the longer one was meant: "중앙시장" over an option "시장".
        return kept.maxWithOrNull(compareBy<Mention>({ it.end }, { it.end - it.start }))?.option
    }

    /**
     * Whether [utterance] names any of [options] at all, by number or by name as [choiceOf] hears
     * them, even one it turns down ("첫 번째 말고"). Such a reply is about the list, so the family
     * may still be choosing: a refusal of one spot is not a refusal of them all.
     */
    fun mentionsOption(
        utterance: String,
        options: List<String>,
        players: List<String> = emptyList(),
        destinationName: String = ""
    ): Boolean = mentions(Korean.compact(utterance), options, players, destinationName).isNotEmpty()

    // Polite endings a player may put after a word: "차고요", "책상이에요". Not "지" or "야", which end
    // many words ("강아지").
    private val politeEndings = listOf("입니다", "이에요", "예요", "에요", "이요", "요")
    // Endings that may have taken the last syllable of a word ending in 이, each with what is left of
    // it without that 이: "고양이요" is 고양이 with 요 as much as 고양 with 이요.
    private val endingsAfterI = mapOf("이요" to "요", "이에요" to "에요")
    // Said after the word to check it: "고래 할래", "고래 맞지?".
    private val checkWords = listOf("할래", "할게", "맞지", "맞아", "어때")
    // Thinking aloud: the player is still looking for a word.
    private val fillers = listOf("잠깐", "잠깐만", "잠시만", "뭐지", "뭐더라", "글쎄")

    /**
     * The word a player said in 끝말잇기, or null when there is none: the last word of [utterance]
     * in Hangul, without a polite ending ("음 차고요" → "차고") and without a word said after it to
     * check it ("고래 할래?" → "고래").
     *
     * An ending is only dropped when two syllables are left, so "종이요" is 종이 and "동요" stays
     * 동요: the game allows no one-syllable words anyway. "고양이요" could be 고양 with 이요 or 고양이
     * with 요; when [known], the words the host knows, has 고양이, it is 고양이.
     *
     * Thinking aloud ("잠깐만", "어 뭐지") and saying the host's prompt back ("래로 시작하는 말?") are
     * not a word, so the player keeps the rest of the turn instead of losing the game with them.
     */
    fun wordOf(utterance: String, known: Set<String> = emptySet()): String? {
        if ("시작하는" in Korean.compact(utterance)) return null
        val words = utterance.split(wordBreak).map { word -> word.filter { it in '가'..'힣' } }
        // A recogniser may write an ending or a filler apart ("차고 요", "차고 음"), and a player may
        // check the word ("고래 할래"): skip those.
        val word = words.lastOrNull { it.length >= 2 && it !in politeEndings && withoutEnding(it, known) !in checkWords }
            ?: return null
        return withoutEnding(word, known).takeIf { it !in fillers }
    }

    /** [word] without one polite ending, if that leaves two syllables; see [wordOf]. */
    private fun withoutEnding(word: String, known: Set<String>): String {
        val ending = politeEndings.firstOrNull { word.endsWith(it) && word.length - it.length >= 2 } ?: return word
        val keepingI = endingsAfterI[ending]?.let { word.dropLast(it.length) }
        return if (keepingI != null && keepingI in known) keepingI else word.dropLast(ending.length)
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

    private val ordinals = listOf("첫", "두", "세", "네", "다섯", "여섯", "일곱", "여덟", "아홉", "열")
    private val sino = listOf("일", "이", "삼", "사", "오", "육", "칠", "팔", "구", "십")
    // Only for choosing a spot: the players are called "첫째" and "둘째", so quiz answers skip these.
    private val nativeOrdinals = listOf("첫째", "둘째", "셋째", "넷째", "다섯째", "여섯째", "일곱째", "여덟째", "아홉째", "열째")
    // After a player's name, these make it a choice: "둘째 거", "첫째 꺼", "둘째로 가자", "첫째요".
    private val choiceAfterName = setOf('거', '꺼', '로', '요')
    // After "이번", these make it "this time": "이번엔 됐어", "이번에는 그냥 가자".
    private val thisTime = setOf('에', '엔')

    private val wordBreak = Regex("[^\\p{L}\\p{N}]+")

    /** Option [option] (counted from 0) was named from [start] to [end] of a compact utterance. */
    private class Mention(val option: Int, val start: Int, val end: Int)

    /** Every mention of one of [options] in [compact], in the order said; see [choiceOf]. */
    private fun mentions(compact: String, options: List<String>, players: List<String>, destinationName: String): List<Mention> {
        if (compact.isEmpty() || options.isEmpty()) return emptyList()
        val playerNames = players.map { Korean.compact(it) }.toSet()
        val said = ArrayList<Mention>()
        for (number in 1..minOf(options.size, ordinals.size)) {
            said += numberMentions(compact, number)
            val ordinal = nativeOrdinals[number - 1]
            said += occurrences(compact, ordinal, number - 1).filter { ordinal !in playerNames || choosesByName(compact, it) }
        }
        said += occurrences(compact, "마지막", options.size - 1)
        said += nameMentions(compact, options, destinationName)
        return said.sortedBy { it.start }
    }

    /**
     * Whether [mention] of an ordinal that is also a player's name is a choice: the whole reply,
     * bare or polite ("첫째", "둘째예요"), or said right before 거, 꺼, 로 or 요 ("둘째 거"). Calling a
     * child by that name ("첫째야!") is not a choice.
     */
    private fun choosesByName(compact: String, mention: Mention): Boolean {
        val name = compact.substring(mention.start, mention.end)
        val politely = politeEndings.any { compact == name + it }
        return compact == name || politely || compact.getOrNull(mention.end) in choiceAfterName
    }

    /** "2번", "이번", "두 번째". */
    private fun saysChoiceNumber(compact: String, number: Int): Boolean =
        number in 1..ordinals.size && numberMentions(compact, number).isNotEmpty()

    /** Where [compact] says choice [number]: "2번" (not "12번"), "두 번째", or "이번" at the very start (not "이번엔"). */
    private fun numberMentions(compact: String, number: Int): List<Mention> {
        val option = number - 1
        val digits = occurrences(compact, "${number}번", option).filter { it.start == 0 || !compact[it.start - 1].isDigit() }
        val words = occurrences(compact, ordinals[option] + "번째", option)
        // "이번" is also "this time" ("초당 이번에 가 보자", "이번엔 괜찮아요"), so a Sino-Korean number
        // only counts first, and not before 에 or 엔.
        val sinoNumber = sino[option] + "번"
        val sinoFirst = compact.startsWith(sinoNumber) && compact.getOrNull(sinoNumber.length) !in thisTime
        return digits + words + if (sinoFirst) listOf(Mention(option, 0, sinoNumber.length)) else emptyList()
    }

    /**
     * Where [compact] names one of [options]: the whole name, or the start of the name or of one of
     * its words when that start belongs to no other option and is not also a start of
     * [destinationName].
     */
    private fun nameMentions(compact: String, options: List<String>, destinationName: String): List<Mention> {
        val starts = options.map { startsOf(it) }
        val destination = startsOf(destinationName)
        return options.indices.flatMap { i ->
            val unique = starts[i].filter { start ->
                start !in destination && starts.indices.none { j -> j != i && start in starts[j] }
            }
            (unique + Korean.compact(options[i])).distinct()
                .filter { it.length >= 2 }
                .flatMap { occurrences(compact, it, i) }
        }
    }

    /** "강릉 중앙시장" → 강릉, 강릉중, …, 강릉중앙시장, 중앙, 중앙시, 중앙시장: ways to say it for short. */
    private fun startsOf(name: String): Set<String> =
        (wordsOf(name) + Korean.compact(name)).flatMap { word -> (2..word.length).map { word.take(it) } }.toSet()

    /** The words of [name] in compact form: "강릉 경포해변" → 강릉, 경포해변. */
    private fun wordsOf(name: String): List<String> =
        name.split(wordBreak).map { Korean.compact(it) }.filter { it.isNotEmpty() }

    /** Every place where [key], which must not be empty, appears in [compact], as a mention of [option]. */
    private fun occurrences(compact: String, key: String, option: Int): List<Mention> {
        val found = ArrayList<Mention>()
        var at = compact.indexOf(key)
        while (at >= 0) {
            found.add(Mention(option, at, at + key.length))
            at = compact.indexOf(key, at + 1)
        }
        return found
    }

    /** Where the last [key] in [compact] ends, or -1 when it is not there. */
    private fun endOfLast(compact: String, key: String): Int {
        val at = compact.lastIndexOf(key)
        return if (at < 0) -1 else at + key.length
    }

    /**
     * Where the last "도착" in [compact] ends, or -1 when there is none. One said right after
     * "휴게소" or "휴게소에" is arriving at the rest stop ("휴게소 도착하면 뭐 먹지"), so it does not count.
     */
    private fun endOfLastArrival(compact: String): Int {
        var end = -1
        var at = compact.indexOf("도착")
        while (at >= 0) {
            if (stopArrivals.none { compact.take(at).endsWith(it) }) end = at + "도착".length
            at = compact.indexOf("도착", at + 1)
        }
        return end
    }
}
