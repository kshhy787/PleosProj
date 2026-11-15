package app.gildongmu.core

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnderstandingTest {

    private fun text(vararg answers: String) = QuizQuestion("q", "?", answers.toList(), "")

    private fun choice(choices: List<String>, vararg answers: String) = QuizQuestion("q", "?", answers.toList(), "", choices)

    // Spots the bundled route recommends near Gangneung, named as the host reads them out.
    private val food = listOf("초당두부마을", "강릉 중앙시장", "주문진수산시장")
    private val sights = listOf("경포대", "경포호", "오죽헌")

    private fun bundled(path: String): String {
        val file = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path")).firstOrNull { it.exists() }
            ?: error("Cannot find $path from ${File(".").absolutePath}")
        return file.readText()
    }

    @Test
    fun recognisesCommands() {
        assertEquals(Intent.QUIET, Understanding.commandOf("조용히 해 줘"))
        assertEquals(Intent.QUIET, Understanding.commandOf("쉿"))
        assertEquals(Intent.QUIET, Understanding.commandOf("이제 그만"))
        assertEquals(Intent.QUIZ, Understanding.commandOf("퀴즈 하자"))
        assertEquals(Intent.QUIZ, Understanding.commandOf("문제 내 줘"))
        assertEquals(Intent.STORY, Understanding.commandOf("이야기해 줘"))
        assertEquals(Intent.STORY, Understanding.commandOf("얘기 하나 들려줘"))
        assertEquals(Intent.SKIP, Understanding.commandOf("건너뛰어"))
        assertEquals(Intent.SKIP, Understanding.commandOf("다음 이야기"))
        assertEquals(Intent.RESUME, Understanding.commandOf("이제 얘기해도 돼"))
        assertEquals(Intent.NONE, Understanding.commandOf("세종대왕"))
        assertEquals(Intent.NONE, Understanding.commandOf(""))
    }

    @Test
    fun resumeWinsOverTheStoryWordsItContains() {
        // "얘기해도 돼" contains "얘기해", which on its own asks for a story.
        assertEquals(Intent.RESUME, Understanding.commandOf("얘기해도 돼"))
    }

    @Test
    fun recognisesRequestsForFoodSightsAndCafes() {
        val requests = mapOf(
            Intent.FOOD to listOf(
                "뭐 먹지", "뭐 먹을까?", "맛집 알려줘", "배고파", "배고프다", "밥 먹자", "먹을 거 추천해 줘",
                "배가 고파요", "배고픈데", "출출하다", "뭐 좀 먹고 가자", "한우 먹으러 가자", "국수 먹고 싶어",
                "거기 뭐가 맛있어?", "맛있는 집 알려 줘", "먹거리 알려 줘", "식당 찾아 줘", "음식점 찾아 줘", "밥집 있어?"
            ),
            Intent.SIGHT to listOf(
                "볼 거 있어?", "구경할 데 있어?", "볼거리 알려 줘", "가 볼 만한 곳 있어?", "볼 곳 있어?",
                "관광지 추천해 줘", "강릉 명소 알려 줘"
            ),
            Intent.CAFE to listOf(
                "커피 마시고 싶어", "카페 가자", "아메리카노 한 잔 하고 싶다", "라테 마시고 싶어", "마실 거 사러 가자", "음료수 사자"
            )
        )
        for ((intent, phrasings) in requests) {
            for (said in phrasings) {
                assertEquals(said, intent, Understanding.commandOf(said))
            }
        }
    }

    @Test
    fun recognisesRequestsForGames() {
        for (said in listOf("수수께끼 내 줘", "수수께끼 하자", "넌센스 퀴즈 하자", "난센스 문제 내 줘", "수수께끼 문제 내 줘")) {
            assertEquals(said, Intent.RIDDLE, Understanding.commandOf(said))
        }
        for (said in listOf("끝말잇기 하자", "끝말잇기 할래", "끝말 이어 가기 하자", "끝말놀이 하자", "끝말있기 하자")) {
            assertEquals(said, Intent.WORD_CHAIN, Understanding.commandOf(said))
        }
    }

    @Test
    fun aRequestForSomewhereToGoWinsOverTheStoryWordsItContains() {
        assertEquals(Intent.FOOD, Understanding.commandOf("맛집 얘기해 줘"))
        assertEquals(Intent.SIGHT, Understanding.commandOf("볼거리 이야기해 줘"))
        assertEquals(Intent.CAFE, Understanding.commandOf("카페 얘기해 줘"))
    }

    @Test
    fun aCafeAskedForWithAWordForEatingIsStillACafe() {
        // Many people say "커피 먹자". A plain wish to eat only asks for food when no drink is named.
        assertEquals(Intent.CAFE, Understanding.commandOf("커피 먹으러 가자"))
        assertEquals(Intent.CAFE, Understanding.commandOf("커피 먹고 싶다"))
        assertEquals(Intent.FOOD, Understanding.commandOf("한우 먹고 싶다"))
    }

    @Test
    fun aGoodPlaceForCoffeeIsACafe() {
        // 맛집 and 맛있는 데 on their own ask for food, but not when they describe the coffee.
        for (said in listOf("커피 맛집 알려 줘", "커피 맛있는 데 있어?", "커피가 맛있는 데", "카페 맛집 가자", "커피 먹을 데 있어?")) {
            assertEquals(said, Intent.CAFE, Understanding.commandOf(said))
        }
        assertEquals(Intent.FOOD, Understanding.commandOf("카페 말고 맛집"))
        assertEquals(Intent.FOOD, Understanding.commandOf("커피 말고 밥 먹자"))
    }

    @Test
    fun quietSkipAndQuizStillWinOverTheNewRequests() {
        assertEquals(Intent.QUIET, Understanding.commandOf("끝말잇기 그만"))
        assertEquals(Intent.QUIET, Understanding.commandOf("수수께끼 그만하자"))
        assertEquals(Intent.SKIP, Understanding.commandOf("맛집 말고 다른 얘기"))
        assertEquals(Intent.QUIZ, Understanding.commandOf("음식 퀴즈 내 줘"))
    }

    @Test
    fun everydayWordsAreNotRequests() {
        // 음식, 이야기 and 놀이 are also words the host plays in 끝말잇기.
        for (said in listOf("밥", "먹어", "비빔밥", "배터리 얼마나 남았어?", "영화 볼래?", "식탁", "카메라", "음식", "이야기", "놀이")) {
            assertEquals(said, Intent.NONE, Understanding.commandOf(said))
        }
    }

    @Test
    fun noBundledAnswerOrYesNoReplyIsTakenForACommand() {
        // While the host listens only QUIET and SKIP cut in, but an answer said just after the time
        // is up is read as a command, so none may be one.
        val pkg = RoutePackageParser.parse(bundled("routes/seoul-gangneung.json"))
        val answers = (pkg.pois.flatMap { it.quiz } + pkg.generalQuiz + pkg.funQuiz).flatMap { it.answers + it.choices }
        val replies = listOf(
            "응", "네", "어", "그래", "좋아요", "응 들르자", "들러 줘", "오케이",
            "아니", "아니요", "괜찮아", "안 들를래", "그냥 가자", "나중에", "싫어", "됐어", "안 가도 돼"
        )
        for (said in answers + replies) {
            assertEquals(said, Intent.NONE, Understanding.commandOf(said))
        }
    }

    @Test
    fun noBundledWordChainWordIsTakenForACommand() {
        // The host offers these words as examples and the scripted passenger plays them. QUIET would
        // silence the host mid-game, and a request repeated after the round would start something else.
        for (word in WordList.parse(bundled("games/word-chain.txt"))) {
            assertEquals(word, Intent.NONE, Understanding.commandOf(word))
        }
    }

    @Test
    fun recognisesYes() {
        for (said in listOf("응", "네", "어", "그래", "좋아요", "응 들르자", "들러 줘", "오케이", "네네", "넵", "응!", "네요")) {
            assertEquals(said, Intent.YES, Understanding.yesNoOf(said))
        }
    }

    @Test
    fun recognisesNo() {
        for (said in listOf("아니", "아니요", "괜찮아", "안 들를래", "그냥 가자", "나중에", "싫어", "됐어", "안 가도 돼", "안 갈래")) {
            assertEquals(said, Intent.NO, Understanding.yesNoOf(said))
        }
    }

    @Test
    fun somethingElseIsNeitherYesNorNo() {
        for (said in listOf("어디야?", "배고파", "예전에 가 봤어", "")) {
            assertEquals(said, Intent.NONE, Understanding.yesNoOf(said))
        }
    }

    @Test
    fun aTextAnswerMatchesInsideWhatWasSaid() {
        val q = text("세종대왕", "세종")
        assertTrue(Understanding.isCorrect("세종대왕", q))
        assertTrue(Understanding.isCorrect("세종대왕이요!", q))
        assertTrue(Understanding.isCorrect("정답은 세종 대왕입니다", q))
        assertTrue(Understanding.isCorrect("세종", q))
        assertFalse(Understanding.isCorrect("태종", q))
        assertFalse(Understanding.isCorrect("", q))
        assertFalse(Understanding.isCorrect("잘 모르겠어요", q))
    }

    @Test
    fun aOneSyllableAnswerMustBeTheWholeReply() {
        val q = text("꿩")
        assertTrue(Understanding.isCorrect("꿩", q))
        assertTrue(Understanding.isCorrect("꿩이요", q))
        assertTrue(Understanding.isCorrect("꿩 같아요", q))
        assertFalse(Understanding.isCorrect("꿩 말고 비둘기", q))
        assertFalse(Understanding.isCorrect("닭", q))
    }

    @Test
    fun anAnswerEndingInAnEndingLikeSyllableStillMatches() {
        // "강아지" ends in "지", which is also a sentence ending that core() would strip.
        val q = text("강아지")
        assertTrue(Understanding.isCorrect("강아지", q))
        assertTrue(Understanding.isCorrect("강아지요", q))
    }

    @Test
    fun aNumericAnswerMustMatchExactly() {
        val q = text("832")
        assertTrue(Understanding.isCorrect("832", q))
        assertTrue(Understanding.isCorrect("832미터요", q))
        assertTrue(Understanding.isCorrect("팔백삼십이 미터", q))
        assertFalse(Understanding.isCorrect("8320", q))
        assertFalse(Understanding.isCorrect("32", q))
        assertFalse(Understanding.isCorrect("1832미터", q))
        assertFalse(Understanding.isCorrect("팔백 미터", q))
    }

    @Test
    fun moneyIsComparedByValueSoFiftyThousandIsNotTenThousand() {
        val q = text("만 원", "10000")
        assertTrue(Understanding.isCorrect("만 원", q))
        assertTrue(Understanding.isCorrect("만 원짜리요", q))
        assertTrue(Understanding.isCorrect("10000원", q))
        assertFalse(Understanding.isCorrect("오만 원", q))
        assertFalse(Understanding.isCorrect("천 원", q))
    }

    @Test
    fun aChoiceCanBeAnsweredByNumberOrByText() {
        val q = choice(listOf("서해", "남해", "동해"), "동해")
        assertTrue(Understanding.isCorrect("동해", q))
        assertTrue(Understanding.isCorrect("3번", q))
        assertTrue(Understanding.isCorrect("삼 번이요", q))
        assertTrue(Understanding.isCorrect("세 번째", q))
        assertFalse(Understanding.isCorrect("1번", q))
        assertFalse(Understanding.isCorrect("서해", q))
    }

    @Test
    fun aNumericChoiceAcceptsItsNumberOrItsValue() {
        val q = choice(listOf("288미터", "1288미터", "2288미터"), "1288미터", "1288")
        assertTrue(Understanding.isCorrect("2번", q))
        assertTrue(Understanding.isCorrect("1288미터", q))
        assertTrue(Understanding.isCorrect("천이백팔십팔 미터", q))
        assertFalse(Understanding.isCorrect("1번", q))
        assertFalse(Understanding.isCorrect("288미터", q))
        assertFalse(Understanding.isCorrect("2288", q))
    }

    @Test
    fun aQuizChoiceIsNotAnsweredByAPlayersNameOrALongerNumber() {
        // The players are called 첫째 and 둘째, so in a quiz those are not choice numbers.
        val q = choice(listOf("서해", "남해", "동해"), "서해")
        assertTrue(Understanding.isCorrect("1번", q))
        assertFalse(Understanding.isCorrect("첫째", q))
        assertFalse(Understanding.isCorrect("11번", q))
    }

    @Test
    fun aRequestCanSayWhichPartOfTheTripItIsAbout() {
        val cases = listOf(
            "강릉 가서 뭐 먹지" to SpotArea.DESTINATION,
            "경포해변 근처 맛집" to SpotArea.DESTINATION,
            "도착해서 뭐 먹을까" to SpotArea.DESTINATION,
            "목적지 근처에 볼 거 있어?" to SpotArea.DESTINATION,
            "휴게소에서 뭐 먹지" to SpotArea.NEXT_STOP,
            "다음 휴게소 근처 볼거리" to SpotArea.NEXT_STOP,
            "여기 휴게소에 맛집 있어?" to SpotArea.NEXT_STOP,
            "근처에 볼 거 있어?" to SpotArea.HERE,
            "여기 맛집 있어?" to SpotArea.HERE,
            "이 주변에 카페 있어?" to SpotArea.HERE,
            "제일 가까운 식당" to SpotArea.HERE,
            "뭐 먹지" to null
        )
        for ((said, area) in cases) {
            assertEquals(said, area, Understanding.areaOf(said, "강릉 경포해변"))
        }
    }

    @Test
    fun onlyDestinationWordsOfTwoOrMoreCharactersCount() {
        assertEquals(SpotArea.DESTINATION, Understanding.areaOf("목장 근처 맛집", "양 떼 목장"))
        // "양" is too short to tell from ordinary speech: here it means a portion.
        assertNull(Understanding.areaOf("양 많이 주는 식당", "양 떼 목장"))
    }

    @Test
    fun whenTheDestinationAndARestStopAreBothNamedTheOneSaidLastCounts() {
        // Rest stops are often named after a town, like 강릉대관령휴게소.
        assertEquals(SpotArea.NEXT_STOP, Understanding.areaOf("강릉대관령휴게소에서 뭐 먹지", "강릉 경포해변"))
        assertEquals(SpotArea.NEXT_STOP, Understanding.areaOf("강릉 가기 전에 휴게소에서 먹자", "강릉 경포해변"))
        assertEquals(SpotArea.DESTINATION, Understanding.areaOf("휴게소 말고 강릉 가서 먹자", "강릉 경포해변"))
        // A destination that is itself a rest stop is still the destination.
        assertEquals(SpotArea.DESTINATION, Understanding.areaOf("횡성휴게소 맛집", "횡성휴게소"))
    }

    @Test
    fun somethingToHaveBeforeArrivingIsNotAtTheDestination() {
        val cases = listOf(
            "도착하기 전에 뭐 먹지" to null,
            "강릉 가기 전에 뭐 먹을까" to null,
            "도착 전에 휴게소에서 먹자" to SpotArea.NEXT_STOP,
            "강릉 들어가기 전에 근처에서 먹자" to SpotArea.HERE,
            "도착해서 뭐 먹지" to SpotArea.DESTINATION
        )
        for ((said, area) in cases) {
            assertEquals(said, area, Understanding.areaOf(said, "강릉 경포해변"))
        }
    }

    @Test
    fun aChoiceCanBeSaidByItsNumber() {
        val cases = mapOf(
            0 to listOf("1번", "일번", "일 번이요", "첫 번째", "첫번째", "첫째", "첫 번째요"),
            1 to listOf("2번", "2번이요", "이번", "두 번째", "두번째 거", "둘째"),
            2 to listOf("3번", "삼번", "삼 번", "세 번째", "셋째", "세 번째로 가자", "마지막 거")
        )
        for ((index, phrasings) in cases) {
            for (said in phrasings) {
                assertEquals(said, index, Understanding.choiceOf(said, food))
            }
        }
    }

    @Test
    fun aNumberBeyondTheListPicksNothing() {
        for (said in listOf("4번", "네 번째", "넷째", "11번", "21번")) {
            assertNull(said, Understanding.choiceOf(said, food))
        }
        assertEquals(0, Understanding.choiceOf("마지막", listOf("경포대")))
        assertNull(Understanding.choiceOf("2번", listOf("경포대")))
    }

    @Test
    fun aChoiceCanBeSaidByItsNameOrTheStartOfIt() {
        val cases = listOf(
            "초당두부마을" to 0, "초당" to 0, "초당 가자" to 0,
            "강릉 중앙시장" to 1, "중앙시장" to 1, "중앙시장으로 가 줘" to 1,
            "주문진수산시장" to 2, "주문진" to 2, "주문진 좋다" to 2
        )
        for ((said, index) in cases) {
            assertEquals(said, index, Understanding.choiceOf(said, food))
        }
    }

    @Test
    fun theStartOfANameCountsOnlyWhenNoOtherOptionStartsTheSameWay() {
        assertNull(Understanding.choiceOf("경포", sights))
        assertNull(Understanding.choiceOf("경포 가자", sights))
        assertEquals(0, Understanding.choiceOf("경포대", sights))
        assertEquals(1, Understanding.choiceOf("경포호 가 보자", sights))
        assertEquals(2, Understanding.choiceOf("오죽", sights))
        // The middle or the end of a name is not enough: "시장" ends two of them.
        assertNull(Understanding.choiceOf("시장", food))
    }

    @Test
    fun ofTwoNamesEndingTogetherTheLongerOneWasMeant() {
        val options = listOf("시장", "강릉 중앙시장")
        assertEquals(1, Understanding.choiceOf("중앙시장", options))
        assertEquals(0, Understanding.choiceOf("시장", options))
    }

    @Test
    fun whenSeveralAreNamedTheLastOneWins() {
        assertEquals(1, Understanding.choiceOf("첫 번째 말고 두 번째", food))
        assertEquals(2, Understanding.choiceOf("1번, 아니 3번", food))
        assertEquals(2, Understanding.choiceOf("두 번째 아니 주문진", food))
        assertEquals(0, Understanding.choiceOf("2번 말고 초당", food))
        assertEquals(0, Understanding.choiceOf("경포호 말고 경포대", sights))
        // Without a word that turns the first one down, the passenger simply changed their mind.
        assertEquals(1, Understanding.choiceOf("첫 번째, 아 잠깐, 두 번째", food))
        assertEquals(2, Understanding.choiceOf("초당 음 주문진으로 하자", food))
    }

    @Test
    fun aChoiceTurnedDownIsNotTaken() {
        assertNull(Understanding.choiceOf("첫 번째 말고", food))
        assertNull(Understanding.choiceOf("1번 빼고", food))
        assertNull(Understanding.choiceOf("두 번째는 싫어", food))
        assertNull(Understanding.choiceOf("경포대는 별로", sights))
        assertNull(Understanding.choiceOf("첫 번째는 안 갈래", food))
        assertNull(Understanding.choiceOf("1번은 안 들를래", food))
        assertNull(Understanding.choiceOf("두 번째는 필요 없어", food))
        assertNull(Understanding.choiceOf("1번은 패스", food))
        // The one said last was turned down, so the one before it stands.
        assertEquals(0, Understanding.choiceOf("1번 가자, 2번은 안 가도 돼", food))
    }

    @Test
    fun everyBundledSpotCanBeChosenByItsName() {
        // The host reads out a few spots of one kind; saying a name must pick it among all of that kind.
        val pkg = RoutePackageParser.parse(bundled("routes/seoul-gangneung.json"))
        for ((category, spots) in pkg.spots.groupBy { it.category }) {
            val names = spots.map { it.name }
            for ((index, name) in names.withIndex()) {
                assertEquals("$category $name", index, Understanding.choiceOf(name, names))
            }
        }
    }

    @Test
    fun aRefusalOrAnythingElsePicksNothing() {
        for (said in listOf("아니", "괜찮아", "됐어", "글쎄", "")) {
            assertNull(said, Understanding.choiceOf(said, food))
        }
        assertNull(Understanding.choiceOf("1번", emptyList()))
    }

    @Test
    fun aSinoKoreanNumberOnlyCountsAtTheStart() {
        // "이번에" is "this time", not "number two".
        assertEquals(0, Understanding.choiceOf("초당 이번에 가 보자", food))
    }

    @Test
    fun aPlayedWordIsTheLastWordWithoutItsPoliteEnding() {
        val cases = listOf(
            "차고" to "차고", "차고요" to "차고", "음 차고" to "차고", "음... 차고요!" to "차고",
            "책상이요" to "책상", "책상이에요" to "책상", "기차예요" to "기차", "고양이예요" to "고양이",
            "차고입니다" to "차고", "기차 아니 차고" to "차고"
        )
        for ((said, word) in cases) {
            assertEquals(said, word, Understanding.wordOf(said))
        }
    }

    @Test
    fun anEndingIsOnlyDroppedWhenTwoSyllablesAreLeft() {
        // "종이요" is 종이 with 요, not 종 with 이요. "동요" is a word, and the game has no
        // one-syllable words to mistake it for.
        assertEquals("종이", Understanding.wordOf("종이요"))
        assertEquals("동요", Understanding.wordOf("동요"))
        // Casual endings such as 지 are left alone, since many words end like them.
        assertEquals("강아지", Understanding.wordOf("강아지"))
        assertEquals("바지", Understanding.wordOf("바지요"))
    }

    @Test
    fun anEndingOrAFillerSaidApartIsNotTheWord() {
        assertEquals("차고", Understanding.wordOf("차고 요"))
        assertEquals("고양이", Understanding.wordOf("고양이 입니다"))
        assertEquals("차고", Understanding.wordOf("차고 음"))
    }

    @Test
    fun everyBundledWordChainWordIsHeardAsItself() {
        // The host offers these as examples and the scripted passenger plays them. A listed word
        // ending like a polite ending, such as 대중가요, would lose its last syllable.
        for (word in WordList.parse(bundled("games/word-chain.txt"))) {
            assertEquals(word, Understanding.wordOf(word))
        }
    }

    @Test
    fun withoutTwoSyllablesInAWordThereIsNoWord() {
        for (said in listOf("", "차", "음", "음 차", "TV", "...")) {
            assertNull(said, Understanding.wordOf(said))
        }
    }
}
