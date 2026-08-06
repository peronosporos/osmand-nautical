package net.osmand.plus.plugins.nautical.engine

import net.osmand.plus.OsmandApplication
import net.osmand.plus.settings.backend.OsmandSettings

/**
 * Centralized source of truth for vessel safety thresholds and SI unit conversions.
 * Force all modules to query this manager to ensure consistent safety evaluation.
 */
class NauticalSafetyManager private constructor(private val app: OsmandApplication) {
    private val settings: OsmandSettings = app.settings
    private val signalKRegions = mutableMapOf<String, net.osmand.plus.plugins.nautical.network.SignalKRegion>()
    private val forwardHazards = mutableListOf<ForwardHazard>()

    fun updateSignalKRegions(regions: Map<String, net.osmand.plus.plugins.nautical.network.SignalKRegion>) {
        signalKRegions.clear()
        signalKRegions.putAll(regions)
    }

    fun getSignalKRegions(): List<net.osmand.plus.plugins.nautical.network.SignalKRegion> = signalKRegions.values.toList()

    fun updateForwardHazards(hazards: List<ForwardHazard>) {
        forwardHazards.clear()
        forwardHazards.addAll(hazards)
    }

    fun getForwardHazards(): List<ForwardHazard> = forwardHazards.toList()

    companion object {
        @Volatile
        private var instance: NauticalSafetyManager? = null

        fun getInstance(app: OsmandApplication): NauticalSafetyManager {
            return instance ?: synchronized(this) {
                instance ?: NauticalSafetyManager(app).also { instance = it }
            }
        }
    }

    /**
     * Returns vessel static draft in SI meters.
     */
    fun getVesselDraft(): Double = settings.NAUTICAL_VESSEL_DRAFT.get().toDouble()

    /**
     * Returns keel offset in SI meters. Positive for depth below keel, negative for depth below waterline.
     */
    fun getKeelOffset(): Double = settings.NAUTICAL_KEEL_OFFSET.get().toDouble()

    /**
     * Returns the safety margin (buffer below keel) in SI meters.
     */
    fun getSafetyMargin(): Double = settings.NAUTICAL_SAFETY_MARGIN.get().toDouble()

    /**
     * Returns the minimum safe depth (Draft + Margin) in SI meters.
     */
    fun getMinSafeDepth(): Double = getVesselDraft() + getSafetyMargin()

    /**
     * Returns safety corridor width in Nautical Miles (internal Signal K standard).
     */
    fun getSafetyCorridorWidthNm(): Double = settings.NAUTICAL_CORRIDOR_WIDTH.get().toDouble()

    /**
     * Returns safety corridor buffer in Nautical Miles.
     */
    fun getSafetyCorridorBufferNm(): Double = settings.NAUTICAL_SAFETY_CORRIDOR_BUFFER.get().toDouble()

    /**
     * Returns look-ahead radius in Nautical Miles.
     */
    fun getLookAheadRadiusNm(): Double = settings.NAUTICAL_LOOK_AHEAD_RADIUS_NM.get().toDouble()

    /**
     * Returns the current depth unit label based on user preferences.
     */
    fun getDepthUnitLabel(): String {
        return if (settings.ALTITUDE_METRIC.get() == net.osmand.shared.settings.enums.AltitudeMetrics.FEET) {
            app.getString(net.osmand.plus.R.string.foot)
        } else {
            app.getString(net.osmand.plus.R.string.nautical_unit_meters)
        }
    }

    /**
     * Returns multiplier to convert SI meters to user preferred depth units.
     */
    fun getDepthSItoUserMultiplier(): Double {
        return if (settings.ALTITUDE_METRIC.get() == net.osmand.shared.settings.enums.AltitudeMetrics.FEET) {
            SignalKUnitConverter.METERS_TO_FEET
        } else {
            1.0
        }
    }

    /**
     * Returns multiplier to convert user preferred depth units to SI meters.
     */
    fun getDepthUserToSIMultiplier(): Double {
        return if (settings.ALTITUDE_METRIC.get() == net.osmand.shared.settings.enums.AltitudeMetrics.FEET) {
            1.0 / SignalKUnitConverter.METERS_TO_FEET
        } else {
            1.0
        }
    }

    /**
     * Returns the total width of the safety corridor in SI meters.
     */
    fun getTotalCorridorWidthMeters(): Double {
        return (getSafetyCorridorWidthNm() + (getSafetyCorridorBufferNm() * 2.0)) * 1852.0
    }
}
