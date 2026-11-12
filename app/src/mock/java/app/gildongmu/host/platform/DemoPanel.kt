package app.gildongmu.host.platform

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import app.gildongmu.core.HostSnapshot
import app.gildongmu.core.ListenMode
import app.gildongmu.host.ui.Palette
import app.gildongmu.host.ui.Ui

/**
 * The controls beside the card in the mock build: start and pause the scripted drive, see what
 * the pretend car and navigation are doing, and say things to the host by pressing a button.
 *
 * None of this exists in the `pleos` build, where the car is real.
 */
class DemoPanel(private val context: Context, private val actions: Actions) {

    interface Actions {
        fun onStartOrPause()
        fun onRestart()
        fun onAutoReplies(enabled: Boolean)
        fun onCaptions(enabled: Boolean)
        fun onHidePanel()
        fun onSay(text: String)
    }

    val root: View

    private val startButton: TextView
    private val autoButton: TextView
    private val captionButton: TextView
    private val position = line()
    private val navigation = line()
    private val guidance = line()
    private val hearing = line()
    private val voice = line()
    private val passenger = Ui.text(context, 20f, Palette.WARM, bold = true)
    private val answerRow: LinearLayout = Ui.row(context)

    private var autoReplies = true
    private var captions = true
    private var expectedAnswer: String? = null

    init {
        val column = Ui.column(context)
        val pad = Ui.dp(context, 20)
        column.setPadding(pad, pad, pad, pad)
        column.background = Ui.rounded(Palette.SURFACE, Ui.dp(context, 28), Palette.OUTLINE, Ui.dp(context, 2))

        column.addView(heading("데모 주행"))
        column.addView(note("차 없이 돌아가는 시연용 주행입니다. 위치·주행 시간·배터리·내비 안내는 대본에 따라 만들어집니다."))

        startButton = small("주행 시작", filled = true) { actions.onStartOrPause() }
        column.addView(startButton, spaced())
        val tools = Ui.row(context)
        tools.addView(small("처음부터") { actions.onRestart() }, Ui.params(0, Ui.WRAP, 1f))
        tools.addView(small("패널 숨기기") { actions.onHidePanel() }, Ui.margins(Ui.params(0, Ui.WRAP, 1f), left = Ui.dp(context, 10)))
        column.addView(tools, spaced())

        column.addView(heading("차와 내비"), spaced(18))
        column.addView(position)
        column.addView(navigation)
        column.addView(guidance)

        column.addView(heading("탑승자"), spaced(18))
        column.addView(passenger)
        autoButton = small(autoLabel()) {
            autoReplies = !autoReplies
            refreshToggles()
            actions.onAutoReplies(autoReplies)
        }
        column.addView(autoButton, spaced())
        column.addView(sayRow("퀴즈 하자", "이야기해 줘"), spaced())
        column.addView(sayRow("응", "아니"), spaced())
        column.addView(sayRow("조용히", "건너뛰어"), spaced())
        answerRow.addView(small("정답 말하기") { expectedAnswer?.let(actions::onSay) }, Ui.params(0, Ui.WRAP, 1f))
        answerRow.addView(small("오답 말하기") { actions.onSay("잘 모르겠어요") }, Ui.margins(Ui.params(0, Ui.WRAP, 1f), left = Ui.dp(context, 10)))
        column.addView(answerRow, spaced())
        Ui.show(answerRow, false)

        column.addView(heading("음성"), spaced(18))
        captionButton = small(captionLabel()) {
            captions = !captions
            refreshToggles()
            actions.onCaptions(captions)
        }
        column.addView(captionButton, spaced())
        column.addView(voice)
        column.addView(hearing)

        val scroll = ScrollView(context)
        scroll.addView(column)
        root = scroll
    }

    fun setRunning(running: Boolean, started: Boolean) {
        startButton.text = when {
            !started -> "주행 시작"
            running -> "일시정지"
            else -> "계속"
        }
    }

    /** What the pretend car and navigation are doing right now. */
    fun update(snapshot: HostSnapshot, arrived: Boolean) {
        val total = Math.round(snapshot.progressKm + snapshot.remainingKm)
        position.text = "위치  ${Math.round(snapshot.progressKm)} / ${total}km" + if (snapshot.parked) "  · 정차" else ""
        val via = snapshot.waypointName
        navigation.text = when {
            arrived -> "내비  ${snapshot.destinationName} 도착"
            via != null -> "내비  ${snapshot.destinationName}, 경유 $via"
            else -> "내비  ${snapshot.destinationName}"
        }
        guidance.text = if (snapshot.holding) "안내  음성 안내 중 (호스트 대기)" else "안내  없음"
    }

    fun setVoiceStatus(problem: String?) {
        voice.text = "말하기  " + (problem ?: "음성 엔진 정상")
    }

    /** [scripted]: the scripted passengers are answering, so the microphone stays closed. */
    fun setHearingStatus(available: Boolean, scripted: Boolean) {
        hearing.text = "듣기  " + when {
            scripted -> "자동 응답 중 (마이크 꺼짐)"
            available -> "마이크 사용 가능"
            else -> "음성 인식 없음 (버튼 사용)"
        }
    }

    /** The host started or stopped listening; offer the matching answer buttons. */
    fun setListening(mode: ListenMode?, expected: List<String>) {
        expectedAnswer = if (mode == ListenMode.QUIZ_ANSWER) expected.firstOrNull() else null
        Ui.show(answerRow, expectedAnswer != null)
    }

    fun showPassenger(text: String) {
        passenger.text = "\"$text\""
    }

    private fun refreshToggles() {
        autoButton.text = autoLabel()
        captionButton.text = captionLabel()
    }

    private fun autoLabel() = if (autoReplies) "자동 응답 켜짐" else "자동 응답 꺼짐"

    private fun captionLabel() = if (captions) "자막 켜짐" else "자막 꺼짐"

    private fun heading(text: String): TextView {
        val view = Ui.text(context, 20f, Palette.ACCENT, bold = true)
        view.text = text
        return view
    }

    private fun note(text: String): TextView {
        val view = Ui.text(context, 15f, Palette.TEXT_SECONDARY)
        view.text = text
        view.setLineSpacing(0f, 1.25f)
        view.setPadding(0, Ui.dp(context, 8), 0, 0)
        return view
    }

    private fun line(): TextView {
        val view = Ui.text(context, 17f, Palette.TEXT)
        view.setPadding(0, Ui.dp(context, 8), 0, 0)
        return view
    }

    private fun small(label: String, filled: Boolean = false, onClick: () -> Unit): TextView =
        Ui.button(context, label, sizeSp = 18f, filled = filled, minHeightDp = 52, onClick = onClick)

    private fun sayRow(first: String, second: String): View {
        val row = Ui.row(context)
        row.addView(small(first) { actions.onSay(first) }, Ui.params(0, Ui.WRAP, 1f))
        row.addView(small(second) { actions.onSay(second) }, Ui.margins(Ui.params(0, Ui.WRAP, 1f), left = Ui.dp(context, 10)))
        return row
    }

    private fun spaced(topDp: Int = 10): LinearLayout.LayoutParams =
        Ui.margins(Ui.params(Ui.MATCH, Ui.WRAP), top = Ui.dp(context, topDp))
}
