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
    val uGrid: FloatArray,
    val vGrid: FloatArray,
    val pressureGrid: FloatArray? = null,    // Surface pressure (hPa)
    val waveHeightGrid: FloatArray? = null,  // Significant wave height (m)
    val waveDirectionGrid: FloatArray? = null, // Wave direction (degrees)
    val currentUGrid: FloatArray? = null,   // Eastward current (m/s)
    val currentVGrid: FloatArray? = null    // Northward current (m/s)
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TimeStepGrid

        if (timestamp != other.timestamp) return false
        if (!uGrid.contentEquals(other.uGrid)) return false
        if (!vGrid.contentEquals(other.vGrid)) return false
        if (pressureGrid != null) {
            if (other.pressureGrid == null) return false
            if (!pressureGrid.contentEquals(other.pressureGrid)) return false
        } else if (other.pressureGrid != null) return false
        
        if (waveHeightGrid != null) {
            if (other.waveHeightGrid == null) return false
            if (!waveHeightGrid.contentEquals(other.waveHeightGrid)) return false
        } else if (other.waveHeightGrid != null) return false

        if (waveDirectionGrid != null) {
            if (other.waveDirectionGrid == null) return false
            if (!waveDirectionGrid.contentEquals(other.waveDirectionGrid)) return false
        } else if (other.waveDirectionGrid != null) return false

        if (currentUGrid != null) {
            if (other.currentUGrid == null) return false
            if (!currentUGrid.contentEquals(other.currentUGrid)) return false
        } else if (other.currentUGrid != null) return false

        if (currentVGrid != null) {
            if (other.currentVGrid == null) return false
            if (!currentVGrid.contentEquals(other.currentVGrid)) return false
        } else if (other.currentVGrid != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = timestamp.hashCode()
        result = 31 * result + uGrid.contentHashCode()
        result = 31 * result + vGrid.contentHashCode()
        result = 31 * result + (pressureGrid?.contentHashCode() ?: 0)
        result = 31 * result + (waveHeightGrid?.contentHashCode() ?: 0)
        result = 31 * result + (waveDirectionGrid?.contentHashCode() ?: 0)
        result = 31 * result + (currentUGrid?.contentHashCode() ?: 0)
        result = 31 * result + (currentVGrid?.contentHashCode() ?: 0)
        return result
    }
}

data class GribGridData(
    val header: GribHeader,
    val timeSteps: List<TimeStepGrid>,
    var fileName: String? = null
)
