package net.osmand.plus.plugins.nautical.s57

import com.vividsolutions.jts.geom.Coordinate
import com.vividsolutions.jts.geom.Geometry
import com.vividsolutions.jts.geom.GeometryFactory
import net.osmand.data.LatLon

/**
 * Represents an S-57 object (feature).
 */
data class S57Object(
    val id: Long,
    val acronym: String,
    val primitiveType: S57PrimitiveType,
    val attributes: Map<String, String> = emptyMap(),
    val geometries: List<S57Geometry> = emptyList()
)

/**
 * Primitive types in S-57.
 */
enum class S57PrimitiveType(val code: Int) {
    POINT(1),
    LINE(2),
    AREA(3),
    UNKNOWN(0);

    companion object {
        fun fromCode(code: Int): S57PrimitiveType {
            return values().find { it.code == code } ?: UNKNOWN
        }
    }
}

/**
 * Represents the spatial component of an S-57 object.
 */
sealed class S57Geometry {
    data class Point(val position: LatLon, val depth: Double? = null) : S57Geometry()
    data class MultiPoint(val positions: List<LatLon>, val depths: List<Double> = emptyList()) : S57Geometry()
    data class Line(val nodes: List<LatLon>) : S57Geometry()
    data class Area(val boundaries: List<List<LatLon>>) : S57Geometry()

    /**
     * Converts S-57 geometry to JTS Geometry.
     */
    fun toJtsGeometry(factory: GeometryFactory): Geometry? {
        return when (this) {
            is Point -> factory.createPoint(Coordinate(position.longitude, position.latitude))
            is MultiPoint -> {
                val coords = positions.map { Coordinate(it.longitude, it.latitude) }.toTypedArray()
                factory.createMultiPoint(coords)
            }
            is Line -> {
                val coords = nodes.map { Coordinate(it.longitude, it.latitude) }.toTypedArray()
                factory.createLineString(coords)
            }
            is Area -> {
                val shells = boundaries.map { ring ->
                    val coords = ring.map { Coordinate(it.longitude, it.latitude) }.toTypedArray()
                    // Ensure closed linear ring
                    val closedCoords = if (coords.isNotEmpty() && coords.first() != coords.last()) {
                        coords + coords.first()
                    } else {
                        coords
                    }
                    factory.createLinearRing(closedCoords)
                }
                if (shells.isEmpty()) null
                else factory.createPolygon(shells.first(), shells.drop(1).toTypedArray())
            }
        }
    }
}

/**
 * Intermediate record for spatial data in ISO 8211.
 */
data class S57SpatialRecord(
    val id: Int,
    val type: String, // VRID type: VI, VC, VE
    val coordinates: List<LatLon> = emptyList(),
    val depths: List<Double> = emptyList(),
    val referencedNodes: List<Int> = emptyList()
)
