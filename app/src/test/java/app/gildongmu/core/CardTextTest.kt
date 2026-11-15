package app.gildongmu.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardTextTest {

    private val restaurants = listOf(
        Fixtures.spot("f1", "해돋이식당", 35.0),
        Fixtures.spot("f2", "달맞이분식", 45.0),
        Fixtures.spot("f3", "별빛국수", 55.0)
    ).map { it.copy(area = "바닷가") }

    /** A trip on a road with [spots], the car at 5 km with nothing to say. */
    private fun tripWith(spots: List<Spot>): Harness {
        val h = Harness(Fixtures.route(spots = spots))
        h.startTrip()
        h.jump(5.0)
        return h
    }

    private fun Harness.chip() = CardText.chip(engine.snapshot())

    @Test
    fun theIdleCardCarriesTheAppsName() {
        val h = Harness()
        assertEquals(null, h.engine.snapshot().card)
        assertEquals("길동무", h.chip())
    }

    @Test
    fun aCardSaysWhatKindItIsAndWhere() {
        val idle = Harness().engine.snapshot()
        val story = Card(SegmentKind.STORY, "치악산 이야기", "원주", "", 0.5f)
        assertEquals("장소 이야기 · 원주", CardText.chip(idle.copy(card = story)))
        assertEquals("No dot after a kind that has no place", "장소 이야기", CardText.chip(idle.copy(card = story.copy(subtitle = ""))))
        assertEquals("퀴즈 · 첫째 차례", CardText.chip(idle.copy(card = Card(SegmentKind.QUIZ, "듣고 있어요", "첫째 차례", "", 1f))))
    }

    @Test
    fun aNoticesChipDoesNotRepeatItsTitle() {
        // The host confirms a choice with a notice, whose card is titled with the app's name.
        val h = tripWith(restaurants)
        h.say("뭐 먹지")
        h.finishSpeaking()
        h.say(CardText.CHOICE_EXAMPLE)
        val notice = h.engine.snapshot().card
        assertEquals(SegmentKind.NOTICE, notice?.kind)
        assertEquals("길동무", notice?.title)
        assertEquals("알림", h.chip())
    }

    @Test
    fun aRecommendationKeepsItsNameAndAreaWhileItIsReadAndWhileTheHostWaits() {
        val h = tripWith(restaurants)
        h.say("뭐 먹지")
        assertEquals("먹거리 추천", h.engine.snapshot().card?.title)
        assertEquals("먹거리 추천 · 바닷가", h.chip())

        h.finishSpeaking()
        assertEquals(ListenMode.CHOICE, h.engine.snapshot().listening)
        assertEquals("The title has become what the host is doing", "듣고 있어요", h.engine.snapshot().card?.title)
        assertEquals("먹거리 추천 · 바닷가", h.chip())
    }

    @Test
    fun aRecommendationWithoutAnAreaIsJustItsName() {
        val h = tripWith(restaurants.map { it.copy(area = "") })
        h.say("뭐 먹지")
        assertEquals("먹거리 추천", h.chip())
    }

    @Test
    fun theChipNamesAnAreaOnlyWhenItIsTrueOfEverySpot() {
        /** The chip while the list is read out and while the host waits for the choice. */
        fun chips(vararg areas: String): List<String> {
            val h = tripWith(restaurants.zip(areas.toList()) { spot, area -> spot.copy(area = area) })
            h.say("뭐 먹지")
            assertEquals(areas.toList(), h.engine.snapshot().offers.map { it.area })
            val reading = h.chip()
            h.finishSpeaking()
            assertEquals(ListenMode.CHOICE, h.engine.snapshot().listening)
            return listOf(reading, h.chip())
        }
        val bare = listOf("먹거리 추천", "먹거리 추천")
        val inGangneung = listOf("먹거리 추천 · 강릉", "먹거리 추천 · 강릉")

        // The last place before a rest stop and two at the destination: the first spot's area is not the others'.
        assertEquals(bare, chips("평창 봉평", "강릉", "강릉"))
        assertEquals("Nor is the last one's", bare, chips("강릉", "강릉", "평창 봉평"))
        // A part of a town and the town itself share the town.
        assertEquals(inGangneung, chips("강릉", "강릉", "강릉 주문진"))
        assertEquals(inGangneung, chips("강릉 주문진", "강릉 안목", "강릉"))
        assertEquals("The whole of it when all agree", listOf("먹거리 추천 · 평창 봉평", "먹거리 추천 · 평창 봉평"), chips("평창 봉평", "평창 봉평", "평창 봉평"))
        assertEquals("A spot without an area shares none", bare, chips("강릉", "", "강릉"))
        assertEquals("A word that only begins like another is not shared", bare, chips("강릉", "강릉시", "강릉"))
    }

    @Test
    fun aTipAboutOneSpotSaysItsKindBecauseTheTileSaysItsName() {
        val h = Harness(Fixtures.route(spots = listOf(Fixtures.spot("d1", "노을식당", 99.0, near = Spot.NEAR_DESTINATION).copy(area = "바닷가"))))
        h.startTrip()
        h.jump(60.0)
        h.drive(70.1)
        assertEquals("노을식당", h.engine.snapshot().card?.title)
        assertEquals("먹거리·볼거리 · 바닷가", h.chip())
    }

    @Test
    fun theStateLineDoesNotRepeatTheTitleWhileTheHostListens() {
        val h = tripWith(restaurants)
        assertEquals("", CardText.state(h.engine.snapshot()))

        // While the host waits for the choice, the card's title is "듣고 있어요", but the tiles
        // are where the title would be: the line has to say it.
        h.say("뭐 먹지")
        assertEquals("Nothing while the list is read out", "", CardText.state(h.engine.snapshot()))
        h.finishSpeaking()
        val waiting = h.engine.snapshot()
        assertEquals(ListenMode.CHOICE, waiting.listening)
        assertEquals(CardText.LISTENING, waiting.card?.title)
        assertEquals(3, waiting.offers.size)
        assertEquals("듣고 있어요", CardText.state(waiting))

        // On a card that shows its title, as a quiz question's does, the title has said it.
        val titled = waiting.copy(offers = emptyList())
        assertEquals("", CardText.state(titled))

        // And on a card whose title does not say so, the line does.
        assertEquals("듣고 있어요", CardText.state(titled.copy(card = titled.card?.copy(title = "먹거리 추천"))))
        assertEquals("듣고 있어요", CardText.state(titled.copy(card = null)))
    }

    @Test
    fun theStateLineSaysWhyTheHostIsNotTalking() {
        val idle = Harness().engine.snapshot()
        assertEquals("내비 안내 중 · 잠시 멈춤", CardText.state(idle.copy(holding = true)))
        assertEquals("조용히 가는 중", CardText.state(idle.copy(quiet = true)))
        assertEquals("Waiting for an answer comes first", "듣고 있어요", CardText.state(idle.copy(listening = ListenMode.CHOICE, quiet = true)))
    }

    @Test
    fun whileParkedAQuestionShowsEvenWithCaptionsOff() {
        // Parked at a rest stop: the host greets, then asks whether to tell the next place's story.
        val h = Harness()
        h.startTrip()
        h.jump(30.0)
        h.send(Signal.Parked(true))
        assertNull("The idle card has no caption", CardText.caption(h.engine.snapshot(), captions = false))
        assertNull(CardText.caption(h.engine.snapshot(), captions = true))

        h.idle(HostConfig().restSettleMs)
        val greeting = h.engine.snapshot()
        assertEquals("잠깐 쉬어 가요.", greeting.card?.caption)
        assertNull("What is only being said is still for the ear", CardText.caption(greeting, captions = false))
        assertEquals("잠깐 쉬어 가요.", CardText.caption(greeting, captions = true))

        h.finishSpeaking()
        val asked = h.engine.snapshot()
        assertEquals(ListenMode.YES_NO, asked.listening)
        assertTrue(asked.parked)
        assertEquals("쉬는 동안, 곧 지나갈 다라 이야기를 들을까요?", CardText.caption(asked, captions = false))
        assertEquals("쉬는 동안, 곧 지나갈 다라 이야기를 들을까요?", CardText.caption(asked, captions = true))
    }

    @Test
    fun whileTheCarMovesTheCaptionFollowsItsSwitchAlone() {
        val h = Harness()
        h.startTrip()
        h.drive(16.2)
        val story = h.engine.snapshot()
        assertEquals("가나 긴 이야기 하나.", story.card?.caption)
        assertNull(CardText.caption(story, captions = false))
        assertEquals("가나 긴 이야기 하나.", CardText.caption(story, captions = true))

        // A question asked on the road is heard, not read.
        h.say("퀴즈")
        h.finishSpeaking()
        val asked = h.engine.snapshot()
        assertEquals(ListenMode.QUIZ_ANSWER, asked.listening)
        assertFalse(asked.parked)
        assertNull(CardText.caption(asked, captions = false))
        assertEquals(asked.card?.caption, CardText.caption(asked, captions = true))
        assertTrue("The question is the caption: ${asked.card?.caption}", asked.card?.caption.orEmpty().endsWith("?"))
    }

    @Test
    fun whatTheHintTellsAPassengerToSayChoosesTheFirstSpot() {
        assertEquals("고르는 건 말로: “첫 번째 들르자” → 내비에 경유지 추가", CardText.CHOICE_HINT)
        assertEquals("첫 번째 들르자", CardText.CHOICE_EXAMPLE)

        // The host hears a choice once it has asked for one, at the end of the list.
        val h = tripWith(restaurants)
        h.say("뭐 먹지")
        h.finishSpeaking()
        assertEquals(ListenMode.CHOICE, h.engine.snapshot().listening)
        h.say(CardText.CHOICE_EXAMPLE)
        assertEquals(restaurants[0].toWaypoint(), h.last<Action.AddWaypoint>()?.waypoint)
        assertEquals("해돋이식당", h.engine.snapshot().waypointName)
    }
}
