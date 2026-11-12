package app.gildongmu.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KoreanTest {

    @Test
    fun particlesFollowTheFinalConsonant() {
        assertEquals("횡성휴게소가", Korean.iGa("횡성휴게소"))
        assertEquals("오죽헌이", Korean.iGa("오죽헌"))
        assertEquals("횡성휴게소를", Korean.eulReul("횡성휴게소"))
        assertEquals("오죽헌을", Korean.eulReul("오죽헌"))
        assertEquals("첫째는", Korean.eunNeun("첫째"))
        assertEquals("강릉은", Korean.eunNeun("강릉"))
        assertEquals("세종대왕이에요", Korean.ieyo("세종대왕"))
        assertEquals("율곡 이이예요", Korean.ieyo("율곡 이이"))
        assertEquals("첫째와", Korean.waGwa("첫째"))
        assertEquals("강릉과", Korean.waGwa("강릉"))
    }

    @Test
    fun roTreatsRieulLikeAVowel() {
        assertEquals("강릉으로", Korean.ro("강릉"))
        assertEquals("서울로", Korean.ro("서울"))
        assertEquals("횡성휴게소로", Korean.ro("횡성휴게소"))
    }

    @Test
    fun digitsTakeTheParticleOfTheirReading() {
        assertEquals("832예요", Korean.ieyo("832")) // 이
        assertEquals("1936이에요", Korean.ieyo("1936")) // 육
        assertEquals("10이에요", Korean.ieyo("10")) // 영... read as 십, which also ends in a consonant
        assertEquals("경포해변 (강릉)이에요", Korean.ieyo("경포해변 (강릉)"))
    }

    @Test
    fun readsSinoKoreanNumbers() {
        assertEquals(832L, Korean.sinoNumber("팔백삼십이"))
        assertEquals(1936L, Korean.sinoNumber("천구백삼십육"))
        assertEquals(2018L, Korean.sinoNumber("이천십팔"))
        assertEquals(10L, Korean.sinoNumber("십"))
        assertEquals(10000L, Korean.sinoNumber("만"))
        assertEquals(50000L, Korean.sinoNumber("오만"))
        assertEquals(12345L, Korean.sinoNumber("만이천삼백사십오"))
        assertNull("Digit strings are not number words", Korean.sinoNumber("일이삼"))
        assertNull(Korean.sinoNumber("세종"))
    }

    @Test
    fun findsNumbersHoweverTheyAreSaid() {
        assertEquals(setOf(832L), Korean.numbersIn("832미터"))
        assertEquals(setOf(832L), Korean.numbersIn("팔백삼십이 미터요"))
        assertEquals(setOf(1288L), Korean.numbersIn("1,288미터"))
        assertEquals(setOf(10000L), Korean.numbersIn("만 원"))
        assertEquals(setOf(10000L), Korean.numbersIn("만 원짜리요"))
        assertEquals(setOf(50000L), Korean.numbersIn("오만 원"))
        assertEquals(setOf(2L), Korean.numbersIn("두 마리"))
        assertEquals(setOf(2L), Korean.numbersIn("둘이요"))
        assertEquals(setOf(5L), Korean.numbersIn("오 번"))
        assertEquals(setOf(7L), Korean.numbersIn("일곱"))
    }

    @Test
    fun ordinarySyllablesAreNotNumbers() {
        assertTrue(Korean.numbersIn("오죽헌이요").isEmpty())
        assertTrue(Korean.numbersIn("신사임당").isEmpty())
        assertTrue(Korean.numbersIn("세종대왕").isEmpty())
        assertTrue(Korean.numbersIn("이효석").isEmpty())
    }

    @Test
    fun aTrailingIMayBeTheNumberTwoOrTheStartOfAnEnding() {
        // "팔백삼십이요" is "832요" or "830이요"; both readings are kept.
        assertEquals(setOf(832L, 830L), Korean.numbersIn("팔백삼십이요"))
    }

    @Test
    fun coreDropsPoliteEndings() {
        assertEquals("세종대왕", Korean.core("세종대왕이요"))
        assertEquals("오죽헌", Korean.core("오죽헌인 것 같아요"))
        assertEquals("꿩", Korean.core("꿩입니다"))
        assertEquals("세종대왕", Korean.core("세종 대왕!"))
        assertEquals("An utterance that is only an ending is left alone", "요", Korean.core("요"))
    }
}
