package net.osmand.plus.plugins.nautical.tide.engine

import net.osmand.plus.plugins.nautical.tide.model.TidePrediction
import net.osmand.plus.plugins.nautical.tide.model.TideStation
import java.util.concurrent.TimeUnit
import kotlin.math.cos

class TideCalculationEngine {

    /**
     * Calculates the predicted water height for a given station at a specific timestamp.
     * 
     * @param station The TideStation containing harmonic constituents.
     * @param timestampMs Unix timestamp in milliseconds.
     * @return Predicted height in meters.
     */
    fun calculateHeight(station: TideStation, timestampMs: Long): Double {
        // Time in hours since Unix Epoch (1970-01-01 00:00:00 UTC)
        val tHours = timestampMs.toDouble() / TimeUnit.HOURS.toMillis(1).toDouble()
        
        var height = 0.0 // Assuming datum is 0.0 if not specified in station
        
        for (constituent in station.constituents) {
            // Formula: A * cos(speed * t - phase)
            // Note: speeds are in degrees per hour, phase (epoch) in degrees.
            // We need to convert degrees to radians for cos().
            val angleDeg = (constituent.speed * tHours) - constituent.epoch
            height += constituent.amplitude * cos(Math.toRadians(angleDeg))
        }
        
        return height
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
                predictions.add(TidePrediction(t, currentHeight, isHighTide = true))
            } else if (currentHeight < prevHeight && currentHeight < nextHeight) {
                // Local Minimum (Low Tide)
                predictions.add(TidePrediction(t, currentHeight, isHighTide = false))
            }
            
            prevHeight = currentHeight
            currentHeight = nextHeight
        }
        
        return predictions
    }
}
