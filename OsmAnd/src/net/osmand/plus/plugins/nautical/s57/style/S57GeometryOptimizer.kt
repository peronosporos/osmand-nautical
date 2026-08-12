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
    fun optimize(geometry: S57Geometry, tolerance: Double, acronym: String? = null): S57Geometry {
        // Safety-Critical Feature Exemptions:
        // Isolated Rocks (UWTROC), Wrecks (WRECKS), Obstructions (OBSTRN), and Lights (LIGHTS)
        // MUST NEVER be culled or simplified to preserve navigation safety.
        if (acronym == "UWTROC" || acronym == "WRECKS" || acronym == "OBSTRN" || acronym == "LIGHTS") {
            return geometry
        }

        return when (geometry) {
            is S57Geometry.Point -> geometry
            is S57Geometry.MultiPoint -> geometry
            is S57Geometry.Line -> S57Geometry.Line(simplifyPoints(geometry.nodes, tolerance))
            is S57Geometry.Area -> S57Geometry.Area(geometry.boundaries.map { simplifyPoints(it, tolerance) })
        }
    }

    private fun simplifyPoints(points: List<LatLon>, tolerance: Double): List<LatLon> {
        if (points.size <= 2) return points

        val keep = BooleanArray(points.size)
        keep[0] = true
        keep[points.size - 1] = true

        val stack = java.util.Stack<Pair<Int, Int>>()
        stack.push(0 to points.size - 1)

        while (stack.isNotEmpty()) {
            val (start, end) = stack.pop()
            if (end - start <= 1) continue

            var maxDistance = 0.0
            var index = start

            for (i in start + 1 until end) {
                val d = perpendicularDistance(points[i], points[start], points[end])
                if (d > maxDistance) {
                    index = i
                    maxDistance = d
                }
            }

            if (maxDistance > tolerance) {
                keep[index] = true
                stack.push(start to index)
                stack.push(index to end)
            }
        }

        val result = mutableListOf<LatLon>()
        for (i in points.indices) {
            if (keep[i]) result.add(points[i])
        }
        return result
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
