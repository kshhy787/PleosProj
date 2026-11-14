package app.gildongmu.core

/**
 * Korean text helpers for speech: choosing the particle that follows a word, and reading numbers
 * that speech recognition returns as words ("천구백삼십육") rather than digits.
 */
object Korean {

    private const val RIEUL = 8

    /** True when the last sound of [word] ends in a consonant (받침). */
    fun endsWithConsonant(word: String): Boolean = finalConsonant(word) != 0

    /**
     * Index of the final consonant of the last pronounceable character: 0 for none, 8 for ㄹ.
     * Digits and a few Latin letters are judged by how they are read aloud (3 → "삼", L → "엘").
     */
    private fun finalConsonant(word: String): Int {
        val c = word.lastOrNull { it.isLetterOrDigit() } ?: return 0
        if (c in '가'..'힣') return (c.code - 0xAC00) % 28
        return when (c) {
            '0' -> 21 // 영
            '1', '7', '8' -> RIEUL // 일, 칠, 팔
            '3' -> 16 // 삼
            '6' -> 1 // 육
            '2', '4', '5', '9' -> 0 // 이, 사, 오, 구
            'l', 'L', 'r', 'R' -> RIEUL // 엘, 알
            'm', 'M' -> 16 // 엠
            'n', 'N' -> 4 // 엔
            else -> 0
        }
    }

    /** "횡성휴게소가", "오죽헌이". */
    fun iGa(word: String) = word + if (endsWithConsonant(word)) "이" else "가"

    /** "횡성휴게소를", "오죽헌을". */
    fun eulReul(word: String) = word + if (endsWithConsonant(word)) "을" else "를"

    /** "첫째는", "강릉은". */
    fun eunNeun(word: String) = word + if (endsWithConsonant(word)) "은" else "는"

    /** "세종대왕이에요", "율곡 이이예요". */
    fun ieyo(word: String) = word + if (endsWithConsonant(word)) "이에요" else "예요"

    /** "강릉으로", "횡성휴게소로", "서울로". */
    fun ro(word: String): String {
        val final = finalConsonant(word)
        return word + if (final != 0 && final != RIEUL) "으로" else "로"
    }

    /** "첫째와", "강릉과". */
    fun waGwa(word: String) = word + if (endsWithConsonant(word)) "과" else "와"

    // Vowel indices (중성) after which the initial-sound rule (두음법칙) changes ㄹ and ㄴ.
    private val toIeung = setOf(2, 6, 7, 12, 17, 20) // ㅑ ㅕ ㅖ ㅛ ㅠ ㅣ: 력 → 역, 녀 → 여
    private val toNieun = setOf(0, 1, 8, 11, 13, 18) // ㅏ ㅐ ㅗ ㅚ ㅜ ㅡ: 라 → 나, 로 → 노

    /**
     * How [syllable] is written at the start of a word under 두음법칙, or null when the rule does
     * not change it: 력 → 역, 리 → 이, 라 → 나, 녀 → 여. In 끝말잇기 a word ending in 력 may be
     * followed by one starting with 역.
     */
    fun initialForm(syllable: Char): Char? {
        if (syllable !in '가'..'힣') return null
        val index = syllable.code - 0xAC00
        val initial = index / 588
        val vowel = (index % 588) / 28
        val newInitial = when {
            initial == 5 && vowel in toIeung -> 11 // ㄹ → ㅇ
            initial == 5 && vowel in toNieun -> 2 // ㄹ → ㄴ
            initial == 2 && vowel in toIeung -> 11 // ㄴ → ㅇ
            else -> return null
        }
        return (0xAC00 + newInitial * 588 + index % 588).toChar()
    }

    // Longest first, so "이에요" is removed before "요" gets a chance.
    private val endings = listOf(
        "인것같아요", "인것같아", "같아요", "같아", "이라고생각해요", "라고생각해요",
        "입니다", "이에요", "이예요", "예요", "에요", "이요", "이야", "이지", "이죠",
        "인가요", "인가", "아닌가요", "아닌가", "일까요", "일까",
        "요", "야", "죠", "지"
    )

    /** Removes spaces, punctuation and case, so "세종 대왕!" and "세종대왕" compare equal. */
    fun compact(text: String): String =
        text.filter { it.isLetterOrDigit() }.lowercase()

    /**
     * [compact], then drops one polite or hedging ending: "세종대왕이요" → "세종대왕",
     * "오죽헌인 것 같아요" → "오죽헌". An utterance that is nothing but an ending is left alone.
     */
    fun core(text: String): String {
        val c = compact(text)
        for (ending in endings) {
            if (c.length > ending.length && c.endsWith(ending)) return c.dropLast(ending.length)
        }
        return c
    }

    private val sinoDigits = mapOf(
        '영' to 0L, '공' to 0L, '일' to 1L, '이' to 2L, '삼' to 3L, '사' to 4L,
        '오' to 5L, '육' to 6L, '륙' to 6L, '칠' to 7L, '팔' to 8L, '구' to 9L
    )
    private val sinoUnits = mapOf('십' to 10L, '백' to 100L, '천' to 1000L)
    private val sinoWord = Regex("[영공일이삼사오육륙칠팔구십백천만]+")

    private val nativeNumbers = listOf(
        "하나" to 1L, "한" to 1L, "둘" to 2L, "두" to 2L, "셋" to 3L, "세" to 3L, "넷" to 4L, "네" to 4L,
        "다섯" to 5L, "여섯" to 6L, "일곱" to 7L, "여덟" to 8L, "아홉" to 9L, "열" to 10L
    )
    // Words that follow a number without changing it. Longest first, so "킬로미터" wins over "미터".
    private val counters = listOf(
        "킬로미터", "퍼센트", "원짜리", "미터", "킬로", "마리", "가지", "사람", "군데", "번째", "년도", "짜리",
        "개", "명", "번", "살", "곳", "분", "년", "층", "위", "등", "점", "원", "도"
    )

    /** Reads a Sino-Korean number word such as "팔백삼십이" (832) or "이천십팔" (2018). */
    fun sinoNumber(word: String): Long? {
        if (word.isEmpty()) return null
        var total = 0L
        var section = 0L
        var current = 0L
        var haveDigit = false
        for (c in word) {
            val digit = sinoDigits[c]
            val unit = sinoUnits[c]
            when {
                digit != null -> {
                    // Two digits in a row ("일이") is a digit string, not a number word.
                    if (haveDigit) return null
                    current = digit
                    haveDigit = true
                }
                unit != null -> {
                    section += (if (haveDigit) current else 1L) * unit
                    current = 0
                    haveDigit = false
                }
                c == '만' -> {
                    val value = section + (if (haveDigit) current else 0L)
                    total += (if (value == 0L) 1L else value) * 10000L
                    section = 0
                    current = 0
                    haveDigit = false
                }
                else -> return null
            }
        }
        return total + section + (if (haveDigit) current else 0L)
    }

    /**
     * Every number that can be read out of [utterance]: digit runs ("832미터"), Sino-Korean number
     * words ("팔백삼십이 미터") and small native counts ("두 마리", "둘이요").
     *
     * Single syllables such as "이" or "오" are also ordinary Korean, so a one-syllable number only
     * counts when it is the whole answer ("오", "오요", "오 번").
     */
    fun numbersIn(utterance: String): Set<Long> {
        val found = LinkedHashSet<Long>()

        Regex("[0-9][0-9,]*").findAll(utterance).forEach { m ->
            m.value.replace(",", "").toLongOrNull()?.let(found::add)
        }

        val whole = core(utterance)
        val wholeWithoutCounter = counters.firstOrNull { whole.length > it.length && whole.endsWith(it) }
            ?.let { whole.dropLast(it.length) } ?: whole

        val compact = compact(utterance)
        sinoWord.findAll(compact).forEach { m ->
            val isWholeAnswer = m.value == whole || m.value == wholeWithoutCounter
            if (m.value.length >= 2 || isWholeAnswer) sinoNumber(m.value)?.let(found::add)
            // "팔백삼십이요" is 830 + "이요" as often as it is 832 + "요": keep both readings.
            val rest = compact.substring(m.range.last + 1)
            if (m.value.length >= 3 && m.value.endsWith("이") && listOf("요", "에요", "야", "지", "죠").any { rest.startsWith(it) }) {
                sinoNumber(m.value.dropLast(1))?.let(found::add)
            }
        }

        for ((word, value) in nativeNumbers) {
            if (whole == word || wholeWithoutCounter == word) found.add(value)
        }
        return found
    }
}
