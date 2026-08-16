package net.osmand.plus.plugins.nautical.ui

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.isVisible
import kotlinx.coroutines.launch
import net.osmand.PlatformUtil
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.settings.enums.VesselContext
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.plugins.nautical.engine.SignalKUnitConverter
import net.osmand.plus.plugins.nautical.utils.TemporalUtils

class TacticalHudView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr), INauticalHudHeader {

    private val rotText: TextView
    private val driftText: TextView
    private val setText: TextView
    private val gustText: TextView
    private val rudderView: RudderView
    private val telemetryBuilder = StringBuilder()
    
    private var lastAnnouncementTime = 0L
    private val announcementThrottleMs = 10000L // Announce every 10s if changed significantly

    private var naLabel: String
    private var offlineLabel: String
    private var staleIcon: String
    private var rotPrefix: String
    private var driftPrefix: String
    private var setPrefix: String

    private val mobButton: View
    private val resetWatchdogButton: com.google.android.material.button.MaterialButton
    private val contextButton: com.google.android.material.button.MaterialButton
    private val forwardHazardWarning: TextView
    private val riggingLoadWarning: TextView

    init {
        LayoutInflater.from(context).inflate(R.layout.nautical_tactical_hud, this, true)
        rotText = findViewById(R.id.rot_value)
        driftText = findViewById(R.id.drift_value)
        setText = findViewById(R.id.set_value)
        gustText = findViewById(R.id.gust_indicator)
        rudderView = findViewById(R.id.rudder_view)
        
        mobButton = findViewById(R.id.btn_hud_mob)
        resetWatchdogButton = findViewById(R.id.btn_reset_watchdog)
        contextButton = findViewById(R.id.btn_vessel_context)
        forwardHazardWarning = findViewById(R.id.forward_hazard_warning)
        riggingLoadWarning = findViewById(R.id.rigging_load_warning)

        mobButton.setOnClickListener {
            val plugin = NauticalPlugin.getInstance()
            val loc = plugin?.application?.locationProvider?.lastKnownLocation
            if (loc != null) {
                plugin.mobViewModel?.triggerMob(
                    net.osmand.data.LatLon(loc.latitude, loc.longitude),
                    net.osmand.plus.plugins.nautical.mob.viewmodel.MobTriggerSource.BUTTON
                )
            } else {
                plugin?.application?.showToastMessage(R.string.nautical_error_no_gps)
            }
        }

        resetWatchdogButton.setOnClickListener {
            resetWatchdog()
        }

        contextButton.setOnClickListener {
            showContextPicker()
        }

        isVisible = true
        
        setBackgroundResource(R.drawable.bg_nautical_hud_panel)
        
        naLabel = context.getString(R.string.n_a)
        offlineLabel = context.getString(R.string.nautical_offline)
        staleIcon = " ⌛"
        
        // Extract prefixes from labels like "ROT: %s"
        rotPrefix = context.getString(R.string.nautical_rot_label, "", "").trim()
        driftPrefix = context.getString(R.string.nautical_drift_label, "", "").trim()
        setPrefix = context.getString(R.string.nautical_set_label, "", "").trim()

        setupAccessibility()
    }

    private fun setupAccessibility() {
        val delegate = object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                val sb = StringBuilder()
                sb.append(rotText.contentDescription ?: "").append(". ")
                sb.append(driftText.contentDescription ?: "").append(". ")
                sb.append(setText.contentDescription ?: "")
                info.contentDescription = sb.toString()
            }
        }
        ViewCompat.setAccessibilityDelegate(this, delegate)
    }

    private var viewScope: kotlinx.coroutines.CoroutineScope? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewScope?.cancel()
        viewScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.SupervisorJob())
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        viewScope?.cancel()
        viewScope = null
    }

    override fun setCompactMode(enabled: Boolean) {
        val p = if (enabled) 2f else 6f
        val px = (p * context.resources.displayMetrics.density).toInt()
        setPadding(px, px, px, px)
    }

    private fun resetWatchdog() {
        val plugin = NauticalPlugin.getInstance() ?: return
        val engine = NauticalPlugin.engine ?: return
        val scope = viewScope ?: plugin.pluginScope ?: return
        scope.launch {
            val rest = engine.getRestService()
            if (rest != null) {
                try {
                    val path = "notifications/safety/watchdog"
                    val body = net.osmand.plus.plugins.nautical.network.SignalKPutBody(value = "normal")
                    val response = rest.putValue(path, body)
                    if (response.isSuccessful) {
                        plugin.application.showToastMessage(R.string.shared_string_ok)
                    }
                } catch (e: Exception) {
                    PlatformUtil.getLog(TacticalHudView::class.java).error("Failed to reset watchdog: ${e.message}")
                }
            }
        }
    }

    private fun showContextPicker() {
        val plugin = NauticalPlugin.getInstance() ?: return
        val mapActivity = plugin.application.osmandMap?.mapView?.mapActivity ?: return
        val contexts = VesselContext.entries
        val items = contexts.map { it.name.replace("_", " ") }.toTypedArray()
        
        androidx.appcompat.app.AlertDialog.Builder(mapActivity)
            .setTitle(R.string.nautical_vessel_context_label)
            .setItems(items) { _, which ->
                val selected = contexts[which]
                plugin.applyVesselContext(selected)
                updateContextButton(selected)
            }
            .show()
    }

    private fun updateContextButton(vesselContext: VesselContext) {
        contextButton.text = vesselContext.name
        // Map context to icon if needed
        val iconRes = when(vesselContext) {
            VesselContext.SAILING -> R.drawable.ic_action_sail_boat_dark
            VesselContext.MOTORING -> R.drawable.ic_action_car_dark
            VesselContext.ANCHORED, VesselContext.MOORED, VesselContext.DOCKING -> R.drawable.ic_action_anchor
            VesselContext.EMERGENCY_HEAVE_TO -> R.drawable.ic_action_alert
            else -> R.drawable.ic_action_sail_boat_dark
        }
        contextButton.setIconResource(iconRes)
    }

    fun updateState(state: MarineState) {
        val now = System.currentTimeMillis()

        val app = context.applicationContext as net.osmand.plus.OsmandApplication
        val settings = app.settings
        val plugin = NauticalPlugin.getInstance()
        val caps = plugin?.capabilityManager?.capabilities?.value ?: net.osmand.plus.plugins.nautical.engine.CapabilityManager.ServerCapabilityMap()

        val isOffline = (state.connectionStatus == net.osmand.plus.plugins.nautical.engine.ConnectionStatus.DISCONNECTED) || (plugin?.isSignalKConnected() == false)
        val isConnecting = plugin?.getConnection()?.isConnecting() == true
        
        if (isConnecting) {
             naLabel = "Connecting..." // Temporary UI feedback
        }

        resetWatchdogButton.isVisible = caps.hasDeadMansSwitch && !isOffline
        if (state.watchdogStatus != null && (state.watchdogStatus.state == net.osmand.plus.plugins.nautical.engine.NotificationState.ALARM || state.watchdogStatus.state == net.osmand.plus.plugins.nautical.engine.NotificationState.EMERGENCY)) {
            resetWatchdogButton.setBackgroundColor(Color.RED)
        } else {
            resetWatchdogButton.setBackgroundResource(R.drawable.bg_nautical_hud_panel)
        }

        val forwardHazard = state.forwardHazards.firstOrNull { it.severity == net.osmand.plus.plugins.nautical.engine.NotificationState.ALARM || it.severity == net.osmand.plus.plugins.nautical.engine.NotificationState.EMERGENCY }
        if (caps.hasForwardWatch && forwardHazard != null) {
            forwardHazardWarning.isVisible = true
            forwardHazardWarning.text = context.getString(R.string.nautical_forward_hazard_warning, forwardHazard.name, forwardHazard.distance)
        } else {
            forwardHazardWarning.isVisible = false
        }

        val highRiggingLoad = state.notifications.values.any { it.message.contains("rigging", ignoreCase = true) && (it.state == net.osmand.plus.plugins.nautical.engine.NotificationState.ALARM || it.state == net.osmand.plus.plugins.nautical.engine.NotificationState.EMERGENCY) }
        riggingLoadWarning.isVisible = caps.hasRiggingLoad && highRiggingLoad

        // Helper to update a telemetry TextView with zero allocation and contentEquals check
        fun updateTelemetry(textView: TextView, value: Double?, prefix: String, unitType: String, timeOfFix: Long?) {
            if (isOffline || (value == null)) {
                val label = if (isOffline) offlineLabel else naLabel
                if (!textView.text.contentEquals(label)) {
                    textView.text = label
                }
                val desc = "$prefix: $label"
                if (!textView.contentDescription.contentEquals(desc)) {
                    textView.contentDescription = desc
                }
                textView.alpha = 0.5f
                return
            }

            val isStale = TemporalUtils.isStale(timeOfFix)
            val (vStr, unit) = SignalKUnitConverter.formatValue(context, settings, value, unitType)
            
            telemetryBuilder.setLength(0)
            telemetryBuilder.append(prefix).append(" ").append(vStr).append(" ").append(unit)
            if (isStale) telemetryBuilder.append(staleIcon)
            
            if (!textView.text.contentEquals(telemetryBuilder)) {
                textView.text = telemetryBuilder.toString()
            }
            
            telemetryBuilder.setLength(0)
            telemetryBuilder.append(prefix).append(": ").append(vStr).append(" ").append(unit)
            if (isStale) telemetryBuilder.append(", stale")
            
            val desc = telemetryBuilder.toString()
            if (!textView.contentDescription.contentEquals(desc)) {
                textView.contentDescription = desc
            }
            
            textView.alpha = if (isStale) 0.5f else 1.0f
        }

        updateTelemetry(rotText, state.rateOfTurn, rotPrefix, "angle", state.timeOfRotFix)
        updateTelemetry(driftText, state.drift, driftPrefix, "speed", state.timeOfDriftFix)
        updateTelemetry(setText, state.setTrue, setPrefix, "angle", state.timeOfDriftFix)

        // Gust Indicator Integration
        val filterService = net.osmand.plus.plugins.nautical.di.SailingDependencyContainer.environmentalFilterService
        gustText.isVisible = filterService?.isGustActive?.value == true

        // Rudder
        val rudderStale = TemporalUtils.isStale(state.timeOfRudderFix)
        if (isOffline || state.rudderAngle == null) {
            rudderView.alpha = 0.5f
            rudderView.setRudderAngle(Double.NaN)
        } else {
            rudderView.alpha = if (rudderStale) 0.5f else 1.0f
            rudderView.setRudderAngle(state.rudderAngle)
            rudderView.setNightMode(NauticalPlugin.isNightVision(app))
        }

        if (now - lastAnnouncementTime > announcementThrottleMs) {
            val announcement = "${driftText.contentDescription}, ${setText.contentDescription}"
            announceForAccessibility(announcement)
            lastAnnouncementTime = now
        }
    }
}
