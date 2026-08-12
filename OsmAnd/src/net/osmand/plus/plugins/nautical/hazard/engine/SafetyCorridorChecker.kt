package net.osmand.plus.plugins.nautical.hazard.engine

import com.vividsolutions.jts.geom.Coordinate
import com.vividsolutions.jts.geom.GeometryFactory
import com.vividsolutions.jts.operation.buffer.BufferParameters
import net.osmand.plus.plugins.nautical.routing.model.Waypoint
import net.osmand.plus.plugins.nautical.s57.S57Geometry
import net.osmand.plus.plugins.nautical.s57.S57Object
import net.osmand.plus.plugins.nautical.s57.S57SpatialIndex

/**
 * Scans a projected safety corridor along a route for navigational hazards.
 */
class SafetyCorridorChecker(
    private val indexManager: S57SpatialIndex,
    private val safetyManager: net.osmand.plus.plugins.nautical.engine.NauticalSafetyManager
) {
    private val geometryFactory = GeometryFactory()

    /**
     * Checks a safety corridor around a list of waypoints.
     * @param waypoints Sequence of waypoints defining the track.
     * @return List of identified safety issues.
     */
    fun checkCorridor(waypoints: List<Waypoint>): List<SafetyIssue> {
        val issues = mutableListOf<SafetyIssue>()
        if (waypoints.size < 2) return issues

        val corridorWidthNm = safetyManager.getSafetyCorridorWidthNm()
        val corridorBufferNm = safetyManager.getSafetyCorridorBufferNm()
        val totalHalfWidthNm = corridorWidthNm / 2.0 + corridorBufferNm

        for (i in 0 until waypoints.size - 1) {
            val p1 = waypoints[i]
            val p2 = waypoints[i + 1]

            if (p1.latitude.isNaN() || p1.longitude.isNaN() || p2.latitude.isNaN() || p2.longitude.isNaN()) continue

            // Item 7 Fix: Account for longitude convergence
            val midLat = (p1.latitude + p2.latitude) / 2.0
            val halfWidthDegrees = totalHalfWidthNm / (60.0 * kotlin.math.cos(Math.toRadians(midLat)).coerceAtLeast(0.01))

            val line = geometryFactory.createLineString(arrayOf(
                Coordinate(p1.longitude, p1.latitude),
                Coordinate(p2.longitude, p2.latitude)
            ))

            val corridor = line.buffer(halfWidthDegrees, 8, BufferParameters.CAP_ROUND)

            val candidates = indexManager.queryFeatures(corridor)
            for (hazard in candidates) {
                // Fine-grained intersection check on candidate set
                val intersects = hazard.geometries.any { geo ->
                    geo.toJtsGeometry(geometryFactory)?.intersects(corridor) == true
                }

                if (intersects) {
                    val issue = evaluateHazard(hazard, i)
                    if (issue != null) {
                        issues.add(issue)
                    }
                }
            }
            
            // Check dynamic SignalK regions
            checkSignalKRegions(corridor, issues, i)
            
            // Check forward hazards if they have positions
            checkForwardHazards(corridor, issues, i)
        }
        return issues
    }

    private fun checkForwardHazards(area: com.vividsolutions.jts.geom.Geometry, issues: MutableList<SafetyIssue>, segmentIndex: Int) {
        val hazards = safetyManager.getForwardHazards()
        for (hazard in hazards) {
            val pos = hazard.position ?: continue
            val point = geometryFactory.createPoint(Coordinate(pos.second, pos.first))
            if (area.intersects(point)) {
                issues.add(SafetyIssue(
                    segmentIndex,
                    "Forward Watch: ${hazard.name}",
                    S57Object(0L, "FORWARD_HAZARD", net.osmand.plus.plugins.nautical.s57.S57PrimitiveType.POINT, emptyMap(), emptyList()),
                    if (hazard.severity == net.osmand.plus.plugins.nautical.engine.NotificationState.EMERGENCY || hazard.severity == net.osmand.plus.plugins.nautical.engine.NotificationState.ALARM) Severity.DANGER else Severity.WARNING
                ))
            }
        }
    }

    private fun checkSignalKRegions(area: com.vividsolutions.jts.geom.Geometry, issues: MutableList<SafetyIssue>, segmentIndex: Int) {
        val regions = safetyManager.getSignalKRegions()
        for (region in regions) {
            val name = region.feature.properties["name"] as? String ?: "SignalK Region"
            // Simple spatial check for bounding boxes if geometry is complex
            val bounds = region.feature.properties["bounds"] as? List<*>
            if (bounds != null && bounds.size == 4) {
                // [minLon, minLat, maxLon, maxLat]
                val minLon = (bounds[0] as? Number)?.toDouble() ?: 0.0
                val minLat = (bounds[1] as? Number)?.toDouble() ?: 0.0
                val maxLon = (bounds[2] as? Number)?.toDouble() ?: 0.0
                val maxLat = (bounds[3] as? Number)?.toDouble() ?: 0.0
                
                val env = com.vividsolutions.jts.geom.Envelope(minLon, maxLon, minLat, maxLat)
                if (area.envelopeInternal.intersects(env)) {
                    // Logic to create a virtual S57Object for SignalK region to satisfy evaluatedHazard
                    // but here we can just add a SafetyIssue directly
                    issues.add(SafetyIssue(
                        segmentIndex, 
                        name, 
                        S57Object(0L, "SIGNALK_REGION", net.osmand.plus.plugins.nautical.s57.S57PrimitiveType.AREA, emptyMap(), emptyList()), 
                        Severity.WARNING
                    ))
                }
            }
        }
    }

    fun checkLookAhead(lat: Double, lon: Double): List<SafetyIssue> {
        if (lat.isNaN() || lon.isNaN()) return emptyList()
        val issues = mutableListOf<SafetyIssue>()
        val lookAheadRadiusNm = safetyManager.getLookAheadRadiusNm()
        val radiusDegrees = lookAheadRadiusNm / 60.0

        val point = geometryFactory.createPoint(Coordinate(lon, lat))
        val lookAheadArea = point.buffer(radiusDegrees)

        val candidates = indexManager.queryFeatures(lookAheadArea)
        for (hazard in candidates) {
            val intersects = hazard.geometries.any { geo ->
                geo.toJtsGeometry(geometryFactory)?.intersects(lookAheadArea) == true
            }
            if (intersects) {
                evaluateHazard(hazard, -1)?.let { issues.add(it) }
            }
        }
        
        // Also check forward hazards
        checkForwardHazards(lookAheadArea, issues, -1)
        
        return issues
    }

    /**
     * Checks if a specific point is safe for the vessel's draft.
     */
    fun isPointSafe(lat: Double, lon: Double): Boolean {
        val point = geometryFactory.createPoint(Coordinate(lon, lat))
        val candidates = indexManager.queryFeatures(point)

        for (hazard in candidates) {
            val intersects = hazard.geometries.any { geo ->
                geo.toJtsGeometry(geometryFactory)?.intersects(point) == true
            }
            if (intersects) {
                val issue = evaluateHazard(hazard, -1)
                if (issue != null && issue.severity == Severity.DANGER) {
                    return false
                }
            }
        }
        return true
    }

    /**
     * Checks if a linear segment intersects any navigational hazards.
     */
    fun checkCorridorIntersection(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Boolean {
        val line = geometryFactory.createLineString(arrayOf(
            Coordinate(lon1, lat1),
            Coordinate(lon2, lat2)
        ))

        val candidates = indexManager.queryFeatures(line)
        for (hazard in candidates) {
            val intersects = hazard.geometries.any { geo ->
                geo.toJtsGeometry(geometryFactory)?.intersects(line) == true
            }
            if (intersects) {
                val issue = evaluateHazard(hazard, -1)
                if (issue != null && issue.severity == Severity.DANGER) {
                    return true // Intersection with danger
                }
            }
        }
        return false
    }

    private fun evaluateHazard(hazard: S57Object, segmentIndex: Int): SafetyIssue? {
        val minSafeDepth = safetyManager.getMinSafeDepth()

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
            "RESTRN", "DMPGRD", "MILARE", "SIGNALK_REGION" -> SafetyIssue(segmentIndex, hazard.attributes["name"] ?: "Restricted Area", hazard, Severity.WARNING)
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
