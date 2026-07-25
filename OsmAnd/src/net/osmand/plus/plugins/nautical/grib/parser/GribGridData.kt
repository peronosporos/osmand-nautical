package net.osmand.plus.plugins.nautical.grib.parser

data class GribHeader(
    val latMin: Double,
    val latMax: Double,
    val lonMin: Double,
    val lonMax: Double,
    val latSteps: Int,
    val lonSteps: Int
)

data class WindVector(
    val u: Double, // eastward wind (m/s)
    val v: Double  // northward wind (m/s)
) {
    val speed: Double
        get() = kotlin.math.sqrt(u * u + v * v)

    val direction: Double
        get() = (kotlin.math.atan2(-u, -v) * 180.0 / Math.PI + 360.0) % 360.0
}

data class WaveVector(
    val height: Double,    // Significant wave height (m)
    val direction: Double  // Mean wave direction (degrees)
)

data class TimeStepGrid(
    val timestamp: Long,
    val uGrid: Array<DoubleArray>,
    val vGrid: Array<DoubleArray>,
    val pressureGrid: Array<DoubleArray>? = null,    // Surface pressure (hPa)
    val waveHeightGrid: Array<DoubleArray>? = null,  // Significant wave height (m)
    val waveDirectionGrid: Array<DoubleArray>? = null // Wave direction (degrees)
)

data class GribGridData(
    val header: GribHeader,
    val timeSteps: List<TimeStepGrid>
)
