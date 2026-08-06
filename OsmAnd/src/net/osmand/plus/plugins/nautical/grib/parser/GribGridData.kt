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
    val waveDirectionGrid: Array<DoubleArray>? = null, // Wave direction (degrees)
    val currentUGrid: Array<DoubleArray>? = null,   // Eastward current (m/s)
    val currentVGrid: Array<DoubleArray>? = null    // Northward current (m/s)
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TimeStepGrid

        if (timestamp != other.timestamp) return false
        if (!uGrid.contentDeepEquals(other.uGrid)) return false
        if (!vGrid.contentDeepEquals(other.vGrid)) return false
        if (!pressureGrid.contentDeepEquals(other.pressureGrid)) return false
        if (!waveHeightGrid.contentDeepEquals(other.waveHeightGrid)) return false
        if (!waveDirectionGrid.contentDeepEquals(other.waveDirectionGrid)) return false
        if (!currentUGrid.contentDeepEquals(other.currentUGrid)) return false
        if (!currentVGrid.contentDeepEquals(other.currentVGrid)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = timestamp.hashCode()
        result = 31 * result + uGrid.contentDeepHashCode()
        result = 31 * result + vGrid.contentDeepHashCode()
        result = 31 * result + (pressureGrid?.contentDeepHashCode() ?: 0)
        result = 31 * result + (waveHeightGrid?.contentDeepHashCode() ?: 0)
        result = 31 * result + (waveDirectionGrid?.contentDeepHashCode() ?: 0)
        result = 31 * result + (currentUGrid?.contentDeepHashCode() ?: 0)
        result = 31 * result + (currentVGrid?.contentDeepHashCode() ?: 0)
        return result
    }
}

data class GribGridData(
    val header: GribHeader,
    val timeSteps: List<TimeStepGrid>
)
