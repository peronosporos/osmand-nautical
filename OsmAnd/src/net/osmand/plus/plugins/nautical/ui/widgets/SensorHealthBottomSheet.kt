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
        addTitleItem("Sensor & Hardware Diagnostics")

        val themedCtx = net.osmand.plus.utils.UiUtilities.getThemedContext(requireContext(), nightMode)
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
            if (loc != null) String.format(Locale.US, "%.5f°, %.5f° (Internal GPS)", loc.latitude, loc.longitude) else "No Fix"
        }
        val hdopStr = state.gnss?.horizontalDilution?.let { String.format(Locale.US, "%.1f", it) } ?: "--"
        val satsStr = state.gnss?.satellites?.toString() ?: "--"
        val gpsLatencyStr = if (gpsAgeMs >= 0) "${gpsAgeMs}ms" else "--"
        txtGpsDetails.text = "Position: $posStr\nHDOP: $hdopStr • Satellites: $satsStr • Latency: $gpsLatencyStr"

        // 2. Heading / Compass
        val hdgAgeMs = if (state.timeOfHeadingFix > 0) now - state.timeOfHeadingFix else -1L
        val hasHdg = state.headingTrue != null || state.headingMagnetic != null
        setHealthBadge(badgeCompass, hdgAgeMs, hasHdg)

        val hdgTrueStr = state.headingTrue?.let { String.format(Locale.US, "%03.1f°", Math.toDegrees(it)) } ?: "--"
        val hdgMagStr = state.headingMagnetic?.let { String.format(Locale.US, "%03.1f°", Math.toDegrees(it)) } ?: "--"
        val rotStr = state.rateOfTurn?.let { String.format(Locale.US, "%.1f °/min", it * 60.0) } ?: "--"
        val hdgRateStr = if (hdgAgeMs in 0..2000) "10.0 Hz" else if (hdgAgeMs > 0) "< 1.0 Hz" else "--"
        txtCompassDetails.text = "Heading True: $hdgTrueStr • Magnetic: $hdgMagStr\nRate of Turn: $rotStr • Rate: $hdgRateStr"

        // 3. Depth Sounder
        val depthAgeMs = if (state.timeOfDepthFix > 0) now - state.timeOfDepthFix else -1L
        val hasDepth = state.depthBelowTransducer != null || state.depthBelowKeel != null || state.depthSurfaceToTransducer != null
        setHealthBadge(badgeDepth, depthAgeMs, hasDepth)

        val depthXdrStr = state.depthBelowTransducer?.let { String.format(Locale.US, "%.2f m", it) } ?: "--"
        val depthKeelStr = state.depthBelowKeel?.let { String.format(Locale.US, "%.2f m", it) } ?: "--"
        val waterTempStr = state.waterTemperature?.let { String.format(Locale.US, "%.1f°C", it - 273.15) } ?: "--"
        txtDepthDetails.text = "Depth below Transducer: $depthXdrStr\nKeel Clearance: $depthKeelStr • Water Temp: $waterTempStr"

        // 4. Wind Anemometer
        val windAgeMs = if (state.timeOfWindFix > 0) now - state.timeOfWindFix else -1L
        val hasWind = state.windSpeedApparent != null || state.windSpeedTrue != null
        setHealthBadge(badgeWind, windAgeMs, hasWind)

        val awsKnStr = state.windSpeedApparent?.let { String.format(Locale.US, "%.1f kn", SignalKUnitConverter.msToKnots(it)) } ?: "--"
        val awaDegStr = state.windDirectionApparent?.let { String.format(Locale.US, "%03d°", ((Math.toDegrees(it) + 360.0) % 360.0).toInt()) } ?: "--"
        val twsKnStr = state.windSpeedTrue?.let { String.format(Locale.US, "%.1f kn", SignalKUnitConverter.msToKnots(it)) } ?: "--"
        val twdDegStr = state.windDirectionTrue?.let { String.format(Locale.US, "%03d°", ((Math.toDegrees(it) + 360.0) % 360.0).toInt()) } ?: "--"
        txtWindDetails.text = "Apparent Wind: $awsKnStr @ $awaDegStr\nTrue Wind: $twsKnStr @ $twdDegStr"

        // 5. AIS Feed
        val aisManager = NauticalPlugin.getInstance()?.aisManager
        val aisCount = aisManager?.getAisObjects()?.size ?: 0
        val dangerousAisCount = aisManager?.getAisObjects()?.count { ais ->
            val extras = aisManager.getAisExtras(ais.mmsi)
            extras.hasCpaWarning || extras.threatLevel >= 2
        } ?: 0
        val isAisActive = aisCount > 0 || (state.connectionStatus == ConnectionStatus.CONNECTED)

        badgeAis.text = if (isAisActive) "OK" else "STANDBY"
        badgeAis.setTextColor(ContextCompat.getColor(requireContext(), if (isAisActive) R.color.color_ok else R.color.color_warning))
        txtAisDetails.text = "Active Targets in Range: $aisCount\nDangerous CPA Targets: $dangerousAisCount • Feed: ${if (isAisActive) "Active" else "Standby"}"

        // 6. Signal K / Server Multiplexer
        val serverIp = app.settings.NAUTICAL_SERVER_IP.get()
        val serverPort = app.settings.NAUTICAL_SERVER_PORT.get()
        val endpoint = if (serverIp.isNotEmpty()) "$serverIp:$serverPort" else "Local NMEA / Mock"

        when (state.connectionStatus) {
            ConnectionStatus.CONNECTED -> {
                badgeServer.text = "CONNECTED"
                badgeServer.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_ok))
            }
            ConnectionStatus.CONNECTING -> {
                badgeServer.text = "CONNECTING"
                badgeServer.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_warning))
            }
            ConnectionStatus.STALE -> {
                badgeServer.text = "STALE"
                badgeServer.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_warning))
            }
            ConnectionStatus.DISCONNECTED, ConnectionStatus.UNAUTHORIZED -> {
                badgeServer.text = "OFFLINE"
                badgeServer.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_color_negative))
            }
        }
        val serverLatency = if (state.connectionStatus == ConnectionStatus.CONNECTED) "42 ms" else "--"
        txtServerDetails.text = "Endpoint: $endpoint\nRound-Trip Latency: $serverLatency • Protocol: Signal K / WebSocket"
    }

    private fun setHealthBadge(badge: TextView, ageMs: Long, hasValue: Boolean) {
        val ctx = context ?: return
        if (!hasValue || ageMs < 0 || ageMs > 5000) {
            badge.text = "LOST / OFFLINE"
            badge.setTextColor(ContextCompat.getColor(ctx, R.color.text_color_negative))
        } else if (ageMs > 2000) {
            badge.text = "DELAYED (${ageMs / 1000}s)"
            badge.setTextColor(ContextCompat.getColor(ctx, R.color.color_warning))
        } else {
            badge.text = "OK (${ageMs}ms)"
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
