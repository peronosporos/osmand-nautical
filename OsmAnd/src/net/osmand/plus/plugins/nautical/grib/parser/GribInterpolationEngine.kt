package net.osmand.plus.plugins.nautical.grib.parser

class GribInterpolationEngine(private val gridData: GribGridData) {

    fun getWindVector(lat: Double, lon: Double, timestamp: Long): WindVector? {
        val (lower, upper, ratio) = getTimeSteps(timestamp) ?: return null
        val windAtLower = interpolateWind(lat, lon, lower) ?: return null
        val windAtUpper = interpolateWind(lat, lon, upper) ?: return null

        val u = windAtLower.u + ratio * (windAtUpper.u - windAtLower.u)
        val v = windAtLower.v + ratio * (windAtUpper.v - windAtLower.v)
        return WindVector(u, v)
    }

    fun getCurrentVector(lat: Double, lon: Double, timestamp: Long): WindVector? {
        val (lower, upper, ratio) = getTimeSteps(timestamp) ?: return null
        val curAtLower = interpolateCurrent(lat, lon, lower) ?: return null
        val curAtUpper = interpolateCurrent(lat, lon, upper) ?: return null

        val u = curAtLower.u + ratio * (curAtUpper.u - curAtLower.u)
        val v = curAtLower.v + ratio * (curAtUpper.v - curAtLower.v)
        return WindVector(u, v)
    }

    fun getPressure(lat: Double, lon: Double, timestamp: Long): Double? {
        val (lower, upper, ratio) = getTimeSteps(timestamp) ?: return null
        val pAtLower = interpolateScalar(lat, lon, lower.pressureGrid) ?: return null
        val pAtUpper = interpolateScalar(lat, lon, upper.pressureGrid) ?: return null
        return pAtLower + ratio * (pAtUpper - pAtLower)
    }

    fun getWaveData(lat: Double, lon: Double, timestamp: Long): WaveVector? {
        val (lower, upper, ratio) = getTimeSteps(timestamp) ?: return null
        val hAtLower = interpolateScalar(lat, lon, lower.waveHeightGrid) ?: return null
        val dAtLower = interpolateScalar(lat, lon, lower.waveDirectionGrid) ?: return null
        val hAtUpper = interpolateScalar(lat, lon, upper.waveHeightGrid) ?: return null
        val dAtUpper = interpolateScalar(lat, lon, upper.waveDirectionGrid) ?: return null

        val h = hAtLower + ratio * (hAtUpper - hAtLower)
        
        // Wrap-around aware interpolation for degrees
        var diff = dAtUpper - dAtLower
        while (diff > 180.0) diff -= 360.0
        while (diff < -180.0) diff += 360.0
        val d = (dAtLower + ratio * diff + 360.0) % 360.0
        
        return WaveVector(h, d)
    }

    private fun getTimeSteps(timestamp: Long): Triple<TimeStepGrid, TimeStepGrid, Double>? {
        val steps = gridData.timeSteps
        if (steps.isEmpty()) return null

        val sortedSteps = steps.sortedBy { it.timestamp }
        var lower = sortedSteps.first()
        var upper = sortedSteps.last()

        if (timestamp <= lower.timestamp) return Triple(lower, lower, 0.0)
        if (timestamp >= upper.timestamp) return Triple(upper, upper, 0.0)

        for (i in 0 until sortedSteps.size - 1) {
            if (timestamp in sortedSteps[i].timestamp..sortedSteps[i + 1].timestamp) {
                lower = sortedSteps[i]
                upper = sortedSteps[i + 1]
                break
            }
        }

        val stepSpan = upper.timestamp - lower.timestamp
        val ratio = if (stepSpan > 0) (timestamp - lower.timestamp).toDouble() / stepSpan.toDouble() else 0.0
        return Triple(lower, upper, ratio.coerceIn(0.0, 1.0))
    }

    private fun interpolateScalar(lat: Double, lon: Double, grid: Array<DoubleArray>?): Double? {
        if (grid == null) return null
        val header = gridData.header
        if (lat !in header.latMin..header.latMax || lon !in header.lonMin..header.lonMax) return null

        val latSpan = header.latMax - header.latMin
        val lonSpan = header.lonMax - header.lonMin
        if (latSpan <= 0.0 || lonSpan <= 0.0) return null

        val latFrac = (lat - header.latMin) / latSpan * (header.latSteps - 1)
        val lonFrac = (lon - header.lonMin) / lonSpan * (header.lonSteps - 1)

        val latIdx = latFrac.toInt().coerceIn(0, header.latSteps - 2)
        val lonIdx = lonFrac.toInt().coerceIn(0, header.lonSteps - 2)
        val latRem = latFrac - latIdx
        val lonRem = lonFrac - lonIdx

        val v00 = grid[latIdx][lonIdx]
        val v10 = grid[latIdx + 1][lonIdx]
        val v01 = grid[latIdx][lonIdx + 1]
        val v11 = grid[latIdx + 1][lonIdx + 1]

        return v00 * (1 - latRem) * (1 - lonRem) +
                v10 * latRem * (1 - lonRem) +
                v01 * (1 - latRem) * lonRem +
                v11 * latRem * lonRem
    }

    private fun interpolateWind(lat: Double, lon: Double, timeStep: TimeStepGrid): WindVector? {
        val u = interpolateScalar(lat, lon, timeStep.uGrid) ?: return null
        val v = interpolateScalar(lat, lon, timeStep.vGrid) ?: return null
        return WindVector(u, v)
    }

    private fun interpolateCurrent(lat: Double, lon: Double, timeStep: TimeStepGrid): WindVector? {
        val u = interpolateScalar(lat, lon, timeStep.currentUGrid) ?: return null
        val v = interpolateScalar(lat, lon, timeStep.currentVGrid) ?: return null
        return WindVector(u, v)
    }
}
