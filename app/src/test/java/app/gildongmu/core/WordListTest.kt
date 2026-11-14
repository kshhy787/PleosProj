package app.gildongmu.core

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class WordListTest {

    private fun bundled(): List<String> {
        val file = listOf(File("src/main/assets/games/word-chain.txt"), File("app/src/main/assets/games/word-chain.txt"))
            .firstOrNull { it.exists() } ?: error("Cannot find the bundled word list from ${File(".").absolutePath}")
        return WordList.parse(file.readText())
    }

    private fun assertRejected(text: String, fragment: String) {
        try {
            WordList.parse(text)
            fail("Expected the word list to be rejected")
        } catch (e: WordListFormatException) {
            assertTrue("Expected \"$fragment\" in: ${e.message}", e.message!!.contains(fragment))
        }
    }

    @Test
    fun readsOneWordPerLineAndSkipsComments() {
        assertEquals(listOf("기차", "차고"), WordList.parse("﻿# 설명\n기차\n\n  차고  \n# 끝\n"))
    }

    @Test
    fun rejectsWordsTheHostCouldNotPlay() {
        assertRejected("기차\n차\n", "line 2: \"차\"")
        assertRejected("기차\nTV\n", "\"TV\"")
        assertRejected("기차\n기 차\n", "\"기 차\"")
        assertRejected("기차\n차고\n기차\n", "listed twice")
        assertRejected("# 비어 있음\n", "empty")
    }

    @Test
    fun initialSoundRuleChangesRieulAndNieun() {
        // ㄹ becomes ㅇ before ㅣ ㅑ ㅕ ㅖ ㅛ ㅠ, and ㄴ before ㅏ ㅐ ㅗ ㅚ ㅜ ㅡ.
        assertEquals('역', Korean.initialForm('력'))
        assertEquals('이', Korean.initialForm('리'))
        assertEquals('용', Korean.initialForm('룡'))
        assertEquals('유', Korean.initialForm('류'))
        assertEquals('예', Korean.initialForm('례'))
        assertEquals('나', Korean.initialForm('라'))
        assertEquals('낙', Korean.initialForm('락'))
        assertEquals('노', Korean.initialForm('로'))
        assertEquals('뇌', Korean.initialForm('뢰'))
        assertEquals('누', Korean.initialForm('루'))
        // ㄴ becomes ㅇ before ㅣ ㅑ ㅕ ㅛ ㅠ.
        assertEquals('여', Korean.initialForm('녀'))
        assertEquals('역', Korean.initialForm('녁'))
        assertEquals('이', Korean.initialForm('니'))
        // Everything else stays as it is.
        assertNull(Korean.initialForm('레'))
        assertNull(Korean.initialForm('나'))
        assertNull(Korean.initialForm('기'))
        assertNull(Korean.initialForm('a'))
    }

    @Test
    fun aWordFollowsOnItsLastSyllableOrItsInitialForm() {
        assertTrue(WordList.follows("기차", "차고"))
        assertTrue(WordList.follows("달력", "역사"))
        assertTrue(WordList.follows("달력", "력사"))
        assertTrue(WordList.follows("코끼리", "이불"))
        assertTrue(WordList.follows("도시락", "낙타"))
        assertFalse(WordList.follows("기차", "자동차"))
        assertFalse(WordList.follows("이불", "이마"))
        assertFalse("One syllable is not a word in this game", WordList.follows("기차", "차"))
        assertEquals(setOf('력', '역'), WordList.nextStarts("달력"))
        assertEquals(setOf('차'), WordList.nextStarts("기차"))
    }

    @Test
    fun theBundledListIsBigEnoughAndHasNoDeadEnds() {
        val words = bundled()
        assertTrue("A few hundred words, found ${words.size}", words.size >= 300)
        // The host only says words that the players can answer from the same list: a word like
        // 구름 or 그릇 would end the game on the spot, which is no fun for a child.
        val starts = words.groupBy { it.first() }
        for (word in words) {
            val next = WordList.nextStarts(word).flatMap { starts[it].orEmpty() }.filter { it != word }
            assertTrue("Nothing in the list follows \"$word\"", next.isNotEmpty())
        }
    }
}
