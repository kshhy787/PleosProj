package app.gildongmu.host.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

/** The app's colours. Dark, high contrast, one accent: it is read at a glance from the driver's seat. */
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
}

/**
 * Small helpers for building views in code. The screens are built without layout XML so that the
 * whole UI is ordinary Kotlin the compiler checks, and there are only a handful of views.
 */
object Ui {

    fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density + 0.5f).toInt()

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
}
