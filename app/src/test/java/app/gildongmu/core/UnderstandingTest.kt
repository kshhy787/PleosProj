package app.gildongmu.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnderstandingTest {

    private fun text(vararg answers: String) = QuizQuestion("q", "?", answers.toList(), "")

    private fun choice(choices: List<String>, vararg answers: String) = QuizQuestion("q", "?", answers.toList(), "", choices)

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
    fun recognisesYes() {
        for (said in listOf("응", "네", "어", "그래", "좋아요", "응 들르자", "들러 줘", "오케이", "네네", "넵", "응!", "네요")) {
            assertEquals(said, Intent.YES, Understanding.yesNoOf(said))
        }
    }

    @Test
    fun recognisesNo() {
        for (said in listOf("아니", "아니요", "괜찮아", "안 들를래", "그냥 가자", "나중에", "싫어", "됐어", "안 가도 돼")) {
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
}
