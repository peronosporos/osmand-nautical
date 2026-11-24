package net.osmand.plus.plugins.nautical.ui.cockpit

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.plugins.nautical.engine.SignalKUnitConverter
import java.util.Locale

/**
 * Helm page of the Cockpit shell: autopilot status readouts (HDG, target,
 * rudder, mode) plus the primary helm actions (engage/standby, ±1°/±10° nudges,
 * auto-tack with abort). One glance surface instead of digging through
 * bottom sheets while steering.
 */
class HelmScreen(private val app: OsmandApplication) : CockpitScreen {

    override val paneId: CockpitScreenId = CockpitScreenId.HELM

    private var scope: CoroutineScope? = null

    private lateinit var hdgValue: TextView
    private lateinit var targetValue: TextView
    private lateinit var rudderValue: TextView
    private lateinit var modeValue: TextView
    private lateinit var engageButton: Button
    private lateinit var tackBanner: TextView

    override fun onCreateView(context: Context, parent: ViewGroup): View {
        val night = NauticalPlugin.isNightVision(app)
        val bg = if (night) 0xFF101010.toInt() else 0xFFF5F5F5.toInt()
        val fg = if (night) 0xFFDDDDDD.toInt() else 0xFF202020.toInt()
        val accent = if (night) 0xFFFF6E00.toInt() else 0xFF0B57D0.toInt()

        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(16), dp(24), dp(16))
            setBackgroundColor(bg)
        }

        fun label(textRes: Int): TextView = TextView(context).apply {
            setText(textRes)
            setTextColor(fg)
            alpha = 0.7f
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        }

        fun bigValue(): TextView = TextView(context).apply {
            text = app.getString(R.string.nautical_cockpit_value_pending)
            setTextColor(accent)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 40f)
            typeface = Typeface.DEFAULT_BOLD
        }

        fun row(labelRes: Int, value: TextView) {
            column.addView(label(labelRes))
            column.addView(value)
        }

        fun button(textRes: Int, onClick: (Button) -> Unit): Button =
            Button(context).apply {
                setText(textRes)
                setTextColor(fg)
                setBackgroundColor(if (night) 0xFF303030.toInt() else 0xFFDDDDDD.toInt())
                isAllCaps = false
                setOnClickListener { onClick(this) }
            }

        hdgValue = bigValue()
        targetValue = bigValue()
        rudderValue = TextView(context).apply {
            setTextColor(fg)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        }
        modeValue = TextView(context).apply {
            setTextColor(fg)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTypeface(typeface, Typeface.BOLD)
        }

        row(R.string.nautical_cockpit_helm_heading, hdgValue)
        row(R.string.nautical_cockpit_helm_target, targetValue)
        column.addView(label(R.string.nautical_cockpit_helm_rudder))
        column.addView(rudderValue)
        column.addView(label(R.string.nautical_cockpit_helm_mode))
        column.addView(modeValue)

        val controls = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val px = dp(8)
            setPadding(0, px, 0, 0)
        }
        val rowParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )

        engageButton = button(R.string.nautical_cockpit_helm_engage) {
            val ap = autopilot()
            val state = NauticalPlugin.engine?.getCurrentState()
            if (ap != null && state != null) {
                if (state.autopilotState.equals("standby", ignoreCase = true)) {
                    ap.engageSmart()
                    it.setText(R.string.nautical_cockpit_helm_standby)
                } else {
                    ap.disengage()
                    it.setText(R.string.nautical_cockpit_helm_engage)
                }
            }
        }
        controls.addView(engageButton, rowParams)

        val nudgeRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(-10, -1, 1, 10).forEach { delta ->
            val text = String.format(Locale.US, "%+d°", delta)
            val b = Button(context).apply {
                this.text = text
                setTextColor(fg)
                setBackgroundColor(if (night) 0xFF303030.toInt() else 0xFFDDDDDD.toInt())
                setOnClickListener { autopilot()?.adjustHeading(delta.toDouble()) }
            }
            nudgeRow.addView(
                b,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )
        }
        controls.addView(nudgeRow, rowParams)

        val tackRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        tackRow.addView(
            button(R.string.nautical_cockpit_helm_tack_port) {
                autopilot()?.initiateAutoTack("PORT")
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        tackRow.addView(
            button(R.string.nautical_cockpit_helm_tack_stbd) {
                autopilot()?.initiateAutoTack("STARBOARD")
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        controls.addView(tackRow, rowParams)

        tackBanner = TextView(context).apply {
            setTextColor(if (night) 0xFF101010.toInt() else 0xFFFFFFFF.toInt())
            setBackgroundColor(0xFFFF6E00.toInt())
            setTypeface(typeface, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        controls.addView(tackBanner, rowParams)
        controls.addView(
            button(R.string.nautical_cockpit_helm_abort_tack) {
                autopilot()?.abortAutoTack()
            },
            rowParams
        )

        column.addView(controls)
        return column
    }

    override fun onStart() {
        val engine = NauticalPlugin.engine ?: return
        scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        scope?.launch {
            engine.marineStateFlow.collect { state -> render(state) }
        }
        val ap = autopilot() ?: return
        scope?.launch {
            ap.autoTackStateFlow.collect { tack ->
                if (tack.isTacking) {
                    tackBanner.visibility = View.VISIBLE
                    tackBanner.text = if (tack.isCountdown) {
                        app.getString(
                            R.string.nautical_cockpit_helm_tack_countdown,
                            tack.countdownSeconds
                        )
                    } else {
                        app.getString(R.string.nautical_cockpit_helm_tack_turning, tack.direction)
                    }
                } else {
                    tackBanner.visibility = View.GONE
                }
            }
        }
    }

    override fun onStop() {
        scope?.cancel()
        scope = null
    }

    private fun render(state: MarineState) {
        val hdg = state.headingTrue
        hdgValue.text = hdg?.let {
            String.format(Locale.US, "%.0f°", (SignalKUnitConverter.radToDeg(it) + 360.0) % 360.0)
        } ?: app.getString(R.string.nautical_cockpit_value_pending)

        val target = state.autopilotHeadingSet ?: state.targetHeading
        targetValue.text = target?.let {
            String.format(Locale.US, "%.0f°", (SignalKUnitConverter.radToDeg(it) + 360.0) % 360.0)
        } ?: app.getString(R.string.nautical_cockpit_value_pending)

        rudderValue.text = state.rudderAngle?.let {
            String.format(Locale.US, "%+.0f°", SignalKUnitConverter.radToDeg(it))
        } ?: "--"

        modeValue.text = app.getString(
            R.string.nautical_cockpit_helm_mode_value,
            state.autopilotState.uppercase(Locale.US)
        )

        val standby = state.autopilotState.equals("standby", ignoreCase = true)
        engageButton.setText(
            if (standby) R.string.nautical_cockpit_helm_engage
            else R.string.nautical_cockpit_helm_standby
        )
    }

    private fun autopilot() = NauticalPlugin.getInstance()?.autopilot

    private fun dp(v: Int): Int = (v * app.resources.displayMetrics.density).toInt()
}
