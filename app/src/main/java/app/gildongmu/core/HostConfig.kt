package app.gildongmu.core

/** The host's tunable thresholds. The defaults are for a real drive. */
data class HostConfig(
    /** Who is playing the quiz, in turn order. */
    val players: List<String> = listOf("첫째", "둘째"),

    /** Suggest a break once this much driving has passed since the last one. */
    val restAfterMinutes: Int = 120,

    /** Suggest a charging stop at or below this battery level. */
    val lowBatteryPercent: Int = 20,

    /** Only suggest a stop that is this far ahead: near enough to matter, far enough to react. */
    val stopLeadMinKm: Double = 2.0,
    val stopLeadMaxKm: Double = 15.0,

    /** After a declined suggestion, wait this much driving time before suggesting again. */
    val suggestCooldownMinutes: Int = 20,

    /** Start a place story this far before the place, and give up this far after it. */
    val storyLeadKm: Double = 4.0,
    val storyTrailKm: Double = 6.0,

    /** Used for the trip estimate and for pacing until the car has reported a few positions. */
    val assumedSpeedKph: Double = 90.0,

    /**
     * A story version counts as fitting only if this many times its stated length is available,
     * because speech engines differ in how fast they talk.
     */
    val pacingMargin: Double = 1.2,

    /** A story is skipped rather than rushed if less than this much real time is available. */
    val minStoryWindowSeconds: Double = 8.0,

    /** Offer a quiz after this much silence (real seconds), if there is something to ask. */
    val quizIdleSeconds: Int = 180,
    val quizQuestionsPerRound: Int = 3,

    val answerTimeoutMs: Long = 10_000,
    val yesNoTimeoutMs: Long = 8_000,

    /** Hold speech when the next navigation instruction is this close. */
    val turnHoldDistanceM: Int = 500,

    /**
     * After parking, wait this long before offering anything, so that a charging session starting
     * a moment later is known about and people getting out are not talked over.
     */
    val restSettleMs: Long = 4_000,

    /** Stay silent this long after a vehicle warning appears (or until it clears, if sooner). */
    val warningQuietMs: Long = 60_000
)
