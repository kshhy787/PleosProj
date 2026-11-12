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
        assertEquals("하나휴게소", h.last<Action.AddWaypoint>()?.stop?.name)
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
        assertEquals("하나휴게소", h.last<Action.AddWaypoint>()?.stop?.name)
        assertEquals(2, h.count<Action.Listen>())
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
}
