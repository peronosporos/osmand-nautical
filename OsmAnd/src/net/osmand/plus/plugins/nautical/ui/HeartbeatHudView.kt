package net.osmand.plus.plugins.nautical.ui

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import net.osmand.data.LatLon
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.plugins.nautical.engine.SignalKNotification
import net.osmand.plus.plugins.nautical.engine.SignalKUnitConverter
import net.osmand.plus.plugins.nautical.mob.viewmodel.MobTriggerSource
import java.util.*

/**
 * Simplified HUD header for WearOS / Watch devices.
 * Displays Heading, Depth, and XTE with maximum font sizes.
 */
class HeartbeatHudView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr), INauticalHudHeader {

    private val headingText: TextView
    private val depthText: TextView
    private val xteText: TextView
    private var isAmbientMode = false

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            // Acknowledge the most severe active alarm
            val plugin = NauticalPlugin.getInstance()
            val engine = NauticalPlugin.engine
            val currentAlarms = engine?.getCurrentState()?.notifications ?: emptyMap()
            if (currentAlarms.isNotEmpty()) {
                val highestPath = currentAlarms.entries.maxByOrNull { it.value.state }?.key
                highestPath?.let { path ->
                    engine?.acknowledgeNotification(path)
                    val app = context.applicationContext as net.osmand.plus.OsmandApplication
                    app.showToastMessage(R.string.nautical_alarm_acknowledged)
                    return true
                }
            }
            return false
        }

        override fun onLongPress(e: MotionEvent) {
            // Safety: Trigger MOB on long-press of the heartbeat HUD
            val app = context.applicationContext as net.osmand.plus.OsmandApplication
            val loc = app.locationProvider.lastKnownLocation
            if (loc != null) {
                NauticalPlugin.getInstance()?.mobViewModel?.triggerMob(LatLon(loc.latitude, loc.longitude), MobTriggerSource.MAP)
                app.showToastMessage(R.string.nautical_mob_label)
            }
        }
    })

    init {
        LayoutInflater.from(context).inflate(R.layout.nautical_heartbeat_hud, this, true)
        headingText = findViewById(R.id.heartbeat_heading)
        depthText = findViewById(R.id.heartbeat_depth)
        xteText = findViewById(R.id.heartbeat_xte)
        
        setBackgroundResource(R.drawable.bg_nautical_hud_panel)
        applyWatchPadding()

        isClickable = true
        isFocusable = true
        setOnTouchListener { v, event -> 
            if (gestureDetector.onTouchEvent(event)) {
                true
            } else {
                if (event.action == MotionEvent.ACTION_UP) {
                    v.performClick()
                }
                false
            }
        }
    }

    fun setAmbientMode(enabled: Boolean) {
        if (isAmbientMode != enabled) {
            isAmbientMode = enabled
            updateAmbientVisuals()
        }
    }

    private fun updateAmbientVisuals() {
        if (isAmbientMode) {
            headingText.setTextColor(Color.WHITE)
            depthText.setTextColor(Color.WHITE)
            xteText.setTextColor(Color.WHITE)
            setBackgroundColor(Color.BLACK)
        } else {
            headingText.setTextColor(ContextCompat.getColor(context, R.color.color_ok))
            depthText.setTextColor(ContextCompat.getColor(context, R.color.text_color_primary_light))
            xteText.setTextColor(ContextCompat.getColor(context, R.color.text_color_primary_light))
            setBackgroundResource(R.drawable.bg_nautical_hud_panel)
        }
    }

    private fun applyWatchPadding() {
        val config = context.resources.configuration
        if (config.isScreenRound) {
            // Extra vertical padding for round screens to center content in the "safe" middle band
            val extraPadding = net.osmand.plus.utils.AndroidUtils.dpToPx(context, 16f)
            setPadding(paddingLeft, paddingTop + extraPadding, paddingRight, paddingBottom + extraPadding)
        }
    }

    override fun setCompactMode(enabled: Boolean) {
        // Heartbeat HUD is already highly simplified, maintain large font for watch readability
    }

    fun updateState(state: MarineState) {
        val app = context.applicationContext as net.osmand.plus.OsmandApplication
        val settings = app.settings

        // Heading: Target if autopilot is engaged, otherwise True Heading
        val heading = state.targetHeading ?: state.headingTrue
        if (heading != null) {
            val headingDeg = Math.toDegrees(heading).toInt()
            headingText.text = String.format(Locale.US, "%03d°", headingDeg)
        } else {
            headingText.text = "---°"
        }

        // Depth: Primary safety metric
        val (depthVal, depthUnit) = SignalKUnitConverter.formatValue(context, settings, state.depthBelowTransducer, "depth")
        depthText.text = String.format(Locale.US, "D: %s%s", depthVal, depthUnit)

        // XTE: Cross Track Error
        val (xteVal, xteUnit) = SignalKUnitConverter.formatValue(context, settings, state.crossTrackError, "distance")
        xteText.text = String.format(Locale.US, "X: %s%s", xteVal, xteUnit)
    }
}
