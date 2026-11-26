package net.osmand.plus.plugins.nautical.ui.cockpit

import android.content.Context
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
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
import net.osmand.plus.plugins.nautical.AnchorDriftWatchdog
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.plugins.nautical.engine.isGpsStale
import net.osmand.util.MapUtils
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Watchkeeper page of the Cockpit shell: everything the anchor/mob watch needs
 * at a glance while off watch — anchor stage (SAFE/CAUTION/CRITICAL_DRAG with
 * color), distance/bearing to the anchor, rode tension and advisories, plus the
 * live MOB section (distance/bearing to casualty, silence/clear controls).
 *
 * Read-mostly by design: the dramatic actions (weigh anchor, reset position)
 * keep their confirmation-gated HUD/map surfaces; this page broadcasts state.
 */
class WatchkeeperScreen(private val app: OsmandApplication) : CockpitScreen {

    override val paneId: CockpitScreenId = CockpitScreenId.WATCHKEEPER

    private var scope: CoroutineScope? = null

    private lateinit var stageValue: TextView
    private lateinit var distanceValue: TextView
    private lateinit var rodeValue: TextView
    private lateinit var advisoryValue: TextView
    private lateinit var mobValue: TextView
    private lateinit var mobButton: Button

    private val clearHandler = Handler(Looper.getMainLooper())
    private var clearRunnable: Runnable? = null
    // Kept so render() can reset the value colour after a stale-GPS episode.
    private var accentColor = 0

    private companion object {
        const val MOB_CLEAR_HOLD_MS = 2000L
    }

    override fun onCreateView(context: Context, parent: ViewGroup): View {
        val night = CockpitScreenTheme.isDark(app)
        val bg = if (night) ContextCompat.getColor(app, R.color.nautical_cockpit_night_bg) else ContextCompat.getColor(app, R.color.nautical_cockpit_day_bg)
        val fg = if (night) ContextCompat.getColor(app, R.color.nautical_cockpit_night_fg) else ContextCompat.getColor(app, R.color.nautical_cockpit_day_fg)
        val accent = if (night) ContextCompat.getColor(app, R.color.nautical_cockpit_night_accent) else ContextCompat.getColor(app, R.color.nautical_cockpit_day_accent)
        accentColor = accent

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

        fun value(textSizeSp: Float, color: Int): TextView = TextView(context).apply {
            text = app.getString(R.string.nautical_cockpit_value_pending)
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
            typeface = Typeface.DEFAULT_BOLD
        }

        stageValue = value(32f, accent)
        distanceValue = value(32f, accent)
        rodeValue = value(20f, fg)
        advisoryValue = TextView(context).apply {
            setTextColor(fg)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            alpha = 0.9f
        }
        mobValue = value(24f, ContextCompat.getColor(app, R.color.nautical_cockpit_stage_danger))

        column.addView(label(R.string.nautical_cockpit_wk_stage))
        column.addView(stageValue)
        column.addView(label(R.string.nautical_cockpit_wk_distance))
        column.addView(distanceValue)
        column.addView(label(R.string.nautical_cockpit_wk_rode))
        column.addView(rodeValue)
        column.addView(label(R.string.nautical_cockpit_wk_advisories))
        column.addView(advisoryValue)

        column.addView(label(R.string.nautical_cockpit_wk_mob))
        column.addView(mobValue)

        mobButton = Button(context).apply {
            setText(R.string.nautical_cockpit_wk_mob_clear)
            setTextColor(fg)
            setBackgroundColor(if (night) ContextCompat.getColor(app, R.color.nautical_cockpit_night_button_bg) else ContextCompat.getColor(app, R.color.nautical_cockpit_day_button_bg))
            isAllCaps = false
            // Safety standard shared with MobEmergencyHeaderView: clearing an
            // ACTIVE man-over-board emergency requires a 2-second hold; a
            // RESOLVED MOB clears on a plain tap. A stray tap during a live
            // emergency must never silence the alarm and drop the casualty.
            setOnTouchListener { v, event ->
                val mob = mobViewModel() ?: return@setOnTouchListener false
                val resolved = mob.uiState.value.state == net.osmand.plus.plugins.nautical.mob.engine.MobState.RESOLVED
                if (resolved) {
                    if (event.action == MotionEvent.ACTION_UP) {
                        clearMob(mob)
                        v.performClick()
                    }
                    return@setOnTouchListener true
                }
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        clearRunnable = Runnable { clearMob(mob) }
                        clearHandler.postDelayed(clearRunnable!!, MOB_CLEAR_HOLD_MS)
                        v.isPressed = true
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        clearRunnable?.let { clearHandler.removeCallbacks(it) }
                        clearRunnable = null
                        v.isPressed = false
                        true
                    }
                    else -> false
                }
            }
        }
        column.addView(mobButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        return column
    }

    override fun onStart() {
        val engine = NauticalPlugin.engine ?: return
        scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        scope?.launch {
            engine.marineStateFlow.collect { state -> render(state) }
        }
        val watchdog = NauticalPlugin.getInstance()?.anchorWatchdog ?: return
        scope?.launch {
            watchdog.driftStage.collect { stage -> renderStage(stage) }
        }
        scope?.launch {
            watchdog.rodeTensionKg.collect { kg ->
                rodeValue.text = String.format(Locale.US, "%.0f kg", kg)
            }
        }
        val mob = mobViewModel() ?: return
        scope?.launch {
            mob.uiState.collect { state -> renderMob(state.isMobActive, state.state == net.osmand.plus.plugins.nautical.mob.engine.MobState.RESOLVED) }
        }
    }

    override fun onStop() {
        clearRunnable?.let { clearHandler.removeCallbacks(it) }
        clearRunnable = null
        scope?.cancel()
        scope = null
    }

    private fun render(state: MarineState) {
        val anchorLat = app.settings.NAUTICAL_ANCHOR_LAT.get()
        val anchorLon = app.settings.NAUTICAL_ANCHOR_LON.get()
        val armed = anchorLat != 0.0
        val lat = state.latitude
        val lon = state.longitude
        if (!armed || lat == null || lon == null) {
            distanceValue.text = if (armed) {
                app.getString(R.string.nautical_cockpit_value_pending)
            } else {
                app.getString(R.string.nautical_cockpit_wk_disarmed)
            }
        } else {
            val distM = MapUtils.getDistance(anchorLat, anchorLon, lat, lon)
            val bearing = initialBearingDeg(lat, lon, anchorLat, anchorLon)
            var text = String.format(Locale.US, "%.0f m / %.0f°", distM, bearing)
            if (state.isGpsStale()) {
                text = "$text (${app.getString(R.string.nautical_cockpit_stale)})"
                distanceValue.setTextColor(if (CockpitScreenTheme.isDark(app)) ContextCompat.getColor(app, R.color.nautical_cockpit_night_stale) else ContextCompat.getColor(app, R.color.nautical_cockpit_day_stale))
            } else {
                // Reset the amber "stale" tint once GPS is fresh again
                // (it previously stuck until the screen was rebuilt).
                distanceValue.setTextColor(accentColor)
            }
            distanceValue.text = text
        }

        val advisories = mutableListOf<String>()
        val watchdog = NauticalPlugin.getInstance()?.anchorWatchdog
        if (watchdog != null) {
            if (watchdog.isHighRodeLoad.value) {
                advisories.add(app.getString(R.string.nautical_cockpit_wk_adv_high_load))
            }
            if (watchdog.isChafeAdvisoryActive.value) {
                advisories.add(app.getString(R.string.nautical_cockpit_wk_adv_chafe))
            }
            if (watchdog.isWindShiftBreakoutRisk.value) {
                val delta = watchdog.windShiftDeltaDeg.value
                advisories.add(
                    app.getString(R.string.nautical_cockpit_wk_adv_windshift, String.format(Locale.US, "%.0f°", delta))
                )
            }
            val quadrant = watchdog.shallowHazardQuadrant.value
            if (quadrant != null) {
                advisories.add(app.getString(R.string.nautical_cockpit_wk_adv_shallow, quadrant))
            }
        }
        advisoryValue.text = if (advisories.isEmpty()) {
            app.getString(R.string.nautical_cockpit_wk_adv_none)
        } else {
            advisories.joinToString(separator = " · ")
        }
    }

    private fun renderStage(stage: AnchorDriftWatchdog.AnchorDriftStage) {
        val (textRes, color) = when (stage) {
            AnchorDriftWatchdog.AnchorDriftStage.SAFE ->
                R.string.nautical_cockpit_wk_stage_safe to ContextCompat.getColor(app, R.color.nautical_cockpit_stage_safe)
            AnchorDriftWatchdog.AnchorDriftStage.CAUTION ->
                R.string.nautical_cockpit_wk_stage_caution to ContextCompat.getColor(app, R.color.nautical_cockpit_stage_caution)
            AnchorDriftWatchdog.AnchorDriftStage.CRITICAL_DRAG ->
                R.string.nautical_cockpit_wk_stage_drag to ContextCompat.getColor(app, R.color.nautical_cockpit_stage_danger)
        }
        stageValue.setText(textRes)
        stageValue.setTextColor(color)
    }

    private fun renderMob(active: Boolean, resolved: Boolean) {
        if (active) {
            mobValue.text = if (resolved) {
                app.getString(R.string.nautical_cockpit_wk_mob_clear_state)
            } else {
                app.getString(R.string.nautical_cockpit_wk_mob_active)
            }
            mobButton.visibility = View.VISIBLE
            // Disclose the hold-to-confirm requirement on the button itself.
            if (resolved) {
                mobButton.setText(R.string.nautical_cockpit_wk_mob_clear)
            } else {
                mobButton.setText(R.string.nautical_cockpit_wk_mob_clear_confirm)
            }
        } else {
            mobValue.text = app.getString(R.string.nautical_cockpit_wk_mob_clear_state)
            mobButton.visibility = View.GONE
        }
    }

    private fun clearMob(mob: net.osmand.plus.plugins.nautical.mob.viewmodel.MobViewModel) {
        mob.silenceAlarm()
        mob.clearMob()
    }

    private fun mobViewModel() = NauticalPlugin.getInstance()?.mobViewModel

    /** Great-circle initial bearing from vessel to anchor, degrees [0, 360). */
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
