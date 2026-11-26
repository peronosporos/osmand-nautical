package net.osmand.plus.plugins.nautical.ui.cockpit

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.ConnectionStatus
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.plugins.nautical.engine.isDepthStale
import net.osmand.plus.plugins.nautical.engine.isSogStale
import net.osmand.plus.plugins.nautical.engine.isWindStale
import net.osmand.plus.plugins.nautical.engine.SignalKUnitConverter
import java.util.Locale

/**
 * Pilot pane of the Cockpit shell: big, glance-able instrument readouts
 * (SOG, COG, depth, apparent wind) fed from the engine state flow.
 *
 * Fully programmatic (no XML layout) to keep the first iteration dependency-free;
 * the shell owns theming via the night-vision flag.
 */
class InstrumentsPane(private val app: OsmandApplication) : CockpitScreen {

    override val paneId: CockpitScreenId = CockpitScreenId.INSTRUMENTS

    private var scope: CoroutineScope? = null

    private lateinit var sogValue: TextView
    private lateinit var cogValue: TextView
    private lateinit var depthValue: TextView
    private lateinit var windValue: TextView
    private lateinit var connectionValue: TextView

    override fun onCreateView(context: Context, parent: ViewGroup): View {
        val night = CockpitScreenTheme.isDark(app)
        val bg = if (night) ContextCompat.getColor(app, R.color.nautical_cockpit_night_bg) else ContextCompat.getColor(app, R.color.nautical_cockpit_day_bg)
        val fg = if (night) ContextCompat.getColor(app, R.color.nautical_cockpit_night_fg) else ContextCompat.getColor(app, R.color.nautical_cockpit_day_fg)
        val accent = if (night) ContextCompat.getColor(app, R.color.nautical_cockpit_night_accent) else ContextCompat.getColor(app, R.color.nautical_cockpit_day_accent)

        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(bg)
            gravity = Gravity.CENTER
        }

        fun label(textRes: Int): TextView = TextView(context).apply {
            setText(textRes)
            setTextColor(fg)
            alpha = 0.7f
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        }

        fun bigValue(): TextView = TextView(context).apply {
            text = app.getString(R.string.nautical_cockpit_value_pending)
            setTextColor(accent)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 46f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        fun row(labelRes: Int, value: TextView) {
            column.addView(label(labelRes))
            column.addView(value)
            (value.layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(2)
            (value.layoutParams as? LinearLayout.LayoutParams)?.bottomMargin = dp(24)
        }

        sogValue = bigValue()
        cogValue = bigValue()
        depthValue = bigValue()
        windValue = bigValue()
        connectionValue = TextView(context).apply {
            setTextColor(fg)
            alpha = 0.8f
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        }

        row(R.string.nautical_cockpit_sog_label, sogValue)
        row(R.string.nautical_cockpit_cog_label, cogValue)
        row(R.string.nautical_cockpit_depth_label, depthValue)
        row(R.string.nautical_cockpit_wind_label, windValue)
        column.addView(connectionValue)

        return column
    }

    override fun onStart() {
        val engine = NauticalPlugin.engine ?: return
        scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        scope?.launch {
            engine.marineStateFlow.collect { state -> render(state) }
        }
    }

    override fun onStop() {
        scope?.cancel()
        scope = null
    }

    private fun render(state: MarineState) {
        val night = CockpitScreenTheme.isDark(app)
        val accent = if (night) ContextCompat.getColor(app, R.color.nautical_cockpit_night_accent) else ContextCompat.getColor(app, R.color.nautical_cockpit_day_accent)
        val staleColor = if (night) ContextCompat.getColor(app, R.color.nautical_cockpit_night_stale) else ContextCompat.getColor(app, R.color.nautical_cockpit_day_stale)
        val staleMark = app.getString(R.string.nautical_cockpit_stale)

        fun setValue(view: TextView, text: String?, stale: Boolean) {
            if (text == null) {
                view.text = app.getString(R.string.nautical_cockpit_value_pending)
                view.setTextColor(accent)
            } else if (stale) {
                view.text = "$text ($staleMark)"
                view.setTextColor(staleColor)
            } else {
                view.text = text
                view.setTextColor(accent)
            }
        }

        val sogKn = state.speedOverGround?.let { SignalKUnitConverter.msToKnots(it) }
        setValue(
            sogValue,
            sogKn?.let { app.getString(R.string.nautical_fmt_kn, it) },
            state.isSogStale()
        )

        val cogDeg = state.courseOverGroundTrue?.let { SignalKUnitConverter.radToDeg(it) }
        setValue(
            cogValue,
            cogDeg?.let { String.format(Locale.US, "%.0f°", (it + 360.0) % 360.0) },
            state.isSogStale()
        )

        val depthM = state.depthBelowKeel ?: state.depthBelowTransducer
        setValue(
            depthValue,
            depthM?.let { app.getString(R.string.nautical_fmt_m, it) },
            state.isDepthStale()
        )

        val awsKn = state.windSpeedApparent?.let { SignalKUnitConverter.msToKnots(it) }
        setValue(
            windValue,
            awsKn?.let { app.getString(R.string.nautical_fmt_kn, it) },
            state.isWindStale()
        )

        connectionValue.text = app.getString(R.string.nautical_cockpit_connection, statusLabel(state))
    }

    private fun statusLabel(state: MarineState): String =
        when (state.connectionStatus) {
            ConnectionStatus.CONNECTED -> app.getString(R.string.nautical_cockpit_conn_connected)
            ConnectionStatus.CONNECTING -> app.getString(R.string.nautical_cockpit_conn_connecting)
            ConnectionStatus.STALE -> app.getString(R.string.nautical_cockpit_conn_stale)
            ConnectionStatus.DISCONNECTED -> app.getString(R.string.nautical_cockpit_conn_disconnected)
            ConnectionStatus.UNAUTHORIZED -> app.getString(R.string.nautical_cockpit_conn_unauthorized)
        }

    private fun dp(v: Int): Int = (v * app.resources.displayMetrics.density).toInt()

}
