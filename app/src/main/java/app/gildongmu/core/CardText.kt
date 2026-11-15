package app.gildongmu.core

/**
 * The words on the card that the host does not say: the chip that names what the card is, and the
 * line under the recommended spots that says how to choose one.
 *
 * They are worked out here, not on the screen, because what is right depends on what the host is
 * doing (reading a list, waiting for the choice, only describing a place), which a test can set up
 * and a look at one screen cannot.
 */
object CardText {

    /** The chip of the card when the host is saying nothing: the app's own name. */
    const val IDLE_CHIP = "길동무"

    /** What the card says while the host waits for an answer. */
    const val LISTENING = "듣고 있어요"

    /** What the hint under the recommended spots tells a passenger to say. A test says it to the host. */
    const val CHOICE_EXAMPLE = "첫 번째 들르자"

    /**
     * The line under the recommended spots. It is for a list one of which can be chosen
     * ([HostSnapshot.choosable]); under spots that are only described it would promise a waypoint
     * that nobody adds.
     */
    const val CHOICE_HINT = "고르는 건 말로: “$CHOICE_EXAMPLE” → 내비에 경유지 추가"

    /**
     * The chip at the top of the card: what sort of thing is on it and where, "장소 이야기 · 원주".
     *
     * A card with spots on offer names the recommendation and the area of the spots, "먹거리 추천 ·
     * 강릉", and keeps saying so while the host waits for the choice: its title is then "듣고
     * 있어요" and the recommendation's name has moved to the subtitle. A tip about a single spot has
     * that spot's name for a title, which is on its tile already, so the chip says what kind of
     * card it is instead. The area is one that is true of every spot on the card ([sharedArea]).
     */
    fun chip(snapshot: HostSnapshot): String {
        val card = snapshot.card ?: return IDLE_CHIP
        val spots = snapshot.offers
        if (spots.isEmpty()) return joined(card.kind.label, card.subtitle)
        val heading = when {
            snapshot.listening == ListenMode.CHOICE -> card.subtitle
            spots.any { it.name == card.title } -> card.kind.label
            else -> card.title
        }
        return joined(heading, sharedArea(spots))
    }

    /**
     * The area to name beside [spots]: the one they all have, else the first word they all have
     * ("강릉" for 강릉 and 강릉 주문진), else none. A list can span two towns, the last places
     * before a rest stop and the first at the destination, and the chip stands over all of its
     * tiles: the first spot's area there would be wrong about the others.
     */
    private fun sharedArea(spots: List<Spot>): String {
        val areas = spots.map { it.area.trim() }.distinct()
        return areas.singleOrNull() ?: areas.map { it.substringBefore(' ') }.distinct().singleOrNull().orEmpty()
    }

    /**
     * The sentence to show under the title, or null when the card shows none.
     *
     * [captions] is the screen's switch for showing what is being spoken: off in the car, where
     * the voice says it, and on for a demo without one. While the car is parked, a question the
     * host is waiting on shows whatever the switch says: the parked screen is the one place where
     * reading is allowed, and somebody who looks at it after the question was asked would
     * otherwise see "듣고 있어요" and not what about. While the car moves nothing is added.
     */
    fun caption(snapshot: HostSnapshot, captions: Boolean): String? {
        val card = snapshot.card ?: return null
        return if (captions || (snapshot.parked && snapshot.listening != null)) card.caption else null
    }

    /**
     * The line above the progress bar: what the host is doing when it is not simply talking.
     *
     * While the host waits for an answer the card's own title says "듣고 있어요", and the same
     * words twice, one under the other, read as a mistake: the line then says nothing. It does
     * say them on a card with spots on offer, where the tiles stand in the title's place and
     * nothing else would show that the host has stopped reading and is waiting.
     */
    fun state(snapshot: HostSnapshot): String = when {
        snapshot.listening != null -> if (snapshot.offers.isEmpty() && snapshot.card?.title == LISTENING) "" else LISTENING
        snapshot.holding -> "내비 안내 중 · 잠시 멈춤"
        snapshot.quiet -> "조용히 가는 중"
        else -> ""
    }

    private fun joined(what: String, where: String): String = if (where.isBlank()) what else "$what · $where"
}
