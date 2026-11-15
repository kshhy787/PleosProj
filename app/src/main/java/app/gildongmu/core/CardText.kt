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
     * card it is instead.
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
        return joined(heading, spots.first().area)
    }

    private fun joined(what: String, where: String): String = if (where.isBlank()) what else "$what · $where"
}
