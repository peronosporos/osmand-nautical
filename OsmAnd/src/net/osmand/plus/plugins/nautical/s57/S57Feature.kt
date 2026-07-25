package net.osmand.plus.plugins.nautical.s57

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
