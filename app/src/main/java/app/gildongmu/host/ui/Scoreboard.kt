package app.gildongmu.host.ui

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ImageSpan
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import app.gildongmu.core.PlayerScore

/**
 * The quiz scores on the parked screen, in the two forms `ScreenFit.scoresBeside` chooses between:
 * [panel], a board beside the card with a row for each player, and [line], all of them on one line
 * for a window where the card cannot give up the board's width. Both are kept up to date, so the
 * screen only shows one or the other. Whoever leads has a crown, which the host decides
 * ([PlayerScore.leader]).
 */
internal class Scoreboard(private val context: Context) {

    val panel: LinearLayout = Ui.column(context)
    val line: TextView = Ui.text(context, SCORE_SP, Palette.TEXT, bold = true)

    private val rows: LinearLayout = Ui.column(context)
    private var shown: List<PlayerScore>? = null

    init {
        val pad = Ui.dp(context, 16)
        panel.background = Ui.rounded(Palette.SURFACE, Ui.dp(context, 28), Palette.OUTLINE, Ui.dp(context, 2))
        panel.setPadding(pad, pad, pad, pad)
        // The quiz is a game, so its board has the games' colour.
        val title = Ui.text(context, 20f, Palette.GAME, bold = true)
        title.text = "퀴즈 점수"
        panel.addView(title, Ui.params(Ui.MATCH, Ui.WRAP))
        panel.addView(rows, Ui.margins(Ui.params(Ui.MATCH, Ui.WRAP), top = Ui.dp(context, 6)))

        line.maxLines = 1
        line.ellipsize = TextUtils.TruncateAt.END
    }

    fun update(scores: List<PlayerScore>) {
        // The host reports several times a second; the scores change a few times a trip.
        if (scores == shown) return
        shown = scores

        rows.removeAllViews()
        for (score in scores) rows.addView(row(score), Ui.margins(Ui.params(Ui.MATCH, Ui.WRAP), top = Ui.dp(context, 10)))

        val text = SpannableStringBuilder()
        val size = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, SCORE_SP, context.resources.displayMetrics).toInt()
        for (score in scores) {
            if (text.isNotEmpty()) text.append("   ")
            if (score.leader) {
                // The crown stands on the baseline like a letter, as high as the text.
                val crown = CrownDrawable(Palette.WARM)
                crown.setBounds(0, 0, size, size)
                text.append(" ", ImageSpan(crown, ImageSpan.ALIGN_BASELINE), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                text.append(" ")
            }
            text.append("${score.player} ${score.score}점")
        }
        line.text = text
    }

    /** A crown for a leader (its place is kept empty for the others, so the names line up), the name, the score. */
    private fun row(score: PlayerScore): View {
        val row = Ui.row(context)
        val crown = View(context)
        crown.background = CrownDrawable(Palette.WARM)
        Ui.keep(crown, score.leader)
        val name = Ui.text(context, SCORE_SP, Palette.TEXT)
        name.text = score.player
        name.maxLines = 1
        name.ellipsize = TextUtils.TruncateAt.END
        val points = Ui.text(context, SCORE_SP, Palette.TEXT, bold = true)
        points.text = "${score.score}점"
        points.maxLines = 1

        val crownSize = Ui.dp(context, CROWN_DP)
        row.addView(crown, Ui.params(crownSize, crownSize))
        row.addView(name, Ui.margins(Ui.params(0, Ui.WRAP, 1f), left = Ui.dp(context, 8)))
        row.addView(points, Ui.margins(Ui.params(Ui.WRAP, Ui.WRAP), left = Ui.dp(context, 8)))
        return row
    }

    private companion object {
        /** The size of the names and the scores, which `ScreenFit.SCOREBOARD_DP` and `SCORES_LINE_DP` are measured for. */
        const val SCORE_SP = 26f
        const val CROWN_DP = 28
    }
}
