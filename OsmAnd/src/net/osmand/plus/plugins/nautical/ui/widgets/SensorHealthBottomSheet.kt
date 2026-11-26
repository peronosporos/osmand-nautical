package net.osmand.plus.plugins.nautical.ui.widgets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.base.bottomsheetmenu.BaseBottomSheetItem
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.ConnectionStatus
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.plugins.nautical.engine.SignalKUnitConverter
import java.util.Locale
import kotlin.math.abs

/**
 * Detailed Sensor & Telemetry Diagnostics Inspector BottomSheet.
 * Displays real-time breakdown of GPS, Compass, Depth, Wind, AIS, and NMEA/Signal K telemetry sources
 * with color-coded health chips (Green = OK, Yellow = Delayed > 2s, Red = Lost > 5s).
 */
class SensorHealthBottomSheet : BaseNauticalBottomSheet() {

    private lateinit var themedCtx: android.content.Context
    private var prevHeadingFixMs: Long = -1L
    private var prevHeadingWallMs: Long = 0L
    private var measuredHeadingHz: Double? = null

    private lateinit var badgeGps: TextView
    private lateinit var txtGpsDetails: TextView

    private lateinit var badgeCompass: TextView
    private lateinit var txtCompassDetails: TextView

    private lateinit var badgeDepth: TextView
    private lateinit var txtDepthDetails: TextView

    private lateinit var badgeWind: TextView
    private lateinit var txtWindDetails: TextView

    private lateinit var badgeAis: TextView
    private lateinit var txtAisDetails: TextView

    private lateinit var badgeServer: TextView
    private lateinit var txtServerDetails: TextView

    override fun createMenuItems(savedInstanceState: Bundle?) {
        addTitleItem(getString(R.string.nautical_sh_title))

        themedCtx = net.osmand.plus.utils.UiUtilities.getThemedContext(requireContext(), nightMode)
        val customView = LayoutInflater.from(themedCtx).inflate(R.layout.bottom_sheet_sensor_health, null)

        badgeGps = customView.findViewById(R.id.badge_gps_health)
        txtGpsDetails = customView.findViewById(R.id.txt_gps_details)

        badgeCompass = customView.findViewById(R.id.badge_compass_health)
        txtCompassDetails = customView.findViewById(R.id.txt_compass_details)

        badgeDepth = customView.findViewById(R.id.badge_depth_health)
        txtDepthDetails = customView.findViewById(R.id.txt_depth_details)

        badgeWind = customView.findViewById(R.id.badge_wind_health)
        txtWindDetails = customView.findViewById(R.id.txt_wind_details)

        badgeAis = customView.findViewById(R.id.badge_ais_health)
        txtAisDetails = customView.findViewById(R.id.txt_ais_details)

        badgeServer = customView.findViewById(R.id.badge_server_health)
        txtServerDetails = customView.findViewById(R.id.txt_server_details)

        customView.findViewById<View>(R.id.btn_close_diagnostics)?.setOnClickListener {
            dismiss()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            NauticalPlugin.engine?.marineStateFlow?.collectLatest { state ->
                updateDiagnostics(state)
            }
        }

        items.add(BaseBottomSheetItem.Builder().setCustomView(customView).create())
    }

    private fun updateDiagnostics(state: MarineState) {
        val now = System.currentTimeMillis()
        val app = activity?.application as? OsmandApplication ?: return

        // 1. GPS / GNSS
        val gpsAgeMs = if (state.timeOfPositionFix > 0) now - state.timeOfPositionFix else -1L
        val hasGps = state.latitude != null && state.longitude != null && gpsAgeMs in 0..10000
        setHealthBadge(badgeGps, gpsAgeMs, hasGps)

        val posStr = if (state.latitude != null && state.longitude != null) {
            String.format(Locale.US, "%.5f°, %.5f°", state.latitude, state.longitude)
        } else {
            val loc = app.locationProvider?.lastKnownLocation
            if (loc != null) getString(R.string.nautical_sh_gps_internal_fmt, loc.latitude, loc.longitude)
            else getString(R.string.nautical_sh_no_fix)
        }
        val hdopStr = state.gnss?.horizontalDilution?.let { String.format(Locale.US, "%.1f", it) } ?: "--"
        val satsStr = state.gnss?.satellites?.toString() ?: "--"
        val gpsLatencyStr = if (gpsAgeMs >= 0) "${gpsAgeMs}ms" else "--"
        txtGpsDetails.text = getString(R.string.nautical_sh_gps_details_fmt, posStr, hdopStr, satsStr, gpsLatencyStr)

        // 2. Heading / Compass
        val hdgAgeMs = if (state.timeOfHeadingFix > 0) now - state.timeOfHeadingFix else -1L
        val hasHdg = state.headingTrue != null || state.headingMagnetic != null
        setHealthBadge(badgeCompass, hdgAgeMs, hasHdg)

        val hdgTrueStr = state.headingTrue?.let { String.format(Locale.US, "%03.1f°", Math.toDegrees(it)) } ?: "--"
        val hdgMagStr = state.headingMagnetic?.let { String.format(Locale.US, "%03.1f°", Math.toDegrees(it)) } ?: "--"
        val rotStr = state.rateOfTurn?.let { String.format(Locale.US, "%.1f °/min", it * 60.0) } ?: "--"
        // Observed heading update rate from successive fix timestamps: the
        // old "10.0 Hz" claim was age-inferred, not measured.
        if (state.timeOfHeadingFix > 0 && state.timeOfHeadingFix != prevHeadingFixMs) {
            val wallNow = System.currentTimeMillis()
            if (prevHeadingFixMs > 0 && wallNow > prevHeadingWallMs) {
                val dtMs = wallNow - prevHeadingWallMs
                if (dtMs in 1..10_000) {
                    val hz = 1000.0 / dtMs
                    measuredHeadingHz = measuredHeadingHz?.let { it * 0.7 + hz * 0.3 } ?: hz
                }
            }
            prevHeadingFixMs = state.timeOfHeadingFix
            prevHeadingWallMs = wallNow
        }
        val hdgRateStr = measuredHeadingHz?.let { String.format(Locale.US, "%.1f Hz", it) } ?: "--"
        txtCompassDetails.text = getString(R.string.nautical_sh_compass_details_fmt, hdgTrueStr, hdgMagStr, rotStr, hdgRateStr)

        // 3. Depth Sounder
        val depthAgeMs = if (state.timeOfDepthFix > 0) now - state.timeOfDepthFix else -1L
        val hasDepth = state.depthBelowTransducer != null || state.depthBelowKeel != null || state.depthSurfaceToTransducer != null
        setHealthBadge(badgeDepth, depthAgeMs, hasDepth)

        val depthXdrStr = state.depthBelowTransducer?.let { String.format(Locale.US, "%.2f m", it) } ?: "--"
        val depthKeelStr = state.depthBelowKeel?.let { String.format(Locale.US, "%.2f m", it) } ?: "--"
        val waterTempStr = state.waterTemperature?.let { String.format(Locale.US, "%.1f°C", it - 273.15) } ?: "--"
        txtDepthDetails.text = getString(R.string.nautical_sh_depth_details_fmt, depthXdrStr, depthKeelStr, waterTempStr)

        // 4. Wind Anemometer
        val windAgeMs = if (state.timeOfWindFix > 0) now - state.timeOfWindFix else -1L
        val hasWind = state.windSpeedApparent != null || state.windSpeedTrue != null
        setHealthBadge(badgeWind, windAgeMs, hasWind)

        val awsKnStr = state.windSpeedApparent?.let { String.format(Locale.US, "%.1f kn", SignalKUnitConverter.msToKnots(it)) } ?: "--"
        val awaDegStr = state.windDirectionApparent?.let { String.format(Locale.US, "%03d°", ((Math.toDegrees(it) + 360.0) % 360.0).toInt()) } ?: "--"
        val twsKnStr = state.windSpeedTrue?.let { String.format(Locale.US, "%.1f kn", SignalKUnitConverter.msToKnots(it)) } ?: "--"
        val twdDegStr = state.windDirectionTrue?.let { String.format(Locale.US, "%03d°", ((Math.toDegrees(it) + 360.0) % 360.0).toInt()) } ?: "--"
        txtWindDetails.text = getString(R.string.nautical_sh_wind_details_fmt, awsKnStr, awaDegStr, twsKnStr, twdDegStr)

        // 5. AIS Feed
        val aisManager = NauticalPlugin.getInstance()?.aisManager
        val aisCount = aisManager?.getAisObjects()?.size ?: 0
        val dangerousAisCount = aisManager?.getAisObjects()?.count { ais ->
            val extras = aisManager.getAisExtras(ais.mmsi)
            extras.hasCpaWarning || extras.threatLevel >= 2
        } ?: 0
        val isAisActive = aisCount > 0 || (state.connectionStatus == ConnectionStatus.CONNECTED)

        badgeAis.text = if (isAisActive) getString(R.string.shared_string_ok).uppercase(Locale.US)
            else getString(R.string.nautical_status_standby).uppercase(Locale.US)
        badgeAis.setTextColor(ContextCompat.getColor(themedCtx, if (isAisActive) R.color.color_ok else R.color.color_warning))
        txtAisDetails.text = getString(
                R.string.nautical_sh_ais_details_fmt,
                aisCount,
                dangerousAisCount,
                getString(if (isAisActive) R.string.nautical_sh_feed_active else R.string.nautical_sh_feed_standby)
            )

        // 6. Signal K / Server Multiplexer
        val serverIp = app.settings.NAUTICAL_SERVER_IP.get()
        val serverPort = app.settings.NAUTICAL_SERVER_PORT.get()
        val endpoint = if (serverIp.isNotEmpty()) "$serverIp:$serverPort" else getString(R.string.nautical_sh_endpoint_local)

        when (state.connectionStatus) {
            ConnectionStatus.CONNECTED -> {
                badgeServer.text = getString(R.string.nautical_cockpit_conn_connected).uppercase()
                badgeServer.setTextColor(ContextCompat.getColor(themedCtx, R.color.color_ok))
            }
            ConnectionStatus.CONNECTING -> {
                badgeServer.text = getString(R.string.nautical_cockpit_conn_connecting).uppercase()
                badgeServer.setTextColor(ContextCompat.getColor(themedCtx, R.color.color_warning))
            }
            ConnectionStatus.STALE -> {
                badgeServer.text = getString(R.string.nautical_cockpit_conn_stale).uppercase()
                badgeServer.setTextColor(ContextCompat.getColor(themedCtx, R.color.color_warning))
            }
            ConnectionStatus.DISCONNECTED, ConnectionStatus.UNAUTHORIZED -> {
                badgeServer.text = getString(R.string.nautical_cockpit_conn_disconnected).uppercase()
                badgeServer.setTextColor(ContextCompat.getColor(themedCtx, R.color.text_color_negative))
            }
        }
        // Real round-trip latency telemetry is not wired yet; never show a
        // fabricated number to the operator.
        val serverLatency = "--"
        txtServerDetails.text = getString(R.string.nautical_sh_server_details_fmt, endpoint, serverLatency)
    }

    private fun setHealthBadge(badge: TextView, ageMs: Long, hasValue: Boolean) {
        val ctx = themedCtx
        if (!hasValue || ageMs < 0 || ageMs > 5000) {
            badge.text = getString(R.string.nautical_sh_badge_lost)
            badge.setTextColor(ContextCompat.getColor(ctx, R.color.text_color_negative))
        } else if (ageMs > 2000) {
            badge.text = getString(R.string.nautical_sh_badge_delayed_fmt, ageMs / 1000)
            badge.setTextColor(ContextCompat.getColor(ctx, R.color.color_warning))
        } else {
            badge.text = getString(R.string.nautical_sh_badge_ok_fmt, ageMs)
            badge.setTextColor(ContextCompat.getColor(ctx, R.color.color_ok))
        }
    }

    companion object {
        const val TAG = "SensorHealthBottomSheet"

        fun show(fragmentManager: FragmentManager) {
            if (fragmentManager.isStateSaved) return
            if (fragmentManager.findFragmentByTag(TAG) == null) {
                SensorHealthBottomSheet().show(fragmentManager, TAG)
            }
        }
    }
}
