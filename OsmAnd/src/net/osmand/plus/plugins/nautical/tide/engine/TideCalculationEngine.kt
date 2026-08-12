package net.osmand.plus.plugins.nautical.tide.engine

import net.osmand.plus.plugins.nautical.tide.model.TidePrediction
import net.osmand.plus.plugins.nautical.tide.model.TideStation
import java.util.concurrent.TimeUnit
import kotlin.math.cos
import kotlin.math.sin

class TideCalculationEngine {

    /**
     * Calculates the predicted water height for a given station at a specific timestamp.
     * 
     * @param station The TideStation containing harmonic constituents.
     * @param timestampMs Unix timestamp in milliseconds.
     * @return Predicted height in meters.
     */
    fun calculateHeight(station: TideStation, timestampMs: Long): Double {
        val tHours = timestampMs.toDouble() / TimeUnit.HOURS.toMillis(1).toDouble()
        val n = getN(timestampMs)
        
        var height = station.datum
        
        for (constituent in station.constituents) {
            val f = getNodeFactor(constituent.name, n)
            val u = getAstronomicalArgument(constituent.name, n)
            val v0 = getV0(constituent.name, timestampMs)
            
            // Formula: H = MSL + sum(A * f * cos(speed * t + V0 + u - g))
            val angleDeg = (constituent.speed * tHours) + v0 + u - constituent.epoch
            height += constituent.amplitude * f * cos(Math.toRadians(angleDeg))
        }
        
        return height
    }

    private fun getV0(name: String, timestampMs: Long): Double {
        // Approximate V0 (phase at epoch) based on J2000
        val j2000 = 946728000000L
        val d = (timestampMs - j2000).toDouble() / (24 * 3600 * 1000.0)
        
        return when (name.uppercase()) {
            "M2" -> (28.9841042 * d * 24.0) % 360.0
            "S2" -> (30.0 * d * 24.0) % 360.0
            "N2" -> (28.4397295 * d * 24.0) % 360.0
            "K1" -> (15.0410686 * d * 24.0 + 90.0) % 360.0
            "O1" -> (13.9430356 * d * 24.0 - 90.0) % 360.0
            "P1" -> (14.9589314 * d * 24.0 - 90.0) % 360.0
            "K2" -> (30.0821373 * d * 24.0) % 360.0
            "M4" -> (57.9682084 * d * 24.0) % 360.0
            "Q1" -> (13.3986609 * d * 24.0) % 360.0
            "MU2" -> (27.9682084 * d * 24.0) % 360.0
            "NU2" -> (28.5125831 * d * 24.0) % 360.0
            "L2" -> (29.5284789 * d * 24.0) % 360.0
            "T2" -> (29.9589333 * d * 24.0) % 360.0
            else -> 0.0
        }
    }

    private fun getN(timestampMs: Long): Double {
        // Longitude of the Moon's ascending node (N)
        val j2000 = 946728000000L
        val d = (timestampMs - j2000).toDouble() / (24 * 3600 * 1000.0)
        val t = d / 36525.0
        val nDeg = 125.04452 - 1934.136261 * t + 0.0020708 * t * t
        return (nDeg + 360.0) % 360.0
    }

    private fun getNodeFactor(name: String, nDeg: Double): Double {
        val n = Math.toRadians(nDeg)
        return when (name.uppercase()) {
            "M2", "N2", "MU2", "NU2", "L2" -> 1.0004 - 0.0373 * cos(n) + 0.0002 * cos(2 * n)
            "K1" -> 1.0060 + 0.1150 * cos(n) - 0.0088 * cos(2 * n)
            "O1", "Q1" -> 1.0089 + 0.1871 * cos(n) - 0.0147 * cos(2 * n)
            "K2" -> 1.0241 + 0.2852 * cos(n) + 0.0247 * cos(2 * n)
            "P1", "S2", "T2" -> 1.0
            "M4" -> {
                val fM2 = 1.0004 - 0.0373 * cos(n) + 0.0002 * cos(2 * n)
                fM2 * fM2
            }
            else -> 1.0
        }
    }

    private fun getAstronomicalArgument(name: String, nDeg: Double): Double {
        val n = Math.toRadians(nDeg)
        return when (name.uppercase()) {
            "M2", "N2", "MU2", "NU2", "L2" -> -2.14 * sin(n)
            "K1" -> -8.85 * sin(n) + 0.68 * sin(2 * n)
            "O1", "Q1" -> 10.80 * sin(n) - 1.34 * sin(2 * n)
            "K2" -> -17.74 * sin(n) + 0.68 * sin(2 * n)
            "M4" -> -4.28 * sin(n)
            else -> 0.0
        }
    }

    /**
     * Finds high and low tides within a 24-hour window starting from [startTimeMs].
     */
    fun predictTides(station: TideStation, startTimeMs: Long): List<TidePrediction> {
        val predictions = mutableListOf<TidePrediction>()
        val stepMs = TimeUnit.MINUTES.toMillis(10) // 10-minute resolution
        val windowMs = TimeUnit.HOURS.toMillis(24)
        
        var prevHeight = calculateHeight(station, startTimeMs - stepMs)
        var currentHeight = calculateHeight(station, startTimeMs)
        
        for (t in (startTimeMs..(startTimeMs + windowMs)) step stepMs) {
            val nextHeight = calculateHeight(station, t + stepMs)
            
            // Peak/Valley Detection
            if (currentHeight > prevHeight && currentHeight > nextHeight) {
                // Local Maximum (High Tide)
                val exactTime = findExactPeak(station, t - stepMs, t + stepMs, isHigh = true)
                predictions.add(TidePrediction(
                    timestamp = exactTime,
                    heightMeters = calculateHeight(station, exactTime),
                    heightAboveDatum = calculateHeight(station, exactTime),
                    meanSeaLevel = station.datum,
                    isHighTide = true
                ))
            } else if (currentHeight < prevHeight && currentHeight < nextHeight) {
                // Local Minimum (Low Tide)
                val exactTime = findExactPeak(station, t - stepMs, t + stepMs, isHigh = false)
                predictions.add(TidePrediction(
                    timestamp = exactTime,
                    heightMeters = calculateHeight(station, exactTime),
                    heightAboveDatum = calculateHeight(station, exactTime),
                    meanSeaLevel = station.datum,
                    isHighTide = false
                ))
            }
            
            prevHeight = currentHeight
            currentHeight = nextHeight
        }
        
        return predictions
    }

    private fun findExactPeak(station: TideStation, tStart: Long, tEnd: Long, isHigh: Boolean): Long {
        var low = tStart
        var high = tEnd
        // Ternary search for local extremum
        repeat(5) {
            val m1 = low + (high - low) / 3
            val m2 = high - (high - low) / 3
            val h1 = calculateHeight(station, m1)
            val h2 = calculateHeight(station, m2)
            if (isHigh) {
                if (h1 < h2) low = m1 else high = m2
            } else {
                if (h1 > h2) low = m1 else high = m2
            }
        }
        return (low + high) / 2
    }
}
