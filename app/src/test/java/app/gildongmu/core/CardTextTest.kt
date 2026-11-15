package app.gildongmu.core

import org.junit.Assert.assertEquals
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
    fun aTipAboutOneSpotSaysItsKindBecauseTheTileSaysItsName() {
        val h = Harness(Fixtures.route(spots = listOf(Fixtures.spot("d1", "노을식당", 99.0, near = Spot.NEAR_DESTINATION).copy(area = "바닷가"))))
        h.startTrip()
        h.jump(60.0)
        h.drive(70.1)
        assertEquals("노을식당", h.engine.snapshot().card?.title)
        assertEquals("먹거리·볼거리 · 바닷가", h.chip())
    }

    @Test
    fun whatTheHintTellsAPassengerToSayChoosesTheFirstSpot() {
        assertTrue(CardText.CHOICE_HINT.contains(CardText.CHOICE_EXAMPLE))

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
