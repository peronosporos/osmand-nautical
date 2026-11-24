package net.osmand.plus.plugins.nautical.ui.cockpit

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
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
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Nav page of the Cockpit shell: active waypoint name, cross-track error,
 * distance/bearing to the waypoint and time-to-waypoint — the steering
 * essentials without the map chrome.
 */
class NavScreen(private val app: OsmandApplication) : CockpitScreen {

    override val paneId: CockpitScreenId = CockpitScreenId.NAV

    private var scope: CoroutineScope? = null

    private lateinit var waypointValue: TextView
    private lateinit var xteValue: TextView
    private lateinit var distValue: TextView
    private lateinit var etaValue: TextView

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

        fun value(sizeSp: Float, color: Int): TextView = TextView(context).apply {
            text = app.getString(R.string.nautical_cockpit_value_pending)
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            typeface = Typeface.DEFAULT_BOLD
        }

        waypointValue = value(24f, fg)
        xteValue = value(40f, accent)
        distValue = value(32f, accent)
        etaValue = value(24f, fg)

        column.addView(label(R.string.nautical_cockpit_nav_waypoint))
        column.addView(waypointValue)
        column.addView(label(R.string.nautical_cockpit_nav_xte))
        column.addView(xteValue)
        column.addView(label(R.string.nautical_cockpit_nav_distance))
        column.addView(distValue)
        column.addView(label(R.string.nautical_cockpit_nav_eta))
        column.addView(etaValue)

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
        val waypoint = state.activeWaypointName ?: state.activeWaypointPosition?.let {
            String.format(Locale.US, "%.4f, %.4f", it.latitude, it.longitude)
        }
        waypointValue.text = waypoint
            ?: app.getString(R.string.nautical_cockpit_nav_no_route)

        val xte = state.crossTrackError
        xteValue.text = xte?.let {
            String.format(Locale.US, "%+.0f m", it)
        } ?: app.getString(R.string.nautical_cockpit_value_pending)

        val position = state.activeWaypointPosition
        val lat = state.latitude
        val lon = state.longitude
        if (position != null && lat != null && lon != null) {
            val distNm = SignalKUnitConverter.metersToNm(
                net.osmand.util.MapUtils.getDistance(lat, lon, position.latitude, position.longitude)
            )
            val bearing = initialBearingDeg(lat, lon, position.latitude, position.longitude)
            distValue.text = String.format(Locale.US, "%.1f nm / %.0f°", distNm, bearing)
        } else {
            distValue.text = app.getString(R.string.nautical_cockpit_value_pending)
        }

        val seconds = state.timeToWaypoint
        etaValue.text = if (seconds == null || seconds <= 0.0) {
            app.getString(R.string.nautical_cockpit_value_pending)
        } else {
            formatDuration(seconds)
        }
    }

    private fun formatDuration(seconds: Double): String {
        val totalMin = (seconds / 60.0).toLong()
        val h = totalMin / 60
        val m = totalMin % 60
        return if (h > 0) {
            String.format(Locale.US, "%dh %02dm", h, m)
        } else {
            String.format(Locale.US, "%dm", m)
        }
    }

    /** Great-circle initial bearing from vessel to target, degrees [0, 360). */
    private fun initialBearingDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dLon = Math.toRadians(lon2 - lon1)
        val y = sin(dLon) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    private fun dp(v: Int): Int = (v * app.resources.displayMetrics.density).toInt()
}
