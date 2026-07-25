package net.osmand.plus.plugins.nautical.s57.style

import net.osmand.data.LatLon
import net.osmand.plus.plugins.nautical.s57.S57Geometry
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Utility for optimizing S-57 geometries for smoother rendering.
 */
object S57GeometryOptimizer {

    /**
     * Optimizes a geometry by simplifying its points using the Douglas-Peucker algorithm.
     * @param tolerance The maximum allowed deviation in degrees (e.g., 0.00001).
     */
    fun optimize(geometry: S57Geometry, tolerance: Double): S57Geometry {
        return when (geometry) {
            is S57Geometry.Point -> geometry
            is S57Geometry.MultiPoint -> geometry
            is S57Geometry.Line -> S57Geometry.Line(simplifyPoints(geometry.nodes, tolerance))
            is S57Geometry.Area -> S57Geometry.Area(geometry.boundaries.map { simplifyPoints(it, tolerance) })
        }
    }

    private fun simplifyPoints(points: List<LatLon>, tolerance: Double): List<LatLon> {
        if (points.size <= 2) return points

        var maxDistance = 0.0
        var index = 0
        val end = points.size - 1

        for (i in 1 until end) {
            val d = perpendicularDistance(points[i], points[0], points[end])
            if (d > maxDistance) {
                index = i
                maxDistance = d
            }
        }

        return if (maxDistance > tolerance) {
            val res1 = simplifyPoints(points.subList(0, index + 1), tolerance)
            val res2 = simplifyPoints(points.subList(index, points.size), tolerance)
            res1.dropLast(1) + res2
        } else {
            listOf(points[0], points[end])
        }
    }

    private fun perpendicularDistance(p: LatLon, start: LatLon, end: LatLon): Double {
        val x = p.longitude
        val y = p.latitude
        val x1 = start.longitude
        val y1 = start.latitude
        val x2 = end.longitude
        val y2 = end.latitude

        val dx = x2 - x1
        val dy = y2 - y1

        if (dx == 0.0 && dy == 0.0) {
            return sqrt((x - x1) * (x - x1) + (y - y1) * (y - y1))
        }

        return abs(dy * x - dx * y + x2 * y1 - y2 * x1) / sqrt(dx * dx + dy * dy)
    }
}
