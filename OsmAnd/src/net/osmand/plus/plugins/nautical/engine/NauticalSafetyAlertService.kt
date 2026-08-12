package net.osmand.plus.plugins.nautical.engine

import net.osmand.plus.OsmandApplication
import net.osmand.plus.R as OsmAndR
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.audio.AlarmType
import net.osmand.plus.plugins.nautical.audio.NauticalAudioArbiter
import net.osmand.plus.plugins.nautical.hazard.engine.SafetyCorridorChecker
import net.osmand.plus.plugins.nautical.hazard.engine.Severity
import net.osmand.shared.util.KMapUtils
import kotlin.math.abs

/**
 * Background service for monitoring map-based hazards (S-57/ENC) and Signal K regions.
 * Implements throttling to ensure efficient look-ahead scans.
 */
class NauticalSafetyAlertService(private val app: OsmandApplication) {
    
    private var lastScanLat = 0.0
    private var lastScanLon = 0.0
    private var lastScanTime = 0L

    companion object {
        private const val SCAN_DISTANCE_THRESHOLD_METERS = 50.0
        private const val SCAN_TIME_THRESHOLD_MS = 5000L
    }

    fun processSafetyUpdate(state: MarineState, notifications: MutableMap<String, SignalKNotification>) {
        val lat = state.latitude ?: return
        val lon = state.longitude ?: return
        val now = System.currentTimeMillis()

        // Throttling: only scan if vessel moved significantly or enough time passed
        val dist = KMapUtils.getDistance(lastScanLat, lastScanLon, lat, lon)
        if (dist < SCAN_DISTANCE_THRESHOLD_METERS && (now - lastScanTime) < SCAN_TIME_THRESHOLD_MS) {
            return
        }

        lastScanLat = lat
        lastScanLon = lon
        lastScanTime = now

        val index = NauticalPlugin.getInstance()?.s57SpatialIndex ?: return
        val sm = NauticalPlugin.getInstance()?.safetyManager ?: return
        
        val checker = SafetyCorridorChecker(index, sm)
        val issues = checker.checkLookAhead(lat, lon)
        
        val danger = issues.find { it.severity == Severity.DANGER }
        if (danger != null) {
            val msg = app.getString(OsmAndR.string.nautical_hazard_ahead) + ": " + danger.description
            notifications["safety.hazard.danger"] = SignalKNotification(msg, NotificationState.ALARM)
            NauticalAudioArbiter.getInstance(app).dispatchAlarm(AlarmType.MAP_HAZARD, voiceText = msg)
        } else {
            val warning = issues.find { it.severity == Severity.WARNING }
            if (warning != null) {
                val msg = app.getString(OsmAndR.string.nautical_hazard_ahead) + ": " + warning.description
                notifications["safety.hazard.warning"] = SignalKNotification(msg, NotificationState.WARN)
            }
        }
    }
}
