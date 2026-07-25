package net.osmand.plus.plugins.nautical.hazard.engine

import com.vividsolutions.jts.geom.*
import com.vividsolutions.jts.operation.buffer.BufferParameters
import net.osmand.plus.plugins.nautical.s57.S57Object
import net.osmand.plus.plugins.nautical.s57.S57SpatialIndex
import net.osmand.plus.plugins.nautical.s57.S57Geometry
import net.osmand.plus.plugins.nautical.routing.model.Waypoint

/**
 * Scans a projected safety corridor along a route for navigational hazards.
 */
class SafetyCorridorChecker(
    private val indexManager: S57SpatialIndex,
    private val vesselDraft: Double,
    private val safetyMargin: Double
) {
    private val geometryFactory = GeometryFactory()

    /**
     * Checks a safety corridor around a list of waypoints.
     * @param waypoints Sequence of waypoints defining the track.
     * @param corridorWidthNm Width of the corridor in Nautical Miles (total width).
     * @return List of identified safety issues.
     */
    fun checkCorridor(waypoints: List<Waypoint>, corridorWidthNm: Double): List<SafetyIssue> {
        val issues = mutableListOf<SafetyIssue>()
        if (waypoints.size < 2) return issues

        // Half width for JTS buffer
        val halfWidthDegrees = (corridorWidthNm / 2.0) / 60.0 

        for (i in 0 until waypoints.size - 1) {
            val p1 = waypoints[i]
            val p2 = waypoints[i + 1]
            
            val line = geometryFactory.createLineString(arrayOf(
                Coordinate(p1.longitude, p1.latitude),
                Coordinate(p2.longitude, p2.latitude)
            ))
            
            val corridor = line.buffer(halfWidthDegrees, 8, BufferParameters.CAP_ROUND)
            
            val candidates = indexManager.queryFeatures(corridor)
            for (hazard in candidates) {
                val issue = evaluateHazard(hazard, i)
                if (issue != null) {
                    issues.add(issue)
                }
            }
        }
        return issues
    }

    private fun evaluateHazard(hazard: S57Object, segmentIndex: Int): SafetyIssue? {
        val minSafeDepth = vesselDraft + safetyMargin

        return when (hazard.acronym) {
            "DEPARE" -> {
                val drval1 = hazard.attributes["DRVAL1"]?.toDoubleOrNull() ?: 0.0
                if (drval1 < minSafeDepth) {
                    SafetyIssue(segmentIndex, "Shallow Water: ${drval1}m", hazard, Severity.DANGER)
                } else null
            }
            "SOUNDG" -> {
                val minSounding = hazard.geometries.filterIsInstance<S57Geometry.Point>()
                    .mapNotNull { it.depth }
                    .minOrNull()
                
                if (minSounding != null && minSounding < minSafeDepth) {
                    SafetyIssue(segmentIndex, "Shallow Sounding: ${minSounding}m", hazard, Severity.DANGER)
                } else null
            }
            "WRECKS" -> SafetyIssue(segmentIndex, "Wreck", hazard, Severity.DANGER)
            "OBSTRN" -> SafetyIssue(segmentIndex, "Obstruction", hazard, Severity.DANGER)
            "RESTRN", "DMPGRD", "MILARE" -> SafetyIssue(segmentIndex, "Restricted Area", hazard, Severity.WARNING)
            "UWTROC" -> SafetyIssue(segmentIndex, "Underwater Rock", hazard, Severity.DANGER)
            else -> null
        }
    }
}

enum class Severity {
    WARNING, DANGER
}

data class SafetyIssue(
    val segmentIndex: Int,
    val description: String,
    val feature: S57Object,
    val severity: Severity
)
