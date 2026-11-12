package app.gildongmu.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The road-trip host: reads what the car reports and decides what, if anything, to say next.
 *
 * It is a plain state machine with no clock, threads or platform types. Callers feed it
 * [Signal]s with the current time and carry out the [Action]s it returns, which is what lets the
 * same logic run in the car, in the mock drive and in unit tests.
 *
 * The order of precedence is fixed:
 *  1. safety: a vehicle warning or an imminent navigation prompt silences the host
 *  2. a stop suggestion (two hours of driving, or a low battery)
 *  3. a story about the place the car is passing
 *  4. a quiz, when a passenger asks or the cabin has been quiet for a while
 *  5. otherwise, silence
 */
class HostEngine(private val pkg: RoutePackage, private val config: HostConfig = HostConfig()) {

    private companion object {
        /** Stuck in traffic or parked: there is time for the longest story. */
        const val STANDING_STILL_SECONDS = 3600.0

        /** What a yes/no question expects to hear; a recogniser can use it as a hint. */
        val YES_NO_WORDS = listOf("응", "아니")
    }

    private enum class Hold { TURN, AUDIO }

    /** A run of sentences spoken as one unit, and what happens when the last one finishes. */
    private class Segment(
        val kind: SegmentKind,
        val title: String,
        val subtitle: String,
        val sentences: List<String>,
        val onDone: (MutableList<Action>) -> Unit
    ) {
        var index = 0
        var paused = false
    }

    private sealed interface Listening {
        val mode: ListenMode
        val deadlineMs: Long

        /** A yes/no question. [onSilence] runs if nobody answers, which may mean nobody is in the car. */
        class YesNo(
            val kind: SegmentKind,
            val title: String,
            val question: String,
            override val deadlineMs: Long,
            val onYes: (MutableList<Action>) -> Unit,
            val onNo: (MutableList<Action>) -> Unit,
            val onSilence: (MutableList<Action>) -> Unit
        ) : Listening {
            override val mode = ListenMode.YES_NO
        }

        class QuizReply(val question: QuizQuestion, val player: String, override val deadlineMs: Long) : Listening {
            override val mode = ListenMode.QUIZ_ANSWER
        }
    }

    private class QuizRound(val questions: List<QuizQuestion>) {
        var index = 0
    }

    private val geometry = RouteGeometry(pkg.polyline)
    private val poiKm: Map<String, Double> = pkg.pois.associate { it.id to geometry.project(it.location).routeKm }
    private val stopKm: Map<String, Double> = pkg.stops.associate { it.id to geometry.project(it.location).routeKm }
    private val poisInOrder: List<Poi> = pkg.pois.sortedBy { poiKm.getValue(it.id) }
    private val stopsInOrder: List<Stop> = pkg.stops.sortedBy { stopKm.getValue(it.id) }
    private val players: List<String> = config.players.ifEmpty { listOf("가족") }

    // What the car last reported.
    private var nowMs = 0L
    private var phase = Phase.WAITING
    private var progressKm = 0.0
    private var startKm = 0.0
    private var speedKph: Double? = null
    private var tripMinutes = 0.0
    private var odometerKm: Double? = null
    private var routeDistanceKm: Double? = null
    private var etaMinutes: Int? = null
    private var batteryPercent: Int? = null
    private var charging = false
    private var parked = false
    private var expandedUi = false
    private val holds = HashSet<Hold>()
    private var warningSinceMs: Long? = null

    // How fast the trip is actually unfolding, learned from successive reports. In traffic this is
    // slow, in the time-compressed demo it is fast; either way speech takes real time, so "is there
    // room for this story" has to be answered in real seconds.
    private var kmPerMs: Double? = null
    private var lastLocationMs: Long? = null
    private var tripMinutesPerMs: Double? = null
    private var lastTripMs: Long? = null

    // What the host has done so far on this trip.
    private var briefed = false
    private var wrappedUp = false
    private var restOffered = false
    private var restingSinceMs = 0L
    private var quiet = false
    private val told = LinkedHashSet<String>()
    private val passed = HashSet<String>()
    private val asked = HashSet<String>()
    private val scores = LinkedHashMap<String, Int>().apply { players.forEach { put(it, 0) } }
    private var playerTurn = 0
    private val declinedStops = HashSet<String>()
    private var acceptedStop: Stop? = null
    private var lastRestTripMinutes = 0.0
    private var lastSuggestTripMinutes = Double.NEGATIVE_INFINITY
    private var quizRequested = false
    private var storyRequested = false
    private var toldCountAtLastIdleQuiz = 0
    private var lastSegmentEndMs = 0L

    // What the host is doing right now.
    private var segment: Segment? = null
    private var listening: Listening? = null
    private var quiz: QuizRound? = null
    private var utteranceSeq = 0
    private var currentUtteranceId: String? = null

    /** Total length of the route in kilometres. */
    val routeLengthKm: Double get() = geometry.lengthKm

    /**
     * Feeds one signal to the host and returns what it wants done, in order.
     * [nowMs] is any monotonic clock in milliseconds; it only has to keep increasing.
     */
    fun handle(signal: Signal, nowMs: Long): List<Action> {
        if (nowMs > this.nowMs) this.nowMs = nowMs
        val out = ArrayList<Action>()
        when (signal) {
            Signal.Tick -> checkListeningTimeout(out)
            is Signal.RouteStarted -> onRouteStarted(signal)
            is Signal.Location -> onLocation(signal)
            is Signal.Trip -> onTrip(signal)
            is Signal.Battery -> {
                batteryPercent = signal.percent
                charging = signal.charging
            }
            is Signal.Parked -> onParked(signal.parked, out)
            is Signal.TurnGuidance -> {
                val distance = signal.distanceM
                setHold(Hold.TURN, distance != null && distance <= config.turnHoldDistanceM, out)
            }
            is Signal.AudioFocus -> setHold(Hold.AUDIO, !signal.available, out)
            is Signal.VehicleWarning -> onWarning(signal.active, out)
            is Signal.Utterance -> onUtterance(signal.text, out)
            is Signal.SpeechFinished -> onSpeechFinished(signal.utteranceId, out)
            Signal.SkipPressed -> skip(out)
            Signal.DestinationArrived -> onArrived(out)
        }
        decide(out)
        return out
    }

    // ---------------------------------------------------------------------------------------
    // Signals
    // ---------------------------------------------------------------------------------------

    private fun onRouteStarted(signal: Signal.RouteStarted) {
        // A report without figures (navigation that gives none, or a trip started by hand in a
        // test) leaves what is already known alone.
        signal.distanceKm?.let { routeDistanceKm = it }
        signal.etaMinutes?.let { etaMinutes = it }
        if (phase == Phase.WAITING) {
            phase = Phase.DRIVING
            startKm = progressKm
            lastSegmentEndMs = nowMs
        }
    }

    private fun onTrip(signal: Signal.Trip) {
        val lastMs = lastTripMs
        if (lastMs != null && nowMs > lastMs) {
            val rate = max(0.0, signal.minutes - tripMinutes) / (nowMs - lastMs)
            tripMinutesPerMs = smooth(tripMinutesPerMs, rate)
        }
        lastTripMs = nowMs
        tripMinutes = signal.minutes
        if (signal.distanceKm != null) odometerKm = signal.distanceKm
    }

    private fun onLocation(signal: Signal.Location) {
        val previousKm = progressKm
        progressKm = geometry.project(signal.point, progressKm).routeKm
        speedKph = signal.speedKph
        val lastMs = lastLocationMs
        if (lastMs != null && nowMs > lastMs) {
            val rate = max(0.0, progressKm - previousKm) / (nowMs - lastMs)
            kmPerMs = smooth(kmPerMs, rate)
        }
        lastLocationMs = nowMs

        for (poi in poisInOrder) {
            if (poi.id !in told && progressKm > poiKm.getValue(poi.id) + config.storyTrailKm) passed.add(poi.id)
        }
        val accepted = acceptedStop
        if (accepted != null && progressKm > stopKm.getValue(accepted.id) + config.stopLeadMinKm) acceptedStop = null
    }

    private fun onParked(nowParked: Boolean, out: MutableList<Action>) {
        if (nowParked == parked) return
        parked = nowParked
        if (nowParked && phase == Phase.DRIVING) {
            // Any stop counts as the break, whether or not the host suggested it.
            phase = Phase.RESTING
            restingSinceMs = nowMs
            restOffered = false
            lastRestTripMinutes = tripMinutes
            acceptedStop = null
            quiet = false
        } else if (!nowParked && phase == Phase.RESTING) {
            phase = Phase.DRIVING
            // A rest offer nobody answered is withdrawn once the car is moving again.
            val waiting = listening
            if (waiting is Listening.YesNo && waiting.kind == SegmentKind.REST) cancelListening(out)
        }
        if (expandedUi != nowParked) {
            expandedUi = nowParked
            out.add(Action.ExpandedUi(nowParked))
        }
    }

    private fun setHold(reason: Hold, active: Boolean, out: MutableList<Action>) {
        val wasHeld = holds.isNotEmpty()
        if (active) holds.add(reason) else holds.remove(reason)
        val held = holds.isNotEmpty()
        val current = segment ?: return
        if (!wasHeld && held && !current.paused) {
            current.paused = true
            currentUtteranceId = null
            out.add(Action.StopSpeaking)
        } else if (wasHeld && !held && current.paused) {
            // Say the interrupted sentence again from its start.
            current.paused = false
            speakCurrent(out)
        }
    }

    private fun onWarning(active: Boolean, out: MutableList<Action>) {
        if (!active) {
            warningSinceMs = null
            return
        }
        if (warningSinceMs == null) {
            warningSinceMs = nowMs
            abandonSegment(out)
            cancelListening(out)
            quiz = null
        }
    }

    private fun warningQuiet(): Boolean {
        val since = warningSinceMs ?: return false
        return nowMs - since < config.warningQuietMs
    }

    private fun onUtterance(text: String, out: MutableList<Action>) {
        val command = Understanding.commandOf(text)
        // These two apply whatever the host is doing, even in the middle of a question.
        if (command == Intent.QUIET) {
            goQuiet(out)
            return
        }
        if (command == Intent.SKIP) {
            skip(out)
            return
        }

        when (val waiting = listening) {
            is Listening.YesNo -> when (Understanding.yesNoOf(text)) {
                Intent.YES -> {
                    cancelListening(out)
                    waiting.onYes(out)
                }
                Intent.NO -> {
                    cancelListening(out)
                    waiting.onNo(out)
                }
                else -> {
                    // Not an answer. A speech recogniser stops once it has heard something, so
                    // ask for the rest of the time again; the deadline itself does not move.
                    val leftMs = waiting.deadlineMs - nowMs
                    if (leftMs > 0) out.add(Action.Listen(ListenMode.YES_NO, leftMs, YES_NO_WORDS))
                }
            }
            is Listening.QuizReply -> grade(waiting, text, out)
            null -> when (command) {
                Intent.RESUME -> if (quiet) {
                    quiet = false
                    if (segment == null) notice("네, 다시 이야기할게요.", out)
                }
                Intent.QUIZ -> if (quiz == null) {
                    quizRequested = true
                    quiet = false
                }
                Intent.STORY -> {
                    storyRequested = true
                    quiet = false
                }
                else -> {}
            }
        }
    }

    private fun onSpeechFinished(utteranceId: String, out: MutableList<Action>) {
        if (utteranceId != currentUtteranceId) return // A stale callback from speech that was cut off.
        val current = segment ?: return
        current.index++
        if (current.index < current.sentences.size) {
            speakCurrent(out)
        } else {
            segment = null
            currentUtteranceId = null
            lastSegmentEndMs = nowMs
            current.onDone(out)
        }
    }

    private fun onArrived(out: MutableList<Action>) {
        if (phase == Phase.ARRIVED) return
        phase = Phase.ARRIVED
        quiet = false
        quiz = null
        quizRequested = false
        storyRequested = false
        abandonSegment(out)
        cancelListening(out)
    }

    private fun checkListeningTimeout(out: MutableList<Action>) {
        val waiting = listening ?: return
        if (nowMs < waiting.deadlineMs) return
        when (waiting) {
            is Listening.YesNo -> {
                cancelListening(out)
                waiting.onSilence(out)
            }
            is Listening.QuizReply -> grade(waiting, null, out)
        }
    }

    // ---------------------------------------------------------------------------------------
    // Deciding what to say
    // ---------------------------------------------------------------------------------------

    private fun decide(out: MutableList<Action>) {
        if (segment != null || listening != null) return
        if (holds.isNotEmpty() || warningQuiet()) return
        when (phase) {
            Phase.WAITING -> {}
            Phase.DRIVING -> {
                if (!briefed) {
                    startBriefing(out)
                    return
                }
                if (suggestStop(out)) return
                if (quiet) return // "조용히": only stop suggestions get through until the next stop.
                if (storyRequested) {
                    tellRequestedStory(out)
                    return
                }
                if (tellStory(out)) return
                if (quizDue()) startQuiz(isFinal = false, out = out)
            }
            Phase.RESTING -> {
                if (storyRequested) {
                    tellRequestedStory(out)
                } else if (quizRequested) {
                    startQuiz(isFinal = false, out = out)
                } else if (!restOffered && nowMs - restingSinceMs >= config.restSettleMs) {
                    restOffered = true
                    offerRestStory(out)
                }
            }
            Phase.ARRIVED -> if (!wrappedUp) {
                wrappedUp = true
                startWrapUp(out)
            }
        }
    }

    private fun startBriefing(out: MutableList<Action>) {
        briefed = true
        val km = routeDistanceKm ?: (geometry.lengthKm - progressKm)
        val minutes = etaMinutes ?: (km / config.assumedSpeedKph * 60).roundToInt()
        val sentences = ArrayList<String>()
        sentences.add("안녕하세요, 오늘 여행을 함께할 길동무예요.")
        sentences.add("${pkg.destination.name}까지 약 ${formatDuration(minutes.toDouble())}, ${formatKm(km)}킬로미터예요.")

        val teasers = poisInOrder.filter { poiKm.getValue(it.id) > progressKm }.take(3).map { it.name }
        if (teasers.isNotEmpty()) sentences.add("가는 길에 ${teasers.joinToString(", ")} 이야기를 들려드릴게요.")

        if (minutes >= config.restAfterMinutes + 15 && stopsInOrder.isNotEmpty()) {
            val averageKph = km / (minutes / 60.0)
            val kmWhenRestIsDue = progressKm + averageKph * config.restAfterMinutes / 60.0
            val stop = stopsInOrder.minByOrNull { abs(stopKm.getValue(it.id) - kmWhenRestIsDue) }!!
            sentences.add("${formatDuration(config.restAfterMinutes.toDouble())}쯤 달리면 ${stop.name} 근처예요. 그때 쉬어 갈지 물어볼게요.")
        }
        sentences.add("퀴즈가 하고 싶으면 퀴즈 하자, 조용히 가고 싶으면 조용히 라고 말해 주세요.")
        start(Segment(SegmentKind.BRIEFING, pkg.title, "출발", sentences) {}, out)
    }

    private fun suggestStop(out: MutableList<Action>): Boolean {
        if (acceptedStop != null) return false
        if (tripMinutes - lastSuggestTripMinutes < config.suggestCooldownMinutes) return false

        val sinceRest = tripMinutes - lastRestTripMinutes
        val restDue = sinceRest >= config.restAfterMinutes
        val battery = batteryPercent
        val chargeDue = battery != null && battery <= config.lowBatteryPercent && !charging
        if (!restDue && !chargeDue) return false

        val stop = stopsInOrder.firstOrNull { candidate ->
            val ahead = stopKm.getValue(candidate.id) - progressKm
            candidate.id !in declinedStops &&
                ahead >= config.stopLeadMinKm && ahead <= config.stopLeadMaxKm &&
                (!chargeDue || candidate.hasCharger)
        } ?: return false

        val ahead = (stopKm.getValue(stop.id) - progressKm).roundToInt()
        val question = if (chargeDue) "들러서 충전할까요?" else "들를까요?"
        val sentences = ArrayList<String>()
        if (chargeDue) sentences.add("배터리가 ${battery}퍼센트 남았어요.")
        if (restDue) sentences.add("${formatDuration(sinceRest)}째 달리고 있어요.")
        sentences.add("${Korean.iGa(stop.name)} ${ahead}킬로미터 앞이에요.")
        sentences.add(question)

        lastSuggestTripMinutes = tripMinutes
        start(Segment(SegmentKind.STOP_SUGGESTION, stop.name, "${ahead}km 앞", sentences) { done ->
            askYesNo(
                kind = SegmentKind.STOP_SUGGESTION,
                title = stop.name,
                question = question,
                onYes = { yes ->
                    acceptedStop = stop
                    yes.add(Action.AddWaypoint(stop))
                    notice("네, ${Korean.eulReul(stop.name)} 경유지로 추가했어요.", yes)
                },
                onNo = { no ->
                    declinedStops.add(stop.id)
                    notice("알겠어요. 계속 갈게요.", no)
                },
                onSilence = { silence ->
                    declinedStops.add(stop.id)
                    notice("대답이 없어서 계속 갈게요.", silence)
                },
                out = done
            )
        }, out)
        return true
    }

    private fun tellStory(out: MutableList<Action>): Boolean {
        val poi = poisInOrder.firstOrNull { candidate ->
            val km = poiKm.getValue(candidate.id)
            candidate.id !in told && candidate.id !in passed &&
                progressKm >= km - config.storyLeadKm && progressKm <= km + config.storyTrailKm
        } ?: return false

        val variant = chooseVariant(poi.story, storyWindowSeconds(poi)) ?: return false
        told.add(poi.id)
        start(Segment(SegmentKind.STORY, poi.story.title, placeLabel(poi), Speech.sentences(variant.text)) {}, out)
        return true
    }

    /** A passenger asked for a story: tell the next one on the route, even if the place is still ahead. */
    private fun tellRequestedStory(out: MutableList<Action>) {
        storyRequested = false
        val poi = nextUntoldPoi()
        if (poi == null) {
            notice("준비한 이야기는 모두 들려드렸어요.", out)
            return
        }
        if (phase == Phase.RESTING) {
            tellRestStory(poi, out)
            return
        }
        val variant = chooseVariant(poi.story, storyWindowSeconds(poi)) ?: poi.story.shortest
        val sentences = ArrayList<String>()
        if (poiKm.getValue(poi.id) - progressKm > config.storyLeadKm) sentences.add("조금 있으면 지나갈 ${poi.name} 이야기예요.")
        sentences.addAll(Speech.sentences(variant.text))
        told.add(poi.id)
        start(Segment(SegmentKind.STORY, poi.story.title, placeLabel(poi), sentences) {}, out)
    }

    private fun nextUntoldPoi(): Poi? = poisInOrder.firstOrNull {
        it.id !in told && it.id !in passed && progressKm <= poiKm.getValue(it.id) + config.storyTrailKm
    }

    /** The longest version that fits in [windowSeconds]; the shortest if it nearly fits; else none. */
    private fun chooseVariant(story: Story, windowSeconds: Double): StoryVariant? {
        val fitting = story.variants.filter { it.seconds * config.pacingMargin <= windowSeconds }.maxByOrNull { it.seconds }
        if (fitting != null) return fitting
        val shortest = story.shortest
        val enough = max(config.minStoryWindowSeconds, shortest.seconds * 0.6)
        return if (windowSeconds >= enough) shortest else null
    }

    /** Real seconds from now until the host next has something else it must say. */
    private fun storyWindowSeconds(current: Poi): Double {
        val limits = ArrayList<Double>()
        limits.add(geometry.lengthKm)
        for (poi in poisInOrder) {
            if (poi.id == current.id || poi.id in told || poi.id in passed) continue
            limits.add(poiKm.getValue(poi.id) - config.storyLeadKm)
        }
        acceptedStop?.let { limits.add(stopKm.getValue(it.id)) }
        nextSuggestionKm()?.let { limits.add(it) }
        val limitKm = limits.filter { it > progressKm }.minOrNull() ?: progressKm
        return realSecondsUntil(limitKm)
    }

    /** Where along the route the next rest suggestion would be made, if one is coming. */
    private fun nextSuggestionKm(): Double? {
        if (acceptedStop != null) return null
        val minutesUntilDue = max(0.0, lastRestTripMinutes + config.restAfterMinutes - tripMinutes)
        val kmWhenDue = progressKm + kmPerTripMinute() * minutesUntilDue
        val stop = stopsInOrder.firstOrNull {
            it.id !in declinedStops && stopKm.getValue(it.id) - config.stopLeadMinKm >= kmWhenDue
        } ?: return null
        return max(kmWhenDue, stopKm.getValue(stop.id) - config.stopLeadMaxKm)
    }

    private fun smooth(previous: Double?, sample: Double): Double =
        if (previous == null) sample else previous * 0.7 + sample * 0.3

    private fun reportedSpeedKph(): Double {
        val speed = speedKph
        return if (speed == null || speed < 5.0) config.assumedSpeedKph else speed
    }

    /** Kilometres covered per minute of driving time. */
    private fun kmPerTripMinute(): Double {
        val km = kmPerMs
        val minutes = tripMinutesPerMs
        return if (km != null && minutes != null && minutes > 0.0) km / minutes else reportedSpeedKph() / 60.0
    }

    /** Real seconds until the car reaches [km] at the pace it is actually moving. */
    private fun realSecondsUntil(km: Double): Double {
        val speed = speedKph
        if (speed != null && speed < 1.0) return STANDING_STILL_SECONDS
        val aheadKm = max(0.0, km - progressKm)
        val pace = kmPerMs ?: (reportedSpeedKph() / 3_600_000.0)
        if (pace <= 1e-9) return STANDING_STILL_SECONDS
        return aheadKm / pace / 1000.0
    }

    private fun quizDue(): Boolean {
        if (quiz != null) return false
        if (quizRequested) return true
        val idleMs = nowMs - lastSegmentEndMs
        return idleMs >= config.quizIdleSeconds * 1000L &&
            told.size > toldCountAtLastIdleQuiz &&
            questionPool().isNotEmpty()
    }

    /** Unasked questions: the most recently told place first, then the route's general questions. */
    private fun questionPool(): List<QuizQuestion> {
        val byPlace = told.toList().asReversed().flatMap { id -> pkg.pois.first { it.id == id }.quiz }
        return (byPlace + pkg.generalQuiz).filter { it.id !in asked }
    }

    private fun startQuiz(isFinal: Boolean, out: MutableList<Action>): Boolean {
        val requested = quizRequested
        quizRequested = false
        toldCountAtLastIdleQuiz = told.size
        val questions = questionPool().take(config.quizQuestionsPerRound)
        if (questions.isEmpty()) {
            if (requested) notice("아직 낼 문제가 없어요. 이야기를 하나 듣고 나서 해요.", out)
            return false
        }
        quiz = QuizRound(questions)
        val intro = when {
            isFinal -> "이제 퀴즈 결승이에요!"
            requested -> "좋아요, 퀴즈 시간이에요!"
            else -> "심심하죠? 퀴즈 하나 할게요."
        }
        ask(listOf(intro), out)
        return true
    }

    private fun ask(intro: List<String>, out: MutableList<Action>) {
        val round = quiz ?: return
        val question = round.questions[round.index]
        val player = players[playerTurn % players.size]
        playerTurn++
        asked.add(question.id)

        val sentences = ArrayList(intro)
        sentences.add("$player 차례!")
        sentences.add(question.prompt)
        if (question.choices.isNotEmpty()) {
            sentences.add(question.choices.mapIndexed { i, choice -> "${i + 1}번 $choice" }.joinToString(", ") + ".")
        }
        val title = "퀴즈 ${round.index + 1}/${round.questions.size}"
        start(Segment(SegmentKind.QUIZ, title, "$player 차례", sentences) { done ->
            listening = Listening.QuizReply(question, player, nowMs + config.answerTimeoutMs)
            done.add(Action.Listen(ListenMode.QUIZ_ANSWER, config.answerTimeoutMs, question.answers))
        }, out)
    }

    /** [said] is null when nobody answered in time. */
    private fun grade(waiting: Listening.QuizReply, said: String?, out: MutableList<Action>) {
        cancelListening(out)
        val correct = said != null && Understanding.isCorrect(said, waiting.question)
        val answer = waiting.question.answers.first()
        val sentences = ArrayList<String>()
        when {
            correct -> {
                scores[waiting.player] = scores.getValue(waiting.player) + 1
                out.add(Action.Light(LightCue.CORRECT_ANSWER))
                sentences.add("정답이에요!")
            }
            said == null -> sentences.add("시간이 다 됐어요. 정답은 ${Korean.ieyo(answer)}.")
            else -> sentences.add("아쉬워요. 정답은 ${Korean.ieyo(answer)}.")
        }
        sentences.addAll(Speech.sentences(waiting.question.explanation))
        val title = if (correct) "정답!" else "정답은 $answer"
        start(Segment(SegmentKind.QUIZ, title, waiting.player, sentences) { done -> afterAnswer(done) }, out)
    }

    private fun afterAnswer(out: MutableList<Action>) {
        val round = quiz ?: return
        round.index++
        if (round.index < round.questions.size) {
            ask(emptyList(), out)
            return
        }
        quiz = null
        val standings = scores.entries.joinToString(", ") { "${it.key} ${it.value}점" }
        start(Segment(SegmentKind.QUIZ, "퀴즈 끝", "점수", listOf("퀴즈 끝! 지금까지 ${standings}이에요.")) {}, out)
    }

    /**
     * At a stop the host offers the next story instead of just starting it: people often leave the
     * car at a rest area, and silence then means there is nobody to tell it to.
     */
    private fun offerRestStory(out: MutableList<Action>) {
        val greeting = if (charging) "충전을 시작했어요." else "잠깐 쉬어 가요."
        val next = nextUntoldPoi()
        if (next == null) {
            start(Segment(SegmentKind.REST, "쉬어 가기", "정차 중", listOf(greeting)) {}, out)
            return
        }
        val question = "쉬는 동안, 곧 지나갈 ${next.name} 이야기를 들을까요?"
        start(Segment(SegmentKind.REST, next.story.title, placeLabel(next), listOf(greeting, question)) { done ->
            askYesNo(
                kind = SegmentKind.REST,
                title = next.story.title,
                question = question,
                onYes = { yes -> tellRestStory(next, yes) },
                onNo = { no -> notice("네, 편히 쉬세요.", no) },
                onSilence = {},
                out = done
            )
        }, out)
    }

    /** Parked, so there is time for the long version, and for the quiz final after it. */
    private fun tellRestStory(poi: Poi, out: MutableList<Action>) {
        told.add(poi.id)
        start(Segment(SegmentKind.REST, poi.story.title, placeLabel(poi), Speech.sentences(poi.story.longest.text)) { done ->
            startQuiz(isFinal = true, out = done)
        }, out)
    }

    private fun startWrapUp(out: MutableList<Action>) {
        val km = odometerKm ?: (progressKm - startKm)
        val sentences = ArrayList<String>()
        sentences.add("도착했어요. ${Korean.ieyo(pkg.destination.name)}.")
        // formatKm rounds long distances to the nearest ten, so the figure is said as approximate.
        sentences.add("오늘 약 ${formatKm(km)}킬로미터를 ${formatDuration(tripMinutes)} 동안 함께 달렸어요.")
        if (told.isNotEmpty()) sentences.add("가는 길에 ${told.size}곳의 이야기를 들었어요.")

        val best = scores.values.maxOrNull() ?: 0
        if (best > 0) {
            val winners = scores.filterValues { it == best }.keys.toList()
            sentences.add(
                if (winners.size == 1) {
                    "오늘의 퀴즈 왕은 ${Korean.ieyo(winners.single())}."
                } else {
                    val others = Korean.waGwa(winners.dropLast(1).joinToString(", "))
                    "퀴즈는 $others ${Korean.iGa(winners.last())} 공동 우승이에요."
                }
            )
        }
        sentences.add("함께해서 즐거웠어요. 다음 여행에서 또 만나요.")
        out.add(Action.Light(LightCue.ARRIVAL))
        start(Segment(SegmentKind.WRAP_UP, "오늘의 기록", pkg.destination.name, sentences) {}, out)
    }

    // ---------------------------------------------------------------------------------------
    // Speaking and listening
    // ---------------------------------------------------------------------------------------

    private fun start(next: Segment, out: MutableList<Action>) {
        segment = next
        speakCurrent(out)
    }

    private fun speakCurrent(out: MutableList<Action>) {
        val current = segment ?: return
        if (holds.isNotEmpty()) {
            current.paused = true
            return
        }
        val id = "u${++utteranceSeq}"
        currentUtteranceId = id
        out.add(Action.Speak(id, current.sentences[current.index]))
    }

    private fun notice(text: String, out: MutableList<Action>) {
        start(Segment(SegmentKind.NOTICE, "길동무", "", Speech.sentences(text)) {}, out)
    }

    private fun askYesNo(
        kind: SegmentKind,
        title: String,
        question: String,
        onYes: (MutableList<Action>) -> Unit,
        onNo: (MutableList<Action>) -> Unit,
        onSilence: (MutableList<Action>) -> Unit,
        out: MutableList<Action>
    ) {
        listening = Listening.YesNo(kind, title, question, nowMs + config.yesNoTimeoutMs, onYes, onNo, onSilence)
        out.add(Action.Listen(ListenMode.YES_NO, config.yesNoTimeoutMs, YES_NO_WORDS))
    }

    private fun abandonSegment(out: MutableList<Action>) {
        val current = segment ?: return
        if (!current.paused) out.add(Action.StopSpeaking)
        segment = null
        currentUtteranceId = null
        lastSegmentEndMs = nowMs
    }

    private fun cancelListening(out: MutableList<Action>) {
        if (listening == null) return
        listening = null
        out.add(Action.CancelListen)
    }

    /** The skip button, or "건너뛰어": drop whatever is being said or asked. */
    private fun skip(out: MutableList<Action>) {
        abandonSegment(out)
        cancelListening(out)
        quiz = null
    }

    private fun goQuiet(out: MutableList<Action>) {
        abandonSegment(out)
        cancelListening(out)
        quiz = null
        quizRequested = false
        storyRequested = false
        quiet = true
        notice("네, 다음 정차까지 조용히 있을게요.", out)
    }

    // ---------------------------------------------------------------------------------------
    // For the screen
    // ---------------------------------------------------------------------------------------

    fun snapshot(): HostSnapshot {
        val current = segment
        val waiting = listening
        val card = when {
            current != null -> Card(
                kind = current.kind,
                title = current.title,
                subtitle = current.subtitle,
                caption = current.sentences[current.index.coerceAtMost(current.sentences.size - 1)],
                progress = (current.index + 1).toFloat() / current.sentences.size
            )
            waiting is Listening.QuizReply -> Card(SegmentKind.QUIZ, "듣고 있어요", "${waiting.player} 차례", waiting.question.prompt, 1f)
            waiting is Listening.YesNo -> Card(waiting.kind, "듣고 있어요", waiting.title, waiting.question, 1f)
            else -> null
        }
        return HostSnapshot(
            phase = phase,
            card = card,
            listening = waiting?.mode,
            quiet = quiet,
            holding = holds.isNotEmpty() || warningQuiet(),
            expandedUi = expandedUi,
            destinationName = pkg.destination.name,
            progressKm = progressKm,
            remainingKm = max(0.0, geometry.lengthKm - progressKm),
            tripMinutes = tripMinutes.roundToInt(),
            batteryPercent = batteryPercent,
            charging = charging,
            parked = parked,
            waypointName = acceptedStop?.name,
            upNext = upNext(),
            scores = scores.map { PlayerScore(it.key, it.value) },
            places = poisInOrder.map { poi ->
                val state = when (poi.id) {
                    in told -> PlaceState.TOLD
                    in passed -> PlaceState.PASSED
                    else -> PlaceState.AHEAD
                }
                PlaceStatus(poi.id, poi.name, poiKm.getValue(poi.id), state)
            }
        )
    }

    private fun upNext(): String? {
        val next = nextUntoldPoi() ?: return null
        val ahead = (poiKm.getValue(next.id) - progressKm).roundToInt()
        return if (ahead > 0) "${next.name} · ${ahead}km" else next.name
    }

    private fun placeLabel(poi: Poi) = if (poi.area.isBlank()) poi.name else "${poi.area} · ${poi.name}"

    private fun formatKm(km: Double): String {
        val rounded = if (km >= 50) (km / 10).roundToInt() * 10 else km.roundToInt()
        return rounded.toString()
    }

    private fun formatDuration(minutes: Double): String {
        val total = max(0, minutes.roundToInt())
        val hours = total / 60
        val rest = total % 60
        return when {
            hours == 0 -> "${rest}분"
            rest == 0 -> "${hours}시간"
            else -> "${hours}시간 ${rest}분"
        }
    }
}
