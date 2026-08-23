package net.osmand.plus.plugins.nautical.network

import com.google.gson.annotations.SerializedName

/**
 * Signal K Resources API Polar Profile and WebSocket Delta Stream data classes.
 */

@kotlinx.serialization.Serializable
data class PolarProfile(
    @SerializedName("name") val name: String?,
    @SerializedName("description") val description: String?,
    val tws: List<Double>?,
    val twa: List<Double>?,
    val speeds: List<List<Double>>?,
)

/**
 * Signal K Route Resource according to Signal K Specification.
 * (GeoJSON LineString with additional attributes)
 */
data class SignalKRoute(
    @SerializedName("name") val name: String?,
    @SerializedName("description") val description: String?,
    @SerializedName("distance") val distance: Double?,
    @SerializedName("feature") val feature: SignalKRouteFeature,
)

data class SignalKRouteFeature(
    @SerializedName("type") val type: String = "Feature",
    @SerializedName("geometry") val geometry: SignalKLineString,
    @SerializedName("properties") val properties: Map<String, Any> = emptyMap()
)

data class SignalKLineString(
    @SerializedName("type") val type: String = "LineString",
    @SerializedName("coordinates") val coordinates: List<List<Double>> // [lon, lat] pairs
)

data class SignalKRouteIdResponse(
    @SerializedName("id") val id: String?
)

data class SignalKWaypoint(
    @SerializedName("name") val name: String?,
    @SerializedName("description") val description: String?,
    @SerializedName("feature") val feature: SignalKPointFeature
)

data class SignalKPointFeature(
    @SerializedName("type") val type: String = "Feature",
    @SerializedName("geometry") val geometry: SignalKPoint,
    @SerializedName("properties") val properties: Map<String, Any> = emptyMap()
)

data class SignalKPoint(
    @SerializedName("type") val type: String = "Point",
    @SerializedName("coordinates") val coordinates: List<Double> // [lon, lat]
)

data class SignalKNote(
    @SerializedName("title") val title: String?,
    @SerializedName("description") val description: String?,
    @SerializedName("position") val position: SignalKPoint? = null,
    @SerializedName("mimeType") val mimeType: String = "text/plain",
    @SerializedName("timestamp") val timestamp: String? = null
)

/**
 * Signal K Logbook Resource.
 */
data class SignalKLogbookEntry(
    @SerializedName("datetime") val timestamp: String,
    @SerializedName("position") val position: SignalKPoint?,
    @SerializedName("category") val category: String?,
    @SerializedName("description") val description: String?,
    @SerializedName("title") val title: String?
)

/**
 * Signal K Checklist Resource.
 */
@kotlinx.serialization.Serializable
data class SignalKChecklist(
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String?,
    @SerializedName("items") val items: List<SignalKChecklistItem>
)

@kotlinx.serialization.Serializable
data class SignalKChecklistItem(
    @SerializedName("title") val title: String,
    @SerializedName("state") val state: String // "pending", "completed"
)

data class SignalKResourceResponse(
    @SerializedName("id") val id: String?
)

/**
 * Signal K Course Object (v2 API).
 */
data class SignalKCourse(
    @SerializedName("activeRoute") val activeRoute: SignalKActiveRoute? = null,
    @SerializedName("nextPoint") val nextPoint: SignalKCoursePoint? = null,
    @SerializedName("previousPoint") val previousPoint: SignalKCoursePoint? = null,
    @SerializedName("targetWaypoint") val targetWaypoint: String? = null,
    @SerializedName("arrivalCircle") val arrivalRadius: Double? = null,
    @SerializedName("anchor") val anchor: SignalKAnchor? = null
)

data class SignalKAnchor(
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("radius") val radius: Double
)

data class SignalKActiveRoute(
    @SerializedName("href") val href: String?,
    @SerializedName("startTime") val startTime: String? = null
)

data class SignalKCoursePoint(
    @SerializedName("position") val position: SignalKPoint? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("href") val href: String? = null
)

/**
 * Signal K Chart Resource.
 */
data class SignalKChart(
    @SerializedName("identifier") val identifier: String,
    @SerializedName("name") val name: String?,
    @SerializedName("description") val description: String?,
    @SerializedName("type") val type: String?, // e.g., "tilelayer", "vector"
    @SerializedName("tilejson") val tilejson: String?,
    @SerializedName("minzoom") val minzoom: Int?,
    @SerializedName("maxzoom") val maxzoom: Int?,
    @SerializedName("bounds") val bounds: List<Double>? // [minLon, minLat, maxLon, maxLat]
)

/**
 * Signal K Region (Restricted Area) Resource.
 */
@kotlinx.serialization.Serializable
data class SignalKRegion(
    @SerializedName("feature") val feature: SignalKRegionFeature,
)

@kotlinx.serialization.Serializable
data class SignalKRegionFeature(
    @SerializedName("type") val type: String = "Feature",
    @kotlinx.serialization.Contextual
    @SerializedName("geometry") val geometry: Map<String, @kotlinx.serialization.Contextual Any>, // Polygons or MultiPolygons
    @kotlinx.serialization.Contextual
    @SerializedName("properties") val properties: Map<String, @kotlinx.serialization.Contextual Any> = emptyMap()
)

data class SignalKTideStation(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("position") val position: SignalKPoint,
    @SerializedName("properties") val properties: Map<String, Any>? = null
)

data class SignalKTideExtreme(
    @SerializedName("timestamp") val timestamp: String,
    @SerializedName("type") val type: String, // "High", "Low"
    @SerializedName("height") val height: Double
)

data class SignalKTidePrediction(
    @SerializedName("timestamp") val timestamp: String,
    @SerializedName("height") val height: Double
)

data class DeltaMessage(
    @SerializedName("context") val context: String?,
    @SerializedName("updates") val updates: List<Update>?
)

data class Update(
    @SerializedName("timestamp") val timestamp: String?,
    @SerializedName("source") val source: Map<String, Any>?,
    @SerializedName("values") val values: List<Value>?
)

data class Value(
    @SerializedName("path") val path: String?,
    @SerializedName("value") val value: Any?
)

data class LivePerformanceData(
    val speedThroughWater: Double? = null, // m/s
    val windSpeedTrue: Double? = null, // m/s
    val windAngleTrueWater: Double? = null, // Radians
    val speedOverGround: Double? = null, // m/s
    val courseOverGround: Double? = null, // Radians
    val latitude: Double? = null,
    val longitude: Double? = null,
    val headingTrue: Double? = null, // Radians
    val headingMagnetic: Double? = null, // Radians
    val magneticVariation: Double? = null, // Radians
    val depthBelowTransducer: Double? = null, // Meters
    val polarSpeed: Double? = null, // m/s
    val targetAngle: Double? = null, // Radians
    val polarSpeedRatio: Double? = null, // 0.0 to 1.0
    val roll: Double? = null,
    val pitch: Double? = null,
    val windAngleApparent: Double? = null,
    val windSpeedApparent: Double? = null,
    val leeway: Double? = null, // Radians
    val destinationLatitude: Double? = null,
    val destinationLongitude: Double? = null,
    val lastWaypointLatitude: Double? = null,
    val lastWaypointLongitude: Double? = null,
    val timeToWaypointVmg: Double? = null,
    val timeToWaypointSog: Double? = null,
    val distanceToWaypoint: Double? = null,
    val drift: Double? = null, // m/s
    val setTrue: Double? = null, // Radians
    val timestamp: Long = System.currentTimeMillis(),
    val timestamps: Map<String, Long> = emptyMap(),
    val sources: Map<String, String> = emptyMap(),
    val history: Map<String, List<Pair<Double, Long>>> = emptyMap()
) {
    val targetTwa: Double? get() = targetAngle
    val targetSpeed: Double? get() = polarSpeed
    val polarEfficiencyPercentage: Double? get() = polarSpeedRatio?.let { it * 100.0 }

    companion object {
        const val PATH_STW = "navigation.speedThroughWater"
        const val PATH_TWS = "environment.wind.speedTrue"
        const val PATH_TWA = "environment.wind.angleTrueWater"
        const val PATH_SOG = "navigation.speedOverGround"
        const val PATH_COG = "navigation.courseOverGroundTrue"
        const val PATH_HEADING_TRUE = "navigation.headingTrue"
        const val PATH_HEADING_MAG = "navigation.headingMagnetic"
        const val PATH_MAG_VARIATION = "navigation.magneticVariation"
        const val PATH_POSITION = "navigation.position"
        const val PATH_DEPTH = "environment.depth.belowTransducer"
        const val PATH_POLAR_SPEED = "performance.polarSpeed"
        const val PATH_TARGET_ANGLE = "performance.targetAngle"
        const val PATH_POLAR_SPEED_RATIO = "performance.polarSpeedRatio"
        const val PATH_ROLL = "navigation.attitude.roll"
        const val PATH_PITCH = "navigation.attitude.pitch"
        const val PATH_LEEWAY = "navigation.leeway"
        const val PATH_AWA = "environment.wind.angleApparent"
        const val PATH_AWS = "environment.wind.speedApparent"
    }
}
