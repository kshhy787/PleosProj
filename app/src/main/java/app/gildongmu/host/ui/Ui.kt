package app.gildongmu.host.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import app.gildongmu.core.Tone

/**
 * The app's colours. Dark and high contrast: it is read at a glance from the driver's seat.
 *
 * What is on the card has a colour of its own, its [Tone], so that a glance tells a story from a
 * quiz from a place to stop before a word is read. Everything else stays grey, white and [ACCENT].
 */
object Palette {
    const val BACKGROUND = 0xFF0B0F14.toInt()
    const val SURFACE = 0xFF151B23.toInt()
    const val SURFACE_RAISED = 0xFF1E2630.toInt()
    const val OUTLINE = 0xFF2A3441.toInt()
    const val TEXT = 0xFFF3F6FA.toInt()
    const val TEXT_SECONDARY = 0xFF9AA7B4.toInt()
    const val ACCENT = 0xFF5B9BFF.toInt()
    const val GOOD = 0xFF37D67A.toInt()
    const val WARM = 0xFFFFC857.toInt()

    // One colour for each Tone. Each is used as text and as thin lines on SURFACE, so each keeps a
    // contrast of at least 4.5 to 1 against it, and still does as chip text on its own faint fill
    // (PaletteTest holds both).

    /** Stories: the accent blue, as the app was blue before it had tones. */
    const val STORY = ACCENT

    /** Things to eat and places to see. */
    const val FOOD = 0xFFFF8A3D.toInt()

    /** Quizzes and games. A little lighter than [GOOD], which stays the colour of a right answer. */
    const val GAME = 0xFF4ADE80.toInt()

    /** Suggestions to stop and rests. More lemon than [WARM], to stand further from [FOOD]'s orange. */
    const val STOP = 0xFFFFD43B.toInt()

    /** Notices, the briefing and the wrap-up: the grey-blue of the secondary text, a step brighter. */
    const val NOTICE = 0xFFA9B8C8.toInt()

    /** How much of a tone its faint fill has. More, and the blue chip's text falls under 4.5 to 1. */
    const val FAINT_ALPHA = 0.16f

    /** The colour of a tone: the card's border, the chip's text, the picture, the progress bar. */
    fun tone(tone: Tone): Int = when (tone) {
        Tone.STORY -> STORY
        Tone.FOOD -> FOOD
        Tone.GAME -> GAME
        Tone.STOP -> STOP
        Tone.NOTICE -> NOTICE
    }

    /**
     * A tone's faint fill, for a chip and for the panel behind a picture: the tone at [FAINT_ALPHA]
     * over [SURFACE]. It is the mixed, opaque colour, not a translucent one, so that it looks the
     * same whatever happens to lie under the view that has it.
     */
    fun faint(tone: Tone): Int = over(tone(tone), FAINT_ALPHA)

    /**
     * [color] laid over [base] at [alpha] (0 to 1), as one opaque colour. Plain arithmetic rather
     * than `android.graphics.Color`, so that the colours can be checked in a unit test.
     */
    fun over(color: Int, alpha: Float, base: Int = SURFACE): Int {
        val share = alpha.coerceIn(0f, 1f)
        fun channel(shift: Int): Int {
            val under = (base ushr shift) and 0xFF
            val top = (color ushr shift) and 0xFF
            return (under + (top - under) * share + 0.5f).toInt() shl shift
        }
        return (0xFF shl 24) or channel(16) or channel(8) or channel(0)
    }
}

/**
 * Small helpers for building views in code. The screens are built without layout XML so that the
 * whole UI is ordinary Kotlin the compiler checks, and there are only a handful of views.
 */
object Ui {

    fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density + 0.5f).toInt()

    /** For a size that `ScreenFit` worked out, which need not be a whole number of dp. */
    fun dp(context: Context, value: Float): Int =
        (value * context.resources.displayMetrics.density + 0.5f).toInt()

    /** A filled circle (in a square view): a number badge. */
    fun disc(color: Int): GradientDrawable {
        val drawable = GradientDrawable()
        drawable.shape = GradientDrawable.OVAL
        drawable.setColor(color)
        return drawable
    }

    fun rounded(color: Int, radiusPx: Int, strokeColor: Int? = null, strokePx: Int = 0): GradientDrawable {
        val drawable = GradientDrawable()
        drawable.setColor(color)
        drawable.setCornerRadius(radiusPx.toFloat())
        if (strokeColor != null) drawable.setStroke(strokePx, strokeColor)
        return drawable
    }

    fun text(context: Context, sizeSp: Float, color: Int, bold: Boolean = false): TextView {
        val view = TextView(context)
        view.textSize = sizeSp
        view.setTextColor(color)
        if (bold) view.typeface = Typeface.DEFAULT_BOLD
        view.includeFontPadding = false
        return view
    }

    /**
     * A flat button. Touch targets in a car need to be big, so the default is 76dp tall; the demo
     * controls, which are not for the driver, pass a smaller [minHeightDp].
     */
    fun button(
        context: Context,
        label: String,
        sizeSp: Float = 22f,
        filled: Boolean = false,
        minHeightDp: Int = 76,
        onClick: () -> Unit
    ): TextView {
        val view = text(context, sizeSp, if (filled) Palette.BACKGROUND else Palette.TEXT, bold = true)
        view.text = label
        view.gravity = Gravity.CENTER
        view.minHeight = dp(context, minHeightDp)
        val side = dp(context, if (minHeightDp >= 60) 28 else 14)
        view.setPadding(side, dp(context, 10), side, dp(context, 10))
        view.background = if (filled) {
            rounded(Palette.ACCENT, dp(context, 18))
        } else {
            rounded(Palette.SURFACE_RAISED, dp(context, 18), Palette.OUTLINE, dp(context, 2))
        }
        view.isClickable = true
        view.isFocusable = true
        view.setOnClickListener { onClick() }
        return view
    }

    fun row(context: Context): LinearLayout {
        val layout = LinearLayout(context)
        layout.orientation = LinearLayout.HORIZONTAL
        layout.setGravity(Gravity.CENTER_VERTICAL)
        return layout
    }

    fun column(context: Context): LinearLayout {
        val layout = LinearLayout(context)
        layout.orientation = LinearLayout.VERTICAL
        return layout
    }

    fun params(width: Int, height: Int, weight: Float = 0f): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(width, height, weight)

    fun margins(params: LinearLayout.LayoutParams, left: Int = 0, top: Int = 0, right: Int = 0, bottom: Int = 0): LinearLayout.LayoutParams {
        params.setMargins(left, top, right, bottom)
        return params
    }

    const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    fun show(view: View, visible: Boolean) {
        view.visibility = if (visible) View.VISIBLE else View.GONE
    }

    /**
     * Shows or blanks [view] without giving up its room, where [show] would let the views around
     * it move: the skip button coming and going must not shift the title.
     */
    fun keep(view: View, visible: Boolean) {
        view.visibility = if (visible) View.VISIBLE else View.INVISIBLE
    }

    /** Sets [view]'s text if it is not that already. The host reports several times a second. */
    fun write(view: TextView, text: String) {
        if (view.text.toString() != text) view.text = text
    }
}
