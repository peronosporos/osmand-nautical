package net.osmand.plus.plugins.nautical.network

import com.google.gson.annotations.SerializedName

/**
 * Signal K Resources API Polar Profile and WebSocket Delta Stream data classes.
 */

data class PolarProfile(
    @SerializedName("name") val name: String?,
    @SerializedName("description") val description: String?,
    @SerializedName("tws") val tws: List<Double>?,
    @SerializedName("twa") val twa: List<Double>?,
    @SerializedName("speeds") val speeds: List<List<Double>>?,
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
    val depthBelowTransducer: Double? = null, // Meters
    val polarSpeed: Double? = null, // m/s
    val targetAngle: Double? = null, // Radians
    val polarSpeedRatio: Double? = null, // 0.0 to 1.0
    val timestamp: Long = System.currentTimeMillis(),
    val timestamps: Map<String, Long> = emptyMap(),
    val sources: Map<String, String> = emptyMap()
) {
    companion object {
        const val PATH_STW = "navigation.speedThroughWater"
        const val PATH_TWS = "environment.wind.speedTrue"
        const val PATH_TWA = "environment.wind.angleTrueWater"
        const val PATH_SOG = "navigation.speedOverGround"
        const val PATH_COG = "navigation.courseOverGroundTrue"
        const val PATH_POSITION = "navigation.position"
        const val PATH_HEADING = "navigation.headingTrue"
        const val PATH_DEPTH = "environment.depth.belowTransducer"
        const val PATH_POLAR_SPEED = "performance.polarSpeed"
        const val PATH_TARGET_ANGLE = "performance.targetAngle"
        const val PATH_POLAR_SPEED_RATIO = "performance.polarSpeedRatio"
    }
}
