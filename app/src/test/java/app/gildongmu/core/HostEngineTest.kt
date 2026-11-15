package app.gildongmu.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HostEngineTest {

    private fun Harness.assertSaid(fragment: String) =
        assertTrue("Expected the host to say something containing \"$fragment\", but it said: $spoken", spoken.any { it.contains(fragment) })

    private fun Harness.assertNeverSaid(fragment: String) =
        assertFalse("The host should not have said \"$fragment\", but it said: $spoken", spoken.any { it.contains(fragment) })

    /** True once the host has started the story about [place] (either version). */
    private fun Harness.toldStory(place: String) = spoken.any { it == "$place 긴 이야기 하나." || it == "$place 짧은 이야기 하나." }

    // ---- departure ----

    @Test
    fun staysSilentUntilNavigationStarts() {
        val h = Harness()
        h.send(Signal.Location(Fixtures.at(0.0), 0.0))
        h.idle(60_000)
        assertTrue(h.actions.isEmpty())
    }

    @Test
    fun briefsOnceWhenTheRouteStarts() {
        val h = Harness()
        h.send(Signal.Location(Fixtures.at(0.0), 90.0))
        h.send(Signal.RouteStarted(distanceKm = 100.0, etaMinutes = 70))
        assertEquals("안녕하세요, 오늘 여행을 함께할 길동무예요.", h.speaking?.text)
        h.finishSpeaking()
        h.assertSaid("도착지까지 약 1시간 10분, 100킬로미터예요.")
        h.assertSaid("가는 길에 가나, 다라, 마바 이야기를 들려드릴게요.")

        val before = h.spoken.size
        h.send(Signal.RouteStarted(distanceKm = 100.0, etaMinutes = 70)) // a re-route
        h.finishSpeaking()
        assertEquals("A re-route must not repeat the briefing", before, h.spoken.size)
    }

    @Test
    fun aLongTripBriefingNamesWhereTheBreakIsLikelyToBe() {
        val h = Harness()
        h.send(Signal.Location(Fixtures.at(0.0), 90.0))
        // 100 km in 200 minutes: two hours in, the car is around the 60 km rest area.
        h.send(Signal.RouteStarted(distanceKm = 100.0, etaMinutes = 200))
        h.finishSpeaking()
        h.assertSaid("2시간쯤 달리면 두리휴게소 근처예요.")
    }

    // ---- place stories ----

    @Test
    fun tellsAPlaceStoryWhenTheCarComesWithinRange() {
        val h = Harness()
        h.startTrip()
        h.drive(15.9)
        assertFalse(h.toldStory("가나"))
        h.drive(16.2) // 4 km before the place
        assertEquals("가나 긴 이야기 하나.", h.speaking?.text)
        h.finishSpeaking()
        h.drive(26.0)
        assertEquals("A story is told once", 1, h.spoken.count { it == "가나 긴 이야기 하나." })
    }

    @Test
    fun picksTheShortVersionWhenTheNextPlaceIsCloseInTime() {
        val h = Harness()
        h.startTrip()
        // 40x compressed: one real second covers 1 km, so the next place is ~30 real seconds away.
        h.drive(16.2, kph = 90.0, timeScale = 40.0)
        assertEquals("가나 짧은 이야기 하나.", h.speaking?.text)
    }

    @Test
    fun skipsAStoryThatCouldNotBeFinishedInTime() {
        val h = Harness()
        h.startTrip()
        // 300x compressed: 7.5 km per real second leaves about four seconds before the next place.
        h.drive(17.0, kph = 90.0, timeScale = 300.0)
        h.finishSpeaking()
        assertFalse(h.toldStory("가나"))
    }

    @Test
    fun tellsTheLongVersionInStandingTraffic() {
        val h = Harness()
        h.startTrip()
        h.drive(16.5, kph = 90.0, timeScale = 300.0) // arrives far too fast for any story
        assertFalse(h.toldStory("가나"))
        h.now += 1000
        h.send(Signal.Location(Fixtures.at(16.5), 0.0)) // and then stops dead
        assertEquals("가나 긴 이야기 하나.", h.speaking?.text)
    }

    @Test
    fun aStoryCanBeAskedForBeforeThePlace() {
        val h = Harness()
        h.startTrip()
        h.drive(5.0)
        h.say("이야기해 줘")
        assertEquals("조금 있으면 지나갈 가나 이야기예요.", h.speaking?.text)
        h.finishSpeaking()
        h.drive(26.0)
        h.finishSpeaking()
        assertEquals("Not told again on arrival", 1, h.spoken.count { it.startsWith("가나 긴 이야기 하나") })
    }

    @Test
    fun saysSoWhenThereAreNoStoriesLeft() {
        val h = Harness()
        h.startTrip()
        h.jump(95.0)
        h.say("얘기해 줘")
        h.assertSaid("준비한 이야기는 모두 들려드렸어요.")
    }

    // ---- navigation prompts and other interruptions ----

    @Test
    fun stopsForANavigationPromptAndRepeatsTheSentenceAfterwards() {
        val h = Harness()
        h.startTrip()
        h.drive(16.2)
        h.finishSentence()
        assertEquals("가나 긴 이야기 둘.", h.speaking?.text)

        h.send(Signal.TurnGuidance(300))
        assertEquals(1, h.count<Action.StopSpeaking>())
        val interrupted = h.spoken.size
        h.idle(5_000)
        assertEquals("Nothing is said while the prompt is due", interrupted, h.spoken.size)

        h.send(Signal.TurnGuidance(null))
        assertEquals("가나 긴 이야기 둘.", h.speaking?.text)
        h.finishSpeaking()
        h.assertSaid("가나 긴 이야기 셋.")
    }

    @Test
    fun aDistantNavigationPromptDoesNotInterrupt() {
        val h = Harness()
        h.startTrip()
        h.drive(16.2)
        h.send(Signal.TurnGuidance(2_000))
        assertEquals(0, h.count<Action.StopSpeaking>())
    }

    @Test
    fun doesNotStartAStoryWhileAPromptIsDue() {
        val h = Harness()
        h.startTrip()
        h.send(Signal.TurnGuidance(200))
        h.drive(17.0)
        assertFalse(h.toldStory("가나"))
        h.send(Signal.TurnGuidance(null))
        assertTrue(h.toldStory("가나"))
    }

    @Test
    fun losingTheAudioChannelPausesAndGettingItBackResumes() {
        val h = Harness()
        h.startTrip()
        h.drive(16.2)
        h.send(Signal.AudioFocus(available = false))
        assertEquals(1, h.count<Action.StopSpeaking>())
        h.send(Signal.AudioFocus(available = true))
        assertEquals("가나 긴 이야기 하나.", h.speaking?.text)
    }

    @Test
    fun aVehicleWarningSilencesTheHostForAWhile() {
        val h = Harness()
        h.startTrip()
        h.drive(16.2)
        h.send(Signal.VehicleWarning(active = true))
        assertEquals(1, h.count<Action.StopSpeaking>())
        val before = h.spoken.size

        h.jump(47.0) // the next place comes into range while the warning is fresh
        h.idle(30_000)
        assertEquals(before, h.spoken.size)

        h.idle(31_000) // a warning that stays on does not silence the whole trip
        assertTrue(h.toldStory("다라"))
    }

    @Test
    fun aWarningThatClearsLetsTheHostSpeakAgainAtOnce() {
        val h = Harness(config = HostConfig(warningQuietMs = 3_600_000))
        h.startTrip()
        h.send(Signal.VehicleWarning(active = true))
        h.drive(17.0)
        assertFalse(h.toldStory("가나"))
        h.send(Signal.VehicleWarning(active = false))
        assertTrue(h.toldStory("가나"))
    }

    @Test
    fun aLateCallbackFromInterruptedSpeechIsIgnored() {
        val h = Harness()
        h.startTrip()
        h.drive(16.2)
        val cutOff = h.speaking!!
        h.send(Signal.SkipPressed)
        val before = h.actions.size
        h.send(Signal.SpeechFinished(cutOff.utteranceId))
        assertEquals(before, h.actions.size)
    }

    @Test
    fun skipDropsWhatIsBeingSaid() {
        val h = Harness()
        h.startTrip()
        h.drive(16.2)
        h.send(Signal.SkipPressed)
        assertEquals(1, h.count<Action.StopSpeaking>())
        h.drive(20.0)
        assertNull("The skipped story is not started again", h.speaking)
    }

    // ---- stop suggestions ----

    @Test
    fun suggestsAStopAfterTwoHoursAndAddsItToTheRouteOnYes() {
        val h = Harness()
        h.startTrip()
        h.jump(22.0)
        h.send(Signal.SkipPressed)
        h.send(Signal.Trip(minutes = 121.0))
        assertEquals("2시간 1분째 달리고 있어요.", h.speaking?.text)
        h.finishSpeaking()
        h.assertSaid("하나휴게소가 8킬로미터 앞이에요.")
        h.assertSaid("들를까요?")
        assertEquals(ListenMode.YES_NO, h.last<Action.Listen>()?.mode)

        h.say("응 들르자")
        assertEquals("하나휴게소", h.last<Action.AddWaypoint>()?.waypoint?.name)
        h.finishSpeaking()
        h.assertSaid("네, 하나휴게소를 경유지로 추가했어요.")
        assertEquals("하나휴게소", h.engine.snapshot().waypointName)

        h.send(Signal.Trip(minutes = 150.0))
        h.idle(1_000)
        assertEquals("No second suggestion while a stop is on the route", 1, h.count<Action.AddWaypoint>())
        assertEquals(1, h.spoken.count { it == "들를까요?" })
    }

    @Test
    fun keepsListeningAfterSomethingThatIsNotAnAnswer() {
        val h = Harness()
        h.startTrip()
        h.jump(22.0)
        h.send(Signal.SkipPressed)
        h.send(Signal.Trip(minutes = 121.0))
        h.finishSpeaking()
        val asked = h.last<Action.Listen>()!!
        assertEquals(1, h.count<Action.Listen>())

        // A recogniser stops once it has heard something, so the host has to ask it to go on.
        h.now += 3_000
        h.say("어디라고?")
        val again = h.last<Action.Listen>()!!
        assertEquals(2, h.count<Action.Listen>())
        assertEquals(ListenMode.YES_NO, again.mode)
        assertEquals("Only what is left of the time", asked.timeoutMs - 3_000, again.timeoutMs)
        assertEquals("The question is still open", 0, h.count<Action.CancelListen>())

        h.say("응")
        assertEquals("하나휴게소", h.last<Action.AddWaypoint>()?.waypoint?.name)
        assertEquals(2, h.count<Action.Listen>())
    }

    @Test
    fun anAcceptedStopGoesToNavigationWithItsPositionAndCharger() {
        val h = Harness()
        h.startTrip()
        h.jump(22.0)
        h.send(Signal.SkipPressed)
        h.send(Signal.Trip(minutes = 121.0))
        h.finishSpeaking()
        h.say("응")
        // Navigation needs the position; the scripted car also needs to know it can charge there.
        assertEquals(Waypoint("s1", "하나휴게소", Fixtures.at(30.0), hasCharger = true), h.last<Action.AddWaypoint>()?.waypoint)
    }

    @Test
    fun anAcceptedStopIsDroppedOnceTheCarHasDrivenPastIt() {
        val h = Harness()
        h.startTrip()
        h.jump(22.0)
        h.send(Signal.SkipPressed)
        h.send(Signal.Trip(minutes = 121.0))
        h.finishSpeaking()
        h.say("응")
        h.finishSpeaking()

        h.jump(31.9)
        assertEquals("Just past the rest area: maybe still pulling in", "하나휴게소", h.engine.snapshot().waypointName)
        h.jump(32.1)
        assertNull("More than two kilometres past it without stopping", h.engine.snapshot().waypointName)
    }

    @Test
    fun aStopSuggestionOutranksAStory() {
        val h = Harness()
        h.startTrip()
        h.send(Signal.Trip(minutes = 121.0))
        h.jump(16.5) // in range of the place at 20 km, and 13.5 km before the rest area
        assertEquals("2시간 1분째 달리고 있어요.", h.speaking?.text)
    }

    @Test
    fun aDeclinedStopIsNotOfferedAgainAndTheNextWaitsForTheCooldown() {
        val h = Harness()
        h.startTrip()
        h.jump(27.0)
        h.send(Signal.SkipPressed)
        h.send(Signal.Trip(minutes = 121.0))
        h.finishSpeaking()
        h.say("아니 괜찮아")
        assertEquals("알겠어요.", h.speaking?.text)
        h.finishSpeaking()
        h.assertSaid("계속 갈게요.")
        assertEquals(0, h.count<Action.AddWaypoint>())

        h.jump(46.0) // 14 km before the next rest area; the 50 km place is in range too
        h.send(Signal.Trip(minutes = 130.0))
        h.finishSpeaking()
        assertEquals("Still within the cooldown", 1, h.spoken.count { it == "들를까요?" })

        h.jump(55.0)
        h.send(Signal.Trip(minutes = 142.0))
        h.finishSpeaking()
        h.assertSaid("두리휴게소가 5킬로미터 앞이에요.")
    }

    @Test
    fun noAnswerToAStopSuggestionMeansDriveOn() {
        val h = Harness()
        h.startTrip()
        h.jump(27.0)
        h.send(Signal.SkipPressed)
        h.send(Signal.Trip(minutes = 121.0))
        h.finishSpeaking()
        h.say("오늘 날씨 좋다") // not an answer
        assertEquals(0, h.count<Action.CancelListen>())
        h.idle(8_000)
        h.finishSpeaking()
        h.assertSaid("대답이 없어서 계속 갈게요.")
        assertEquals(0, h.count<Action.AddWaypoint>())
    }

    @Test
    fun aLowBatteryStopMustHaveACharger() {
        val h = Harness()
        h.startTrip()
        h.jump(52.0) // 8 km before the rest area without a charger
        h.send(Signal.SkipPressed)
        h.send(Signal.Battery(percent = 18, charging = false))
        h.idle(1_000)
        h.assertNeverSaid("배터리")

        h.jump(78.0) // 12 km before the one with a charger; the 80 km place is in range too
        assertEquals("배터리가 18퍼센트 남았어요.", h.speaking?.text)
        h.finishSpeaking()
        h.assertSaid("세모휴게소가 12킬로미터 앞이에요.")
        h.assertSaid("들러서 충전할까요?")
    }

    @Test
    fun aStoryIsKeptShortWhenAStopSuggestionIsAboutToBeDue() {
        val h = Harness()
        h.startTrip()
        // Real-time driving at 90 km/h. At 16.2 km the two-hour mark is 30 seconds away.
        h.drive(15.5)
        h.send(Signal.Trip(minutes = 119.0))
        h.now += 1000
        h.send(Signal.Trip(minutes = 119.0 + 1.0 / 60))
        h.now += 29_000
        h.send(Signal.Trip(minutes = 119.5))
        h.jump(16.2)
        assertEquals("가나 짧은 이야기 하나.", h.speaking?.text)
    }

    // ---- quiet ----

    @Test
    fun quietStopsStoriesButNotStopSuggestionsUntilTheNextStop() {
        val h = Harness()
        h.startTrip()
        h.drive(16.2)
        h.say("조용히 해 줘")
        assertEquals(1, h.count<Action.StopSpeaking>())
        assertEquals("네, 다음 정차까지 조용히 있을게요.", h.speaking?.text)
        h.finishSpeaking()
        assertTrue(h.engine.snapshot().quiet)

        h.jump(47.0) // the next place
        h.idle(1_000)
        assertFalse(h.toldStory("다라"))

        h.send(Signal.Trip(minutes = 121.0))
        h.jump(50.0)
        assertEquals("2시간 1분째 달리고 있어요.", h.speaking?.text)
        h.finishSpeaking()
        h.say("응")
        h.finishSpeaking()

        h.jump(60.0)
        h.send(Signal.Parked(true))
        assertFalse("Quiet ends at the stop", h.engine.snapshot().quiet)
    }

    @Test
    fun askingForAQuizEndsQuiet() {
        val h = Harness(Fixtures.route(listOf(QuizQuestion("g1", "일반 문제?", listOf("동해"), ""))))
        h.startTrip()
        h.say("조용히")
        h.finishSpeaking()
        h.say("퀴즈 하자")
        assertFalse(h.engine.snapshot().quiet)
        h.assertSaid("좋아요, 퀴즈 시간이에요!")
    }

    @Test
    fun resumeEndsQuiet() {
        val h = Harness()
        h.startTrip()
        h.say("쉿")
        h.finishSpeaking()
        h.say("이제 말해도 돼")
        assertFalse(h.engine.snapshot().quiet)
        h.assertSaid("네, 다시 이야기할게요.")
    }

    // ---- quiz ----

    @Test
    fun aQuizAskedForDuringAStoryStartsWhenTheStoryEndsAndTakesTurns() {
        val h = Harness()
        h.startTrip()
        h.drive(16.2)
        h.say("퀴즈 하자")
        assertEquals("The story is not cut off", 0, h.count<Action.StopSpeaking>())
        h.finishSpeaking()

        h.assertSaid("좋아요, 퀴즈 시간이에요!")
        h.assertSaid("첫째 차례!")
        h.assertSaid("가나 문제 하나?")
        assertEquals(listOf("사과"), h.last<Action.Listen>()?.expected)

        h.say("사과요")
        assertEquals(LightCue.CORRECT_ANSWER, h.last<Action.Light>()?.cue)
        h.finishSpeaking()
        h.assertSaid("정답이에요!")
        h.assertSaid("둘째 차례!")
        h.assertSaid("가나 문제 둘?")

        h.say("팔백 미터")
        h.finishSpeaking()
        h.assertSaid("아쉬워요. 정답은 832예요.")
        h.assertSaid("퀴즈 끝! 지금까지 첫째 1점, 둘째 0점이에요.")
        assertEquals(listOf(PlayerScore("첫째", 1), PlayerScore("둘째", 0)), h.engine.snapshot().scores)
        assertEquals("Exactly one correct answer", 1, h.count<Action.Light>())
    }

    @Test
    fun anUnansweredQuestionTimesOutAndGivesTheAnswer() {
        val h = Harness(config = HostConfig(quizQuestionsPerRound = 1))
        h.startTrip()
        h.drive(16.2)
        h.say("퀴즈")
        h.finishSpeaking()
        h.idle(10_000)
        assertEquals(1, h.count<Action.CancelListen>())
        h.finishSpeaking()
        h.assertSaid("시간이 다 됐어요. 정답은 사과예요.")
    }

    @Test
    fun aQuizWithNothingToAskSaysSo() {
        val h = Harness()
        h.startTrip()
        h.say("퀴즈 하자")
        assertEquals("아직 낼 문제가 없어요.", h.speaking?.text)
        h.finishSpeaking()
        h.assertSaid("이야기를 하나 듣고 나서 해요.")
    }

    @Test
    fun offersAQuizAfterALongSilenceButOnlyOncePerStory() {
        val h = Harness(config = HostConfig(quizQuestionsPerRound = 1))
        h.startTrip()
        h.drive(16.2)
        h.finishSpeaking()
        h.idle(179_000)
        h.assertNeverSaid("심심하죠")
        h.idle(2_000)
        h.assertSaid("심심하죠? 퀴즈 하나 할게요.")
        h.finishSpeaking()
        h.say("사과")
        h.finishSpeaking()

        val before = h.spoken.size
        h.idle(400_000)
        assertEquals("No second idle quiz until another story has been told", before, h.spoken.size)
    }

    @Test
    fun quietDuringAQuestionEndsTheQuiz() {
        val h = Harness()
        h.startTrip()
        h.drive(16.2)
        h.say("퀴즈")
        h.finishSpeaking()
        h.say("퀴즈 그만")
        h.finishSpeaking()
        h.assertSaid("네, 다음 정차까지 조용히 있을게요.")
        assertNull(h.engine.snapshot().listening)
        h.assertNeverSaid("둘째 차례")
    }

    // ---- at a stop ----

    @Test
    fun atAStopTheHostOffersTheNextStoryAndTellsTheLongVersionOnYes() {
        val h = Harness()
        h.startTrip()
        h.jump(30.0)
        h.send(Signal.SkipPressed)

        h.send(Signal.Battery(percent = 40, charging = true))
        h.send(Signal.Parked(true))
        assertEquals(true, h.last<Action.ExpandedUi>()?.enabled)
        h.idle(3_000)
        assertNull("Nothing is said while people are getting out", h.speaking)
        h.idle(1_000)
        assertEquals("충전을 시작했어요.", h.speaking?.text)
        h.finishSpeaking()
        h.assertSaid("쉬는 동안, 곧 지나갈 다라 이야기를 들을까요?")

        h.say("응")
        h.finishSpeaking()
        h.assertSaid("다라 긴 이야기 셋.")
        h.assertSaid("이제 퀴즈 결승이에요!")
        h.assertSaid("다라 문제 하나?")
    }

    @Test
    fun anUnansweredOfferAtAStopIsFollowedBySilenceUntilSomeoneAsks() {
        val h = Harness()
        h.startTrip()
        h.jump(30.0)
        h.send(Signal.SkipPressed)
        h.send(Signal.Parked(true))
        h.idle(4_000)
        assertEquals("잠깐 쉬어 가요.", h.speaking?.text)
        h.finishSpeaking()

        h.idle(8_000) // nobody answers: the car may be empty
        val before = h.spoken.size
        h.idle(60_000)
        assertEquals(before, h.spoken.size)

        h.say("이야기해 줘")
        assertEquals("다라 긴 이야기 하나.", h.speaking?.text)
    }

    @Test
    fun drivingOffClosesTheParkedScreenAndWithdrawsAnUnansweredOffer() {
        val h = Harness()
        h.startTrip()
        h.jump(30.0)
        h.send(Signal.SkipPressed)
        h.send(Signal.Parked(true))
        h.idle(4_000)
        h.finishSpeaking()
        assertEquals(ListenMode.YES_NO, h.engine.snapshot().listening)

        h.send(Signal.Parked(false))
        assertEquals(false, h.last<Action.ExpandedUi>()?.enabled)
        assertNull(h.engine.snapshot().listening)
        assertEquals(Phase.DRIVING, h.engine.snapshot().phase)
    }

    @Test
    fun aStopResetsTheTwoHourClock() {
        val h = Harness()
        h.startTrip()
        h.jump(30.0)
        h.send(Signal.SkipPressed)
        h.send(Signal.Trip(minutes = 100.0))
        h.send(Signal.Parked(true))
        h.send(Signal.Parked(false))
        h.jump(48.0)
        h.send(Signal.SkipPressed)
        h.send(Signal.Trip(minutes = 130.0)) // two hours of driving in total, but only 30 minutes since the stop
        h.idle(1_000)
        h.assertNeverSaid("들를까요?")
    }

    // ---- arrival ----

    @Test
    fun arrivalInterruptsWhateverIsPlayingAndNamesTheWinner() {
        val h = Harness()
        h.startTrip()
        h.drive(16.2)
        h.say("퀴즈")
        h.finishSpeaking()
        h.say("사과")
        h.finishSentence() // still giving feedback when the car arrives

        h.send(Signal.Trip(minutes = 75.0, distanceKm = 100.0))
        h.send(Signal.DestinationArrived)
        assertEquals(1, h.count<Action.StopSpeaking>())
        assertEquals(LightCue.ARRIVAL, h.last<Action.Light>()?.cue)
        assertEquals("도착했어요. 도착지예요.", h.speaking?.text)
        h.finishSpeaking()
        h.assertSaid("오늘 약 100킬로미터를 1시간 15분 동안 함께 달렸어요.")
        h.assertSaid("가는 길에 1곳의 이야기를 들었어요.")
        h.assertSaid("오늘의 퀴즈 왕은 첫째예요.")
        assertEquals(Phase.ARRIVED, h.engine.snapshot().phase)

        val before = h.spoken.size
        h.send(Signal.DestinationArrived)
        h.idle(5_000)
        assertEquals("The wrap-up is said once", before, h.spoken.size)
    }

    @Test
    fun aTiedQuizHasJointWinners() {
        val h = Harness()
        h.startTrip()
        h.drive(16.2)
        h.say("퀴즈")
        h.finishSpeaking()
        h.say("사과")
        h.finishSpeaking()
        h.say("832미터")
        h.finishSpeaking()
        h.send(Signal.DestinationArrived)
        h.finishSpeaking()
        h.assertSaid("퀴즈는 첫째와 둘째가 공동 우승이에요.")
    }

    @Test
    fun withNoScoresTheWrapUpLeavesTheQuizOut() {
        val h = Harness()
        h.startTrip()
        h.send(Signal.DestinationArrived)
        h.finishSpeaking()
        h.assertNeverSaid("퀴즈 왕")
        h.assertNeverSaid("공동 우승")
        h.assertSaid("다음 여행에서 또 만나요.")
    }

    // ---- what the screen is told ----

    @Test
    fun theSnapshotDescribesTheCardAndThePlaces() {
        val h = Harness()
        h.startTrip()
        val idle = h.engine.snapshot()
        assertNull(idle.card)
        assertEquals("가나 · 20km", idle.upNext)
        assertEquals(100.0, idle.remainingKm, 0.01)

        h.drive(16.2)
        val telling = h.engine.snapshot()
        val card = telling.card
        assertNotNull(card)
        assertEquals(SegmentKind.STORY, card!!.kind)
        assertEquals("가나 이야기", card.title)
        assertEquals("가나 긴 이야기 하나.", card.caption)
        assertEquals(1f / 3f, card.progress, 0.001f)
        assertEquals(PlaceState.TOLD, telling.places.first { it.id == "a" }.state)
        assertEquals(PlaceState.AHEAD, telling.places.first { it.id == "b" }.state)

        h.send(Signal.SkipPressed)
        h.send(Signal.Trip(minutes = 121.0)) // keeps the host busy with a suggestion, not the next story
        h.jump(57.0)
        assertEquals("A place the car went past untold is marked as passed", PlaceState.PASSED, h.engine.snapshot().places.first { it.id == "b" }.state)
    }

    // ---- somewhere to eat or to see ----

    /** Four places to eat along the road, in the order the car reaches them. */
    private val restaurants = listOf(
        Fixtures.spot("f1", "해돋이식당", 35.0),
        Fixtures.spot("f2", "달맞이분식", 45.0),
        Fixtures.spot("f3", "별빛국수", 55.0),
        Fixtures.spot("f4", "구름떡집", 65.0)
    )

    /** A trip on a road with [spots], the car at 5 km with nothing to say. */
    private fun tripWith(spots: List<Spot>): Harness {
        val h = Harness(Fixtures.route(spots = spots))
        h.startTrip()
        h.jump(5.0)
        return h
    }

    @Test
    fun aFoodRequestNamesAtMostThreeSpotsInOrderAndWaitsForAChoice() {
        val h = tripWith(restaurants)
        h.say("뭐 먹지?")
        assertEquals("먹을 만한 곳을 세 군데 골라 봤어요.", h.speaking?.text)
        val card = h.engine.snapshot().card!!
        assertEquals(SegmentKind.RECOMMENDATION, card.kind)
        assertEquals("먹거리 추천", card.title)
        assertEquals("The screen gets the spots being read out", restaurants.take(3), h.engine.snapshot().offers)

        h.finishSpeaking()
        assertEquals(
            listOf(
                "먹을 만한 곳을 세 군데 골라 봤어요.",
                "첫 번째, 해돋이식당.", "해돋이식당 이유예요.",
                "두 번째, 달맞이분식.", "달맞이분식 이유예요.",
                "세 번째, 별빛국수.", "별빛국수 이유예요.",
                "가고 싶은 곳이 있으면 번호나 이름을 말해 주세요."
            ),
            h.spoken.takeLast(8)
        )
        h.assertNeverSaid("구름떡집")
        val listen = h.last<Action.Listen>()!!
        assertEquals(ListenMode.CHOICE, listen.mode)
        assertEquals(HostConfig().choiceTimeoutMs, listen.timeoutMs)
        assertEquals(listOf("해돋이식당", "달맞이분식", "별빛국수", "첫 번째", "두 번째", "세 번째"), listen.expected)

        val waiting = h.engine.snapshot()
        assertEquals(ListenMode.CHOICE, waiting.listening)
        assertEquals(
            Card(SegmentKind.RECOMMENDATION, "듣고 있어요", "먹거리 추천", "가고 싶은 곳이 있으면 번호나 이름을 말해 주세요.", 1f),
            waiting.card
        )
        assertEquals("Still on screen while the passengers decide", restaurants.take(3), waiting.offers)
    }

    @Test
    fun aSpotChosenByNumberOrByNameBecomesTheWaypoint() {
        for ((said, chosen) in listOf("첫 번째" to 0, "1번이요" to 0, "두 번째로 할래" to 1, "별빛국수 가자" to 2)) {
            val h = tripWith(restaurants)
            h.say("뭐 먹지")
            h.finishSpeaking()
            h.say(said)
            val spot = restaurants[chosen]
            assertEquals(said, spot.toWaypoint(), h.last<Action.AddWaypoint>()?.waypoint)
            assertEquals(said, 1, h.count<Action.CancelListen>())
            h.finishSpeaking()
            h.assertSaid("네, ${Korean.eulReul(spot.name)} 경유지로 추가했어요.")
            val after = h.engine.snapshot()
            assertEquals(said, spot.name, after.waypointName)
            assertNull(said, after.listening)
            assertEquals(said, emptyList<Spot>(), after.offers)
        }
    }

    @Test
    fun aRefusalLetsTheRecommendationDrop() {
        for (said in listOf("아니", "괜찮아")) {
            val h = tripWith(restaurants)
            h.say("뭐 먹지")
            h.finishSpeaking()
            h.say(said)
            assertEquals(said, 1, h.count<Action.CancelListen>())
            assertEquals(said, "알겠어요.", h.speaking?.text)
            h.finishSpeaking()
            h.assertSaid("필요하면 또 물어봐 주세요.")
            assertEquals(said, 0, h.count<Action.AddWaypoint>())
            assertNull(said, h.engine.snapshot().waypointName)
        }
    }

    @Test
    fun withNoAnswerTheRecommendationLapses() {
        val h = tripWith(restaurants)
        h.say("뭐 먹지")
        h.finishSpeaking()
        h.idle(HostConfig().choiceTimeoutMs - 1_000)
        assertEquals("Still waiting", ListenMode.CHOICE, h.engine.snapshot().listening)
        h.idle(1_000)
        assertEquals(1, h.count<Action.CancelListen>())
        assertEquals("대답이 없어서 넘어갈게요.", h.speaking?.text)
        h.finishSpeaking()
        assertEquals(0, h.count<Action.AddWaypoint>())
        assertTrue(h.engine.snapshot().offers.isEmpty())
    }

    @Test
    fun keepsListeningForAChoiceAfterSomethingThatIsNotOne() {
        val h = tripWith(restaurants)
        h.say("뭐 먹지")
        h.finishSpeaking()
        val asked = h.last<Action.Listen>()!!
        h.now += 4_000
        h.say("음 글쎄")
        val again = h.last<Action.Listen>()!!
        assertEquals(2, h.count<Action.Listen>())
        assertEquals(ListenMode.CHOICE, again.mode)
        assertEquals("Only what is left of the time", asked.timeoutMs - 4_000, again.timeoutMs)
        assertEquals("The choice is still open", 0, h.count<Action.CancelListen>())

        h.say("달맞이분식")
        assertEquals("f2", h.last<Action.AddWaypoint>()?.waypoint?.id)
    }

    @Test
    fun aSingleSpotIsOfferedWithAYesNoQuestion() {
        val cafe = Fixtures.spot("c1", "바다다방", 40.0, SpotCategory.CAFE)
        val h = tripWith(restaurants + cafe)
        h.say("커피 마시고 싶어")
        h.finishSpeaking()
        assertEquals(listOf("커피 마시기 좋은 곳이 한 군데 있어요.", "바다다방은 바다다방 이유예요.", "들를까요?"), h.spoken.takeLast(3))
        assertEquals(listOf("바다다방", "응", "아니"), h.last<Action.Listen>()?.expected)
        assertEquals("카페 추천", h.engine.snapshot().card?.subtitle)

        h.say("응 좋아")
        assertEquals(cafe.toWaypoint(), h.last<Action.AddWaypoint>()?.waypoint)
    }

    @Test
    fun aRequestThatNamesTheDestinationGetsSpotsThere() {
        val dinner = Fixtures.spot("d1", "노을식당", 99.0, near = Spot.NEAR_DESTINATION)
        val h = tripWith(restaurants + dinner)
        h.say("도착해서 뭐 먹지")
        h.finishSpeaking()
        h.assertSaid("노을식당은 노을식당 이유예요.")
        h.assertNeverSaid("해돋이식당")
        assertEquals(listOf(dinner), h.engine.snapshot().offers)
    }

    @Test
    fun saysSoWhenThereIsNothingToRecommend() {
        val h = Harness()
        h.startTrip()
        h.say("맛집 알려 줘")
        assertEquals("아쉽지만 추천할 먹거리가 없어요.", h.speaking?.text)
        h.finishSpeaking()
        assertEquals("Nothing to choose from, so nothing to wait for", 0, h.count<Action.Listen>())
    }

    @Test
    fun aChosenSpotHoldsBackStopSuggestionsUntilTheCarHasPassedIt() {
        val h = Harness(Fixtures.route(spots = listOf(Fixtures.spot("f1", "해돋이식당", 44.0))))
        h.startTrip()
        h.jump(22.0)
        h.say("배고파")
        h.finishSpeaking()
        h.say("응")
        h.finishSpeaking()
        assertEquals("해돋이식당", h.engine.snapshot().waypointName)

        h.send(Signal.Trip(minutes = 121.0)) // a break is due, and 하나휴게소 is 8 km ahead
        h.idle(1_000)
        h.jump(45.9)
        h.assertNeverSaid("달리고 있어요")
        h.jump(46.1) // more than two kilometres past the spot
        assertNull(h.engine.snapshot().waypointName)
        assertEquals("2시간 1분째 달리고 있어요.", h.speaking?.text)
    }

    @Test
    fun askingForSomewhereToGoEndsQuiet() {
        val sights = listOf(
            Fixtures.spot("v1", "전망대", 40.0, SpotCategory.SIGHT),
            Fixtures.spot("v2", "폭포", 50.0, SpotCategory.SIGHT)
        )
        val h = tripWith(sights)
        h.say("조용히")
        h.finishSpeaking()
        assertTrue(h.engine.snapshot().quiet)
        h.say("볼 거 있어?")
        assertFalse(h.engine.snapshot().quiet)
        assertEquals("구경할 만한 곳을 두 군데 골라 봤어요.", h.speaking?.text)
        assertEquals("볼거리 추천", h.engine.snapshot().card?.title)
    }

    @Test
    fun aStopSuggestionThatIsDueGoesBeforeARequestedRecommendation() {
        val h = Harness(Fixtures.route(spots = restaurants))
        h.startTrip()
        h.drive(16.2)
        h.say("뭐 먹지") // during the story, which is not cut off
        h.send(Signal.Trip(minutes = 121.0))
        h.finishSpeaking()
        h.assertSaid("들를까요?")
        h.assertNeverSaid("골라 봤어요")

        h.say("응")
        h.finishSpeaking()
        assertEquals(0, h.count<Action.StopSpeaking>())
        val suggestion = h.spoken.indexOf("들를까요?")
        val recommendation = h.spoken.indexOf("먹을 만한 곳을 세 군데 골라 봤어요.")
        assertTrue("The suggestion first, then the recommendation: ${h.spoken}", suggestion in 0 until recommendation)
    }

    @Test
    fun aRequestedRecommendationGoesBeforeTheStoryOfThePlaceBeingPassed() {
        val h = Harness(Fixtures.route(spots = restaurants))
        h.startTrip()
        h.send(Signal.TurnGuidance(200))
        h.drive(16.5) // the place at 20 km comes into range while a navigation prompt holds everything
        h.say("뭐 먹지")
        h.send(Signal.TurnGuidance(null))
        assertEquals("먹을 만한 곳을 세 군데 골라 봤어요.", h.speaking?.text)
        h.finishSpeaking()
        h.say("괜찮아")
        h.finishSpeaking()
        assertTrue("The story comes once the passengers have answered", h.toldStory("가나"))
    }

    @Test
    fun skipDropsARecommendationWaitingForAChoice() {
        val h = tripWith(restaurants)
        h.say("뭐 먹지")
        h.finishSpeaking()
        h.send(Signal.SkipPressed)
        assertEquals(1, h.count<Action.CancelListen>())
        assertNull(h.engine.snapshot().listening)
        assertTrue(h.engine.snapshot().offers.isEmpty())
        h.say("첫 번째")
        assertEquals("Too late: the choice is gone", 0, h.count<Action.AddWaypoint>())
    }

    @Test
    fun quietDropsARequestThatIsStillWaiting() {
        val h = Harness(Fixtures.route(spots = restaurants))
        h.startTrip()
        h.drive(16.2)
        h.say("뭐 먹지") // waits for the story to end
        h.say("조용히 해 줘")
        h.finishSpeaking()
        h.say("이제 말해도 돼")
        h.finishSpeaking()
        h.assertNeverSaid("골라 봤어요")
    }

    @Test
    fun atAStopARequestedRecommendationComesBeforeARequestedQuiz() {
        val general = QuizQuestion("g1", "일반 문제 하나?", listOf("하늘"), "설명 g1.")
        val h = Harness(Fixtures.route(generalQuiz = listOf(general), spots = restaurants))
        h.startTrip()
        h.jump(30.0)
        h.send(Signal.Parked(true))
        h.send(Signal.TurnGuidance(200)) // holds everything while both are asked for
        h.say("퀴즈 하자")
        h.say("뭐 먹지")
        h.send(Signal.TurnGuidance(null))
        assertEquals("먹을 만한 곳을 세 군데 골라 봤어요.", h.speaking?.text)
        h.finishSpeaking()
        h.say("괜찮아")
        h.finishSpeaking()
        h.assertSaid("좋아요, 퀴즈 시간이에요!")
    }

    @Test
    fun aNewerRequestReplacesOneStillWaiting() {
        val h = Harness(Fixtures.route(spots = restaurants), words = words)
        h.startTrip()
        h.drive(16.2)
        h.say("뭐 먹지") // both while the story is told, which is not cut off
        h.say("끝말잇기 하자")
        h.finishSpeaking()
        h.assertSaid("좋아요, 끝말잇기 해요!")
        h.assertNeverSaid("골라 봤어요")
    }

    @Test
    fun aWarningOrTheArrivalDropsAChoiceStillOpen() {
        val warned = tripWith(restaurants)
        warned.say("뭐 먹지")
        warned.finishSpeaking()
        warned.send(Signal.VehicleWarning(active = true))
        assertEquals(1, warned.count<Action.CancelListen>())
        assertNull(warned.engine.snapshot().listening)
        warned.say("첫 번째")
        assertEquals(0, warned.count<Action.AddWaypoint>())

        val arrived = tripWith(restaurants)
        arrived.say("뭐 먹지")
        arrived.finishSpeaking()
        arrived.send(Signal.DestinationArrived)
        assertEquals(1, arrived.count<Action.CancelListen>())
        arrived.say("첫 번째")
        assertEquals(0, arrived.count<Action.AddWaypoint>())
    }

    // ---- a tip about the destination ----

    /** Spots at the destination: a restaurant, a lighthouse and, nearest of all, a cafe. */
    private val atTheDestination = listOf(
        Fixtures.spot("d1", "노을식당", 99.0, near = Spot.NEAR_DESTINATION),
        Fixtures.spot("d2", "하얀등대", 97.0, SpotCategory.SIGHT, near = Spot.NEAR_DESTINATION),
        Fixtures.spot("d3", "바다다방", 99.5, SpotCategory.CAFE, near = Spot.NEAR_DESTINATION)
    )

    @Test
    fun mentionsOnePlaceAtTheDestinationOnceWhenItIsTwentyMinutesAway() {
        val h = Harness(Fixtures.route(spots = atTheDestination))
        h.startTrip()
        h.jump(60.0) // past the first two places
        h.drive(69.9) // at 90 km/h, a little over 20 minutes to go
        h.assertNeverSaid("도착해서 들러 볼 만한")
        h.drive(70.1)
        assertEquals("Somewhere to eat or to see, not the cafe", "도착해서 들러 볼 만한 노을식당은 노을식당 이유예요.", h.speaking?.text)
        val tip = h.engine.snapshot()
        assertEquals(SegmentKind.RECOMMENDATION, tip.card?.kind)
        assertEquals(listOf(atTheDestination[0]), tip.offers)
        h.finishSpeaking()
        assertEquals("Nothing to answer", 0, h.count<Action.Listen>())

        h.drive(77.0)
        h.finishSpeaking() // the story of the place at 80 km
        h.drive(95.0)
        h.finishSpeaking()
        assertEquals("Said once a trip", 1, h.spoken.count { it.startsWith("도착해서 들러 볼 만한") })
    }

    @Test
    fun noTipInQuietMode() {
        val h = Harness(Fixtures.route(spots = atTheDestination))
        h.startTrip()
        h.jump(60.0)
        h.say("조용히")
        h.finishSpeaking()
        h.drive(96.0)
        h.assertNeverSaid("도착해서 들러 볼 만한")
    }

    @Test
    fun aPlaceStoryThatIsDueGoesBeforeTheTip() {
        val h = Harness(Fixtures.route(spots = atTheDestination), HostConfig(destinationTipMinutes = 15))
        h.startTrip()
        h.jump(60.0)
        h.drive(75.0)
        h.send(Signal.TurnGuidance(200))
        h.drive(78.0) // in range of the place at 80 km, and under 15 minutes from the destination
        h.send(Signal.TurnGuidance(null))
        assertEquals("마바 긴 이야기 하나.", h.speaking?.text)
        h.finishSpeaking()
        val story = h.spoken.indexOf("마바 긴 이야기 셋.")
        val tip = h.spoken.indexOfFirst { it.startsWith("도착해서 들러 볼 만한") }
        assertTrue("The story first, then the tip: ${h.spoken}", story in 0 until tip)
    }

    @Test
    fun noTipWhileAPlaceStoryIsDueEvenIfThatStoryIsLeftOut() {
        // A place at 66 km with an hour-long story, which never fits: it is due from 62 to 72 km.
        val epic = Poi(
            "e", "사자", "", Fixtures.at(66.0),
            Story("사자 이야기", listOf(StoryVariant(3600, "사자 아주 긴 이야기."))), emptyList(), emptyList()
        )
        val h = Harness(Fixtures.route(spots = atTheDestination).let { route -> route.copy(pois = route.pois + epic) })
        h.startTrip()
        h.jump(60.0)
        h.drive(71.9) // under 20 minutes from the destination since 70 km
        h.assertNeverSaid("도착해서 들러 볼 만한")
        h.drive(72.1) // past the place at 66 km, and the story at 80 km is not due until 76 km
        h.assertNeverSaid("사자 아주 긴 이야기")
        assertEquals("도착해서 들러 볼 만한 노을식당은 노을식당 이유예요.", h.speaking?.text)
    }

    @Test
    fun aRequestedQuizGoesBeforeTheTip() {
        val general = QuizQuestion("g1", "일반 문제 하나?", listOf("하늘"), "설명 g1.")
        val h = Harness(Fixtures.route(generalQuiz = listOf(general), spots = atTheDestination))
        h.startTrip()
        h.jump(60.0)
        h.send(Signal.TurnGuidance(200))
        h.drive(70.5) // the tip is due, but a navigation prompt holds everything
        h.say("퀴즈 하자")
        h.send(Signal.TurnGuidance(null))
        assertEquals("좋아요, 퀴즈 시간이에요!", h.speaking?.text)
        h.finishSpeaking()
        h.say("하늘")
        h.finishSpeaking()
        assertEquals("도착해서 들러 볼 만한 노을식당은 노을식당 이유예요.", h.spoken.last())
    }

    @Test
    fun theTipMustBeOverThreeMinutesBeforeArriving() {
        val h = Harness(Fixtures.route(spots = atTheDestination))
        h.startTrip()
        h.jump(60.0)
        h.send(Signal.TurnGuidance(200))
        h.drive(95.4) // when the prompt is over, three minutes and four seconds from the destination
        h.send(Signal.TurnGuidance(null))
        h.drive(99.9)
        h.assertNeverSaid("도착해서 들러 볼 만한")
    }

    @Test
    fun noTipWhenStoppedAFewMinutesFromTheDestination() {
        val h = Harness(Fixtures.route(spots = atTheDestination))
        h.startTrip()
        h.jump(60.0)
        h.send(Signal.TurnGuidance(200))
        h.drive(96.5)
        h.send(Signal.Location(Fixtures.at(96.5), 0.0)) // waiting at a light, a few minutes from the end
        h.send(Signal.TurnGuidance(null))
        h.idle(10_000)
        h.assertNeverSaid("도착해서 들러 볼 만한")
    }

    @Test
    fun theTipIsLeftOutWhereItWouldRunIntoTheNextStory() {
        val h = Harness(Fixtures.route(spots = atTheDestination))
        h.startTrip()
        h.jump(60.0)
        // Time-compressed like the demo: from 70 km, where the tip is due, the story at 80 km is
        // due within four real seconds, too soon for even the one sentence.
        h.drive(75.9, timeScale = 60.0)
        h.assertNeverSaid("도착해서 들러 볼 만한")
        h.drive(77.0, timeScale = 60.0)
        assertEquals("마바 짧은 이야기 하나.", h.speaking?.text)
        h.finishSpeaking()
        assertEquals("After the story there is room", "도착해서 들러 볼 만한 노을식당은 노을식당 이유예요.", h.spoken.last())
    }

    @Test
    fun aStopSuggestionThatIsDueGoesBeforeTheTip() {
        val h = Harness(Fixtures.route(spots = atTheDestination))
        h.startTrip()
        h.jump(60.0)
        h.drive(69.0)
        h.send(Signal.TurnGuidance(200))
        h.drive(75.5) // under 20 minutes from the destination, and 14.5 km before 세모휴게소
        h.send(Signal.Trip(minutes = 121.0))
        h.send(Signal.TurnGuidance(null))
        assertEquals("2시간 1분째 달리고 있어요.", h.speaking?.text)
        h.finishSpeaking()
        h.say("아니")
        h.finishSpeaking()
        val suggestion = h.spoken.indexOf("들를까요?")
        val tip = h.spoken.indexOfFirst { it.startsWith("도착해서 들러 볼 만한") }
        assertTrue("The suggestion first, then the tip: ${h.spoken}", suggestion in 0 until tip)
    }

    @Test
    fun theTipIsNeverAboutAPlaceThatHasAStory() {
        // Nearest the destination is a restaurant right at the place at 80 km, whose story covers it.
        val spots = listOf(
            Fixtures.spot("same", "마바식당", 80.3, near = Spot.NEAR_DESTINATION),
            Fixtures.spot("other", "언덕식당", 66.0, near = Spot.NEAR_DESTINATION)
        )
        val h = Harness(Fixtures.route(spots = spots))
        h.startTrip()
        h.jump(60.0)
        h.drive(70.1)
        assertEquals("도착해서 들러 볼 만한 언덕식당은 언덕식당 이유예요.", h.speaking?.text)
        h.assertNeverSaid("마바식당")
    }

    @Test
    fun theTipIsNotAboutASpotAlreadyRecommended() {
        val h = Harness(Fixtures.route(spots = atTheDestination))
        h.startTrip()
        h.jump(60.0)
        h.say("도착해서 뭐 먹지") // 노을식당, the only restaurant there
        h.finishSpeaking()
        h.say("아니")
        h.finishSpeaking()
        h.drive(70.1)
        assertEquals("도착해서 들러 볼 만한 하얀등대는 하얀등대 이유예요.", h.speaking?.text)
    }

    // ---- riddles ----

    private val riddles = listOf(
        Fixtures.riddle("r1", "수수께끼 하나?", "비누"),
        Fixtures.riddle("r2", "수수께끼 둘?", "거울"),
        Fixtures.riddle("r3", "넌센스 셋?", "킹콩", kind = QuizKind.NONSENSE),
        Fixtures.riddle("r4", "수수께끼 넷?", "의자")
    )

    @Test
    fun riddlesGoThroughTheQuizWithTurnsPointsAndGameCards() {
        val h = Harness(Fixtures.route(funQuiz = riddles))
        h.startTrip()
        h.say("수수께끼 내 줘")
        assertEquals("좋아요, 수수께끼 시간이에요!", h.speaking?.text)
        val card = h.engine.snapshot().card!!
        assertEquals(SegmentKind.GAME, card.kind)
        assertEquals("수수께끼 1/3", card.title)
        h.finishSpeaking()
        assertEquals(listOf("첫째 차례!", "수수께끼 하나?"), h.spoken.takeLast(2))
        assertEquals(Action.Listen(ListenMode.QUIZ_ANSWER, HostConfig().answerTimeoutMs, listOf("비누")), h.last<Action.Listen>())
        assertEquals(Card(SegmentKind.GAME, "듣고 있어요", "첫째 차례", "수수께끼 하나?", 1f), h.engine.snapshot().card)

        h.say("비누요")
        assertEquals(LightCue.CORRECT_ANSWER, h.last<Action.Light>()?.cue)
        assertEquals(SegmentKind.GAME, h.engine.snapshot().card?.kind)
        h.finishSpeaking()
        h.assertSaid("정답이에요!")
        h.assertSaid("설명 r1.")
        assertEquals(listOf("둘째 차례!", "수수께끼 둘?"), h.spoken.takeLast(2))

        h.say("잘 모르겠어요")
        h.finishSpeaking()
        h.assertSaid("아쉬워요. 정답은 거울이에요.")
        h.assertSaid("넌센스 셋?")
        h.idle(HostConfig().answerTimeoutMs)
        h.finishSpeaking()
        h.assertSaid("시간이 다 됐어요. 정답은 킹콩이에요.")
        assertEquals("수수께끼 끝! 지금까지 첫째 1점, 둘째 0점이에요.", h.spoken.last())
        h.assertNeverSaid("수수께끼 넷?")
        assertEquals(listOf(PlayerScore("첫째", 1), PlayerScore("둘째", 0)), h.engine.snapshot().scores)
    }

    @Test
    fun riddlesAreNotAskedTwiceAndTheHostSaysWhenTheyRunOut() {
        val h = Harness(Fixtures.route(funQuiz = riddles.take(3)), HostConfig(riddlesPerRound = 2))
        h.startTrip()
        h.say("수수께끼 하자")
        h.finishSpeaking()
        h.say("비누")
        h.finishSpeaking()
        h.say("거울")
        h.finishSpeaking()
        h.assertSaid("수수께끼 끝!")

        h.say("수수께끼 하자")
        assertEquals("수수께끼 1/1", h.engine.snapshot().card?.title)
        h.finishSpeaking()
        h.say("킹콩")
        h.finishSpeaking()

        h.say("수수께끼 하자")
        assertEquals("준비한 수수께끼는 모두 냈어요.", h.speaking?.text)
        assertEquals(1, h.spoken.count { it == "수수께끼 하나?" })
    }

    @Test
    fun withoutRiddlesTheHostSaysSo() {
        val h = Harness()
        h.startTrip()
        h.say("넌센스 퀴즈 하자")
        assertEquals("아쉽지만 준비한 수수께끼가 없어요.", h.speaking?.text)
        h.finishSpeaking()
        assertEquals(0, h.count<Action.Listen>())
    }

    @Test
    fun askingForTheGameThatIsGoingOnDoesNotQueueAnother() {
        val riddling = Harness(Fixtures.route(funQuiz = riddles))
        riddling.startTrip()
        riddling.say("수수께끼 하자")
        riddling.say("수수께끼 하자") // while the round's intro is read out
        riddling.finishSpeaking()
        for (answer in listOf("비누", "거울", "킹콩")) {
            riddling.say(answer)
            riddling.finishSpeaking()
        }
        riddling.assertSaid("수수께끼 끝!")
        assertEquals(1, riddling.spoken.count { it == "좋아요, 수수께끼 시간이에요!" })

        val chaining = Harness(config = HostConfig(wordChainRounds = 1), words = words)
        chaining.startTrip()
        chaining.say("끝말잇기 하자")
        chaining.say("끝말잇기 하자") // while the host says its first word
        chaining.finishSpeaking()
        chaining.say("차고")
        chaining.finishSpeaking()
        chaining.assertSaid("오늘은 여기까지 할게요.")
        assertEquals(1, chaining.spoken.count { it.startsWith("좋아요, 끝말잇기 해요!") })
    }

    @Test
    fun askingForAGameEndsQuiet() {
        val h = Harness(Fixtures.route(funQuiz = riddles), words = words)
        h.startTrip()
        h.say("조용히")
        h.finishSpeaking()
        h.say("수수께끼 하자")
        assertFalse(h.engine.snapshot().quiet)
        assertEquals("좋아요, 수수께끼 시간이에요!", h.speaking?.text)
        h.send(Signal.SkipPressed)

        h.say("조용히")
        h.finishSpeaking()
        h.say("끝말잇기 하자")
        assertFalse(h.engine.snapshot().quiet)
        assertEquals("좋아요, 끝말잇기 해요! 제가 먼저 할게요.", h.speaking?.text)
    }

    @Test
    fun aQuizNeverAsksRiddles() {
        val h = Harness(Fixtures.route(funQuiz = riddles))
        h.startTrip()
        h.say("퀴즈 하자")
        assertEquals("Riddles are not quiz questions", "아직 낼 문제가 없어요.", h.speaking?.text)
        h.finishSpeaking()
        h.drive(16.2)
        h.finishSpeaking() // the story of the place at 20 km
        h.idle(181_000)
        h.finishSpeaking()
        h.assertSaid("심심하죠? 퀴즈 하나 할게요.")
        h.assertSaid("가나 문제 하나?")
        h.assertNeverSaid("수수께끼")
    }

    @Test
    fun aRequestedGameWaitsForTheStoryOfThePlaceBeingPassed() {
        val h = Harness(Fixtures.route(funQuiz = riddles))
        h.startTrip()
        h.send(Signal.TurnGuidance(200))
        h.drive(16.5) // the place at 20 km comes into range while a navigation prompt holds everything
        h.say("수수께끼 하자")
        h.send(Signal.TurnGuidance(null))
        assertEquals("가나 긴 이야기 하나.", h.speaking?.text)
        h.finishSpeaking()
        h.assertSaid("좋아요, 수수께끼 시간이에요!")
    }

    // ---- 끝말잇기 ----

    /** The host's words: 기차, 차고, 고래, 내일 (두음법칙 after 고래), 일기, 기린, 인형, and none after 인형. */
    private val words = listOf("기차", "차고", "고래", "내일", "일기", "기린", "인형")

    /** A game of 끝말잇기 that has just begun: the host said 기차, and it is 첫째's turn. */
    private fun wordGame(config: HostConfig = HostConfig()): Harness {
        val h = Harness(config = config, words = words)
        h.startTrip()
        h.say("끝말잇기 하자")
        h.finishSpeaking()
        return h
    }

    @Test
    fun theHostAnswersEachWordWithOneThatFollowsItUntilItRunsOut() {
        val h = wordGame(HostConfig(wordChainRounds = 10))
        assertEquals(listOf("좋아요, 끝말잇기 해요! 제가 먼저 할게요.", "기차!", "첫째 차례! 차로 시작하는 말!"), h.spoken.takeLast(3))
        assertEquals(Action.Listen(ListenMode.WORD, HostConfig().answerTimeoutMs, listOf("차고")), h.last<Action.Listen>())
        assertEquals(Card(SegmentKind.GAME, "듣고 있어요", "첫째 차례", "차로 시작하는 말", 1f), h.engine.snapshot().card)

        h.say("차고")
        assertEquals(SegmentKind.GAME, h.engine.snapshot().card?.kind)
        assertEquals("끝말잇기", h.engine.snapshot().card?.title)
        h.finishSpeaking()
        assertEquals(listOf("고래!", "둘째 차례! 래로 시작하는 말!"), h.spoken.takeLast(2))

        h.say("내일이요")
        h.finishSpeaking()
        assertEquals(listOf("일기!", "첫째 차례! 기로 시작하는 말!"), h.spoken.takeLast(2))
        assertEquals("기차 has been said", listOf("기린"), h.last<Action.Listen>()?.expected)

        h.say("기린")
        h.finishSpeaking()
        assertEquals(listOf("인형!", "둘째 차례! 형으로 시작하는 말!"), h.spoken.takeLast(2))

        h.say("형광등") // not in the host's list, but it follows
        h.finishSpeaking()
        assertEquals("제가 졌어요! 여러분이 이겼어요.", h.spoken.last())
        assertNull(h.engine.snapshot().listening)
    }

    @Test
    fun theHostNeverRepeatsAWordAndLosesInstead() {
        val h = Harness(config = HostConfig(wordChainRounds = 10), words = listOf("기차", "차고", "고기"))
        h.startTrip()
        h.say("끝말잇기 하자")
        h.finishSpeaking()
        h.assertSaid("기차!")
        h.say("차고") // a word the host knows: it may not say it any more
        h.finishSpeaking()
        h.assertSaid("고기!")
        h.say("기러기") // only 기차 starts with 기, and it has been said
        h.finishSpeaking()
        assertEquals("제가 졌어요! 여러분이 이겼어요.", h.spoken.last())
        assertEquals(1, h.spoken.count { it == "기차!" })
    }

    @Test
    fun theWordGameEndsWithPraiseAfterTheRounds() {
        val h = wordGame(HostConfig(wordChainRounds = 2))
        h.say("차고")
        h.finishSpeaking()
        h.say("내일")
        h.finishSpeaking()
        assertEquals("와, 끝말잇기 정말 잘하네요! 오늘은 여기까지 할게요.", h.spoken.last())
        h.assertNeverSaid("일기!")
        assertNull(h.engine.snapshot().listening)
        assertEquals("Two turns, two words", 2, h.count<Action.Listen>())
    }

    @Test
    fun aWordThatDoesNotFollowEndsTheGameAndTheHostGivesAnExample() {
        val h = wordGame()
        h.say("사과")
        assertEquals(1, h.count<Action.CancelListen>())
        h.finishSpeaking()
        assertEquals(
            listOf("아쉬워요! 차로 시작하는 말이어야 해요.", "예를 들면 차고 같은 말이 있어요.", "이번 판은 제가 이겼어요. 다음에 또 해요!"),
            h.spoken.takeLast(3)
        )
        assertNull(h.engine.snapshot().listening)
        h.say("차고")
        h.finishSpeaking()
        assertEquals("The game is over", 1, h.count<Action.Listen>())
    }

    @Test
    fun aWordSaidBeforeEndsTheGame() {
        val h = wordGame()
        h.say("차고")
        h.finishSpeaking()
        h.say("내일")
        h.finishSpeaking()
        h.say("기차")
        h.finishSpeaking()
        assertEquals(
            listOf("아쉬워요! 기차는 벌써 나온 말이에요.", "예를 들면 기린 같은 말이 있어요.", "이번 판은 제가 이겼어요. 다음에 또 해요!"),
            h.spoken.takeLast(3)
        )
        assertNull(h.engine.snapshot().listening)
    }

    @Test
    fun silenceEndsTheWordGame() {
        val h = wordGame()
        h.idle(HostConfig().answerTimeoutMs - 1_000)
        assertEquals("Still waiting", ListenMode.WORD, h.engine.snapshot().listening)
        h.idle(1_000)
        assertEquals(1, h.count<Action.CancelListen>())
        h.finishSpeaking()
        assertEquals(
            listOf("시간이 다 됐어요.", "예를 들면 차고 같은 말이 있어요.", "이번 판은 제가 이겼어요. 다음에 또 해요!"),
            h.spoken.takeLast(3)
        )
        assertNull(h.engine.snapshot().listening)
    }

    @Test
    fun somethingThatIsNotAWordIsWaitedPast() {
        val h = wordGame()
        val asked = h.last<Action.Listen>()!!
        h.now += 3_000
        h.say("음")
        assertEquals(2, h.count<Action.Listen>())
        assertEquals(Action.Listen(ListenMode.WORD, asked.timeoutMs - 3_000, listOf("차고")), h.last<Action.Listen>())
        assertEquals("The turn is still open", 0, h.count<Action.CancelListen>())

        h.say("음 차고요")
        h.finishSpeaking()
        h.assertSaid("고래!")
    }

    @Test
    fun enoughEndsTheWordGameWithoutGoingQuiet() {
        val h = wordGame()
        h.say("그만할래")
        assertEquals(1, h.count<Action.CancelListen>())
        assertEquals("네, 끝말잇기는 여기까지 할게요.", h.speaking?.text)
        h.finishSpeaking()
        val after = h.engine.snapshot()
        assertFalse("Only the game stops", after.quiet)
        assertNull(after.listening)
        h.drive(16.2)
        assertTrue("Stories go on", h.toldStory("가나"))
    }

    @Test
    fun quietDuringTheWordGameEndsItAndGoesQuiet() {
        val h = wordGame()
        h.say("조용히 해 줘")
        h.finishSpeaking()
        h.assertSaid("네, 다음 정차까지 조용히 있을게요.")
        assertTrue(h.engine.snapshot().quiet)
        h.say("차고")
        h.finishSpeaking()
        h.assertNeverSaid("고래!")
        h.say("끝말잇기 하자")
        assertEquals("The game is over, so a new one can start", "좋아요, 끝말잇기 해요! 제가 먼저 할게요.", h.speaking?.text)
    }

    @Test
    fun skipEndsTheWordGameSilently() {
        val h = wordGame()
        h.say("차고") // the host is saying its next word when the skip button is pressed
        h.send(Signal.SkipPressed)
        assertEquals(1, h.count<Action.StopSpeaking>())
        h.finishSpeaking()
        assertEquals("No turn is handed over", 1, h.count<Action.Listen>())
        assertNull(h.engine.snapshot().listening)
        h.assertNeverSaid("둘째 차례!")
        h.say("끝말잇기 하자")
        assertEquals("The game is over, so a new one can start", "좋아요, 끝말잇기 해요! 제가 먼저 할게요.", h.speaking?.text)
    }

    @Test
    fun aWarningOrTheArrivalEndsTheWordGame() {
        val warned = wordGame()
        warned.send(Signal.VehicleWarning(active = true))
        assertEquals(1, warned.count<Action.CancelListen>())
        warned.idle(61_000)
        warned.say("차고")
        warned.finishSpeaking()
        warned.assertNeverSaid("고래!")
        assertNull(warned.engine.snapshot().listening)
        warned.say("끝말잇기 하자")
        assertEquals("The game is over, so a new one can start", "좋아요, 끝말잇기 해요! 제가 먼저 할게요.", warned.speaking?.text)

        val arrived = wordGame()
        arrived.send(Signal.DestinationArrived)
        assertEquals(1, arrived.count<Action.CancelListen>())
        arrived.finishSpeaking()
        arrived.assertSaid("다음 여행에서 또 만나요.")
        arrived.say("차고")
        arrived.finishSpeaking()
        arrived.assertNeverSaid("고래!")
    }

    @Test
    fun withoutAWordListTheHostSaysItCannotPlay() {
        val h = Harness()
        h.startTrip()
        h.say("끝말잇기 하자")
        assertEquals("아쉽지만 끝말잇기는 할 수 없어요.", h.speaking?.text)
        h.finishSpeaking()
        h.assertSaid("낱말 목록을 불러오지 못했어요.")
        assertEquals(0, h.count<Action.Listen>())
    }

    @Test
    fun eachGameOpensWithADifferentWord() {
        val h = wordGame()
        h.say("그만")
        h.finishSpeaking()
        h.say("끝말잇기 또 하자")
        h.finishSpeaking()
        assertEquals(listOf("차고!", "첫째 차례! 고로 시작하는 말!"), h.spoken.takeLast(2))
    }

    @Test
    fun atAStopARequestedGameComesBeforeTheOfferOfAStory() {
        val h = Harness(words = words)
        h.startTrip()
        h.jump(30.0)
        h.send(Signal.Parked(true))
        h.say("끝말잇기 하자")
        assertEquals("좋아요, 끝말잇기 해요! 제가 먼저 할게요.", h.speaking?.text)
    }

    @Test
    fun aWordThatSoundsLikeARequestIsPlayedAsAWord() {
        val cafe = Fixtures.spot("c1", "바다다방", 40.0, SpotCategory.CAFE)
        val h = Harness(Fixtures.route(spots = listOf(cafe)), words = listOf("모카", "페달"))
        h.startTrip()
        h.say("끝말잇기 하자")
        h.finishSpeaking()
        h.assertSaid("모카!")
        h.say("카페") // a word in the game, not a wish for coffee
        h.finishSpeaking()
        h.assertSaid("페달!")
        h.assertNeverSaid("커피 마시기 좋은 곳")
    }
}
