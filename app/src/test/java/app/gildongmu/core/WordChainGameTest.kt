package app.gildongmu.core

import app.gildongmu.core.WordChainGame.Verdict
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WordChainGameTest {

    private fun bundled(): List<String> {
        val file = listOf(File("src/main/assets/games/word-chain.txt"), File("app/src/main/assets/games/word-chain.txt"))
            .firstOrNull { it.exists() } ?: error("Cannot find the bundled word list from ${File(".").absolutePath}")
        return WordList.parse(file.readText())
    }

    @Test
    fun theHostOpensWithAListedWordAndRecordsIt() {
        val game = WordChainGame(listOf("기차", "차고", "고기"), seed = 0)
        assertNull(game.last)
        assertEquals("기차", game.hostMove())
        assertEquals("기차", game.last)
        assertEquals(listOf("기차"), game.used)
    }

    @Test
    fun theHostFollowsTheLastWordUnderTheInitialSoundRuleToo() {
        // After 달력 a word may start with 역, after 도시락 with 낙 and after 코끼리 with 이 (두음법칙).
        val words = listOf("역사", "낙타", "이불", "차고", "자두")
        for ((said, answer) in listOf("기차" to "차고", "달력" to "역사", "도시락" to "낙타", "코끼리" to "이불")) {
            for (seed in 0..4) {
                val game = WordChainGame(words, seed)
                game.play(said)
                assertEquals("After $said with seed $seed", answer, game.hostMove())
            }
        }
    }

    @Test
    fun theHostPrefersAWordThatCanBeAnswered() {
        // 사과 would leave nothing in the list to follow it; 사자 leaves 자두.
        for (seed in 0..9) {
            val game = WordChainGame(listOf("사과", "사자", "자두"), seed)
            game.play("역사")
            assertEquals("Seed $seed", "사자", game.hostMove())
        }
    }

    @Test
    fun anAnswerThatWasAlreadySaidDoesNotCount() {
        // 기차 could only be answered with 차고, which the player opened with; 기린 leaves 인형.
        for (seed in 0..9) {
            val game = WordChainGame(listOf("차고", "고기", "기차", "기린", "인형"), seed)
            game.play("차고")
            assertEquals("고기", game.hostMove())
            game.play("기러기")
            assertEquals("Seed $seed", "기린", game.hostMove())
        }
    }

    @Test
    fun withOnlyDeadEndsLeftTheHostStillAnswers() {
        val game = WordChainGame(listOf("사과", "사슴"), seed = 1)
        game.play("역사")
        assertEquals("사슴", game.hostMove())
    }

    @Test
    fun theHostNeverRepeatsAWordWhoeverSaidIt() {
        // 기차 and 차기 could follow each other for ever.
        val own = WordChainGame(listOf("기차", "차기"), seed = 0)
        assertEquals("기차", own.hostMove())
        own.play("차기")
        assertNull("기차 follows 차기, but the host has said it", own.hostMove())

        val theirs = WordChainGame(listOf("고기", "기차", "차고"), seed = 0)
        theirs.play("기차")
        assertEquals("차고", theirs.hostMove())
        theirs.play("고기")
        assertNull("기차 follows 고기, but a player has said it", theirs.hostMove())
    }

    @Test
    fun theHostHasNoWordWhenNothingListedFollows() {
        val game = WordChainGame(listOf("기차", "차고"), seed = 0)
        game.play("바다")
        assertNull(game.hostMove())
        assertEquals("A move that finds nothing records nothing", listOf("바다"), game.used)
        assertNull(WordChainGame(emptyList(), seed = 0).hostMove())
    }

    @Test
    fun theHostNeverSaysAListEntryItWouldNotAcceptFromAPlayer() {
        for (seed in 0..2) {
            assertEquals("Seed $seed", "차고", WordChainGame(listOf("기 차", "차고", "고기"), seed).hostMove())
        }
    }

    @Test
    fun theSeedPicksTheOpeningWordInListOrder() {
        val words = listOf("기차", "차고", "고기")
        assertEquals(words, (0..2).map { WordChainGame(words, it).hostMove() })
        assertEquals("기차", WordChainGame(words, seed = 3).hostMove())
        assertEquals("고기", WordChainGame(words, seed = -1).hostMove())
        assertEquals("차고", WordChainGame(words, seed = Int.MAX_VALUE).hostMove())
        assertEquals("차고", WordChainGame(words, seed = Int.MIN_VALUE).hostMove())
    }

    @Test
    fun laterMovesVaryEvenWithOneSeedPerGame() {
        // Seed 0 opens with the first candidate; the next move must not take the first one again.
        val game = WordChainGame(listOf("모자", "자동차", "차고", "차표", "고래", "표범"), seed = 0)
        assertEquals("모자", game.hostMove())
        game.play("자동차")
        assertEquals("차표", game.hostMove())
    }

    @Test
    fun theSameSeedAndAnswersAlwaysGiveTheSameGame() {
        val words = listOf(
            "기차", "기린", "차고", "차표", "고기", "고래", "인형", "인사",
            "표범", "형제", "사자", "자두", "두부", "부채", "제비", "비누"
        )
        fun gameWith(seed: Int): List<String> {
            val game = WordChainGame(words, seed)
            while (game.hostMove() != null) {
                val answer = game.hints(max = 1).firstOrNull() ?: break
                game.play(answer)
            }
            return game.used
        }
        assertEquals(gameWith(5), gameWith(5))
        assertEquals("Each seed plays its own game", 5, (0..4).map { gameWith(it) }.toSet().size)
    }

    @Test
    fun aWordIsTwoOrMoreHangulSyllables() {
        val game = WordChainGame(listOf("기차"), seed = 0)
        game.hostMove()
        for (said in listOf(null, "", "차", "TV", "차 고", "차고!", "ㅊㅏ", "차go", "차1")) {
            assertEquals("\"$said\"", Verdict.NOT_A_WORD, game.check(said))
        }
    }

    @Test
    fun aWordSaidBeforeIsAlreadyUsedWhoeverSaidIt() {
        val game = WordChainGame(listOf("기차", "차고", "고기"), seed = 0)
        assertEquals("기차", game.hostMove())
        game.play("차고")
        assertEquals("고기", game.hostMove())
        assertEquals("The player's own word", Verdict.ALREADY_USED, game.check("차고"))
        assertEquals("The host's first word, though it starts with 기", Verdict.ALREADY_USED, game.check("기차"))
        assertEquals("The word just said is a repeat, not a wrong start", Verdict.ALREADY_USED, game.check("고기"))
    }

    @Test
    fun aWordThatDoesNotFollowHasTheWrongStart() {
        val game = WordChainGame(listOf("기차"), seed = 0)
        game.hostMove()
        assertEquals(Verdict.WRONG_START, game.check("자동차"))
        assertEquals(Verdict.WRONG_START, game.check("기러기"))
    }

    @Test
    fun aNewWordThatFollowsIsValidWhetherListedOrNot() {
        val game = WordChainGame(listOf("기차", "차고"), seed = 0)
        assertEquals("Anything may open the game", Verdict.VALID, game.check("바다"))
        assertEquals("기차", game.hostMove())
        assertEquals(Verdict.VALID, game.check("차고"))
        assertEquals("The host cannot tell which words are real", Verdict.VALID, game.check("차례"))
        assertEquals("Checking records nothing", listOf("기차"), game.used)

        val rule = WordChainGame(listOf("기차"), seed = 0)
        rule.play("달력")
        assertEquals("두음법칙", Verdict.VALID, rule.check("역사"))
        assertEquals(Verdict.VALID, rule.check("력사"))
    }

    @Test
    fun onlyAValidWordCanBePlayed() {
        val game = WordChainGame(listOf("기차"), seed = 0)
        game.hostMove()
        for (said in listOf("차", "기차", "자동차")) {
            assertThrows(said, IllegalArgumentException::class.java) { game.play(said) }
        }
        assertEquals(listOf("기차"), game.used)
    }

    @Test
    fun hintsAreUnusedListedWordsThatFollowInListOrder() {
        val game = WordChainGame(listOf("역사", "사과", "사자", "사슴", "사탕", "자원봉사"), seed = 0)
        assertEquals("Before the first word anything goes", listOf("역사", "사과", "사자"), game.hints())
        game.play("달력")
        assertEquals("두음법칙: 력 may become 역", listOf("역사"), game.hints())
        assertEquals("역사", game.hostMove())
        assertEquals(listOf("사과", "사자", "사슴"), game.hints())
        assertEquals(listOf("사과", "사자", "사슴", "사탕"), game.hints(max = 10))
        assertEquals(listOf("사과"), game.hints(max = 1))
        assertEquals(emptyList<String>(), game.hints(max = 0))
        game.play("사자")
        assertEquals("자원봉사", game.hostMove())
        assertEquals("사자 has been said", listOf("사과", "사슴", "사탕"), game.hints())

        val stuck = WordChainGame(listOf("기차"), seed = 0)
        stuck.play("바다")
        assertEquals("Nothing listed starts with 다", emptyList<String>(), stuck.hints())
    }

    @Test
    fun theNextSyllableIsTheLastOneAsWritten() {
        val game = WordChainGame(listOf("기차", "차고"), seed = 0)
        assertNull(game.nextSyllable())
        assertEquals("기차", game.hostMove())
        assertEquals('차', game.nextSyllable())
        game.play("차례")
        assertEquals("Named as written, before 두음법칙", '례', game.nextSyllable())
        assertEquals(Verdict.VALID, game.check("예절"))
    }

    @Test
    fun aLongGameOnTheBundledListNeverRepeatsAndAlwaysFollows() {
        val words = bundled()
        for (seed in listOf(0, 1, 7, 42, Int.MAX_VALUE, Int.MIN_VALUE)) {
            val game = WordChainGame(words, seed)
            // A second game plays the passengers, so they answer from the list as well as the host does.
            val passengers = WordChainGame(words, seed + 1)
            while (true) {
                val previous = game.last
                val word = game.hostMove()
                if (word == null) {
                    assertTrue(
                        "Seed $seed: the host gave up after \"$previous\" with a word left",
                        previous != null && words.none { it !in game.used && WordList.follows(previous, it) }
                    )
                    break
                }
                assertTrue("Seed $seed: \"$word\" is not listed", word in words)
                assertTrue("Seed $seed: \"$word\" does not follow \"$previous\"", previous == null || WordList.follows(previous, word))
                assertEquals("Seed $seed: \"$word\" was said before", 1, game.used.count { it == word })
                for (hint in game.hints()) {
                    assertTrue("Seed $seed: hint \"$hint\" after \"$word\"", hint !in game.used && WordList.follows(word, hint))
                }
                passengers.play(word)
                val answer = passengers.hostMove() ?: break
                assertEquals(Verdict.VALID, game.check(answer))
                game.play(answer)
            }
            assertEquals(game.used, passengers.used)
            assertEquals("Seed $seed: a word was said twice", game.used.size, game.used.toSet().size)
            assertTrue("Seed $seed: a long game, found ${game.used.size} words", game.used.size >= 30)
        }
    }
}
