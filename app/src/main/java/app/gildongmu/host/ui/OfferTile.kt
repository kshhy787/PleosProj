package app.gildongmu.host.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import app.gildongmu.core.Illustration
import app.gildongmu.core.ScreenFit
import app.gildongmu.core.Spot
import app.gildongmu.core.Tone

/**
 * One recommended spot on the recommendation card: its number, a picture, its name and why to go.
 *
 * The number is the point of it: the host reads the spots out as "첫 번째, 두 번째" and a
 * passenger chooses by saying one, so the tile shows which is which. How much else it shows
 * (`ScreenFit.TileStyle`) and how high its picture is are [ScreenFit]'s decisions, laid out here
 * as its comment lists them. [onTap] is called for a touch while the tile takes touches.
 */
internal class OfferTile(private val context: Context, number: Int, onTap: () -> Unit) {

    val view: LinearLayout = Ui.column(context)

    private val shape: GradientDrawable = Ui.rounded(Palette.SURFACE_RAISED, Ui.dp(context, 20), Palette.OUTLINE, Ui.dp(context, 2))
    private val iconArea = FrameLayout(context)
    private val picture = View(context)

    /** The number on the corner of the picture, for a tile that has one. */
    private val cornerBadge = badge(number)

    /**
     * The number above the name, for a tile without a picture. Above and not beside: the name is
     * what a passenger says to choose, and beside the badge it had too little of a narrow tile's
     * width to be read whole.
     */
    private val aboveBadge = badge(number)

    /** The name and the reason: under the picture, or beside it in a tile that lies on its side. */
    private val labels: LinearLayout = Ui.column(context)
    private val name = Ui.text(context, 22f, Palette.TEXT, bold = true)
    private val reason = Ui.text(context, 18f, Palette.TEXT_SECONDARY)

    /** What [picture] shows now, so that it is redrawn only when the spot or the card's colour changes. */
    private var pictured: Pair<Illustration, Tone>? = null
    private var tone: Tone? = null
    private var tappable = false

    init {
        val pad = Ui.dp(context, ScreenFit.TILE_PADDING_DP)
        val badgeSize = Ui.dp(context, ScreenFit.TILE_BADGE_DP)
        view.background = shape
        view.setPadding(pad, pad, pad, pad)
        // While parked a tile is a button, and is never lower than one.
        view.minimumHeight = Ui.dp(context, ScreenFit.TILE_MIN_DP)
        // On its side the tile is a row of two boxes, not of text to line up on a baseline.
        view.isBaselineAligned = false

        name.maxLines = 2
        name.ellipsize = TextUtils.TruncateAt.END
        reason.ellipsize = TextUtils.TruncateAt.END

        iconArea.addView(picture, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))
        iconArea.addView(cornerBadge, FrameLayout.LayoutParams(badgeSize, badgeSize, Gravity.TOP or Gravity.START))
        labels.addView(aboveBadge, Ui.params(badgeSize, badgeSize))
        labels.addView(name)
        labels.addView(reason, Ui.margins(Ui.params(Ui.MATCH, Ui.WRAP), top = Ui.dp(context, 6)))
        view.addView(iconArea)
        view.addView(labels)

        // Setting a listener makes a view take touches; it must not until [show] says so.
        view.setOnClickListener { onTap() }
        view.isClickable = false
    }

    /**
     * Lays the tile out as [fit] says: everything, or the number and the name only, in the type
     * of the full-size card or of the compact one. [onItsSide]: the picture is a square on the
     * left with the name and the reason beside it (`ScreenFit.tileOnItsSide`).
     */
    fun arrange(fit: ScreenFit, onItsSide: Boolean) {
        val full = fit.tiles == ScreenFit.TileStyle.FULL
        val icon = Ui.dp(context, fit.tileIconDp)
        view.orientation = if (onItsSide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        // The picture's tile starts at the top, where its picture takes up whatever height is
        // spare. A tile of a number and a name has none to take it up: in a row as high as its
        // tallest tile, the two sit in the middle of a tile whose name is a line shorter.
        view.setGravity(if (full && !onItsSide) Gravity.TOP else Gravity.CENTER_VERTICAL)
        iconArea.layoutParams = if (onItsSide) Ui.params(icon, icon) else Ui.params(Ui.MATCH, icon)
        labels.layoutParams = when {
            onItsSide -> Ui.margins(Ui.params(0, Ui.WRAP, 1f), left = Ui.dp(context, ScreenFit.ART_GAP_DP))
            full -> Ui.margins(Ui.params(Ui.MATCH, Ui.WRAP), top = Ui.dp(context, 8))
            else -> Ui.params(Ui.MATCH, Ui.WRAP)
        }
        name.textSize = if (fit.compact) 22f else 28f
        reason.textSize = if (fit.compact) 18f else 22f
        Ui.show(iconArea, full)
        Ui.show(reason, full)
        Ui.show(aboveBadge, !full)
        name.layoutParams = Ui.margins(Ui.params(Ui.MATCH, Ui.WRAP), top = if (full) 0 else Ui.dp(context, ScreenFit.NAMES_BADGE_GAP_DP))
        reason.maxLines = fit.tileReasonLines.coerceAtLeast(1)
    }

    /**
     * Shows [spot] in the card's [tone]. [tappable]: touching the tile chooses the spot, which the
     * host allows only while parked; the tile then has the tone's outline, as a button would, and
     * the rest of the time it is a picture that takes no touch.
     */
    fun show(spot: Spot, tone: Tone, tappable: Boolean) {
        Ui.write(name, spot.name)
        Ui.write(reason, spot.reason)

        val color = Palette.tone(tone)
        if (tone != this.tone) {
            for (badge in listOf(cornerBadge, aboveBadge)) badge.background = Ui.disc(color)
        }
        val wanted = (spot.illustration ?: spot.category.illustration) to tone
        if (wanted != pictured) {
            pictured = wanted
            val layers = LayerDrawable(arrayOf(Ui.rounded(Palette.faint(tone), Ui.dp(context, 12)), IllustrationDrawable(wanted.first, color)))
            val inset = Ui.dp(context, 6)
            layers.setLayerInset(1, inset, inset, inset, inset)
            picture.background = layers
        }
        if (tone != this.tone || tappable != this.tappable) {
            shape.setStroke(Ui.dp(context, 2), if (tappable) color else Palette.OUTLINE)
        }
        this.tone = tone
        this.tappable = tappable
        view.isClickable = tappable
    }

    private fun badge(number: Int): TextView {
        // Dark on the tone colour, as the text of a filled button is.
        val badge = Ui.text(context, 18f, Palette.BACKGROUND, bold = true)
        badge.text = number.toString()
        badge.gravity = Gravity.CENTER
        return badge
    }
}
