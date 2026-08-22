package net.osmand.plus.plugins.nautical.telemetry

import android.content.Context
import androidx.annotation.StringRes
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.plugins.nautical.engine.SignalKPaths
import net.osmand.plus.plugins.nautical.engine.SignalKUnitConverter
import net.osmand.plus.settings.backend.OsmandSettings
import net.osmand.plus.utils.OsmAndFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

enum class MetricCategory(@StringRes val titleRes: Int) {
    NAVIGATION(R.string.nautical_category_navigation),
    WIND(R.string.nautical_category_wind),
    ENVIRONMENT(R.string.nautical_category_environment),
    VESSEL(R.string.nautical_category_vessel),
    POWER(R.string.nautical_category_power)
}

enum class AngleSide {
    NONE,
    PORT,
    STARBOARD
}

data class MetricValue(
    val primaryText: String,
    val secondaryText: String = "",
    val unitText: String = "",
    val rawNumericValue: Double? = null,
    val isPortStarboard: Boolean = false,
    val angleSide: AngleSide = AngleSide.NONE,
    val isValid: Boolean = true
)

data class TelemetrySample(
    val timestamp: Long,
    val value: Double
)

class TelemetryRingBuffer(val capacityHighRes: Int = 3600, val capacityDownsampled: Int = 8640) {
    private val highResSamples = ArrayDeque<TelemetrySample>(capacityHighRes)
    private val downsampledSamples = ArrayDeque<TelemetrySample>(capacityDownsampled)
    private var lastDownsampledTime = 0L

    @Synchronized
    fun addSample(timestamp: Long, value: Double) {
        if (value.isNaN() || value.isInfinite()) return

        if (highResSamples.size >= capacityHighRes) {
            highResSamples.removeFirst()
        }
        highResSamples.addLast(TelemetrySample(timestamp, value))

        if (timestamp - lastDownsampledTime >= 10000L) {
            if (downsampledSamples.size >= capacityDownsampled) {
                downsampledSamples.removeFirst()
            }
            downsampledSamples.addLast(TelemetrySample(timestamp, value))
            lastDownsampledTime = timestamp
        }
    }

    @Synchronized
    fun getSamples(durationMs: Long, now: Long = System.currentTimeMillis()): List<TelemetrySample> {
        val cutoff = now - durationMs
        return if (durationMs <= 3600_000L) {
            highResSamples.filter { it.timestamp >= cutoff }
        } else {
            downsampledSamples.filter { it.timestamp >= cutoff }
        }
    }

    @Synchronized
    fun getStats(durationMs: Long, now: Long = System.currentTimeMillis()): MetricStats {
        val samples = getSamples(durationMs, now)
        if (samples.isEmpty()) return MetricStats.EMPTY
        var min = Double.MAX_VALUE
        var max = -Double.MAX_VALUE
        var sum = 0.0
        for (s in samples) {
            if (s.value < min) min = s.value
            if (s.value > max) max = s.value
            sum += s.value
        }
        val avg = sum / samples.size

        val first = samples.first()
        val last = samples.last()
        val dtHours = (last.timestamp - first.timestamp) / 3600000.0
        val ratePerHour = if (dtHours > 0.05) (last.value - first.value) / dtHours else 0.0

        return MetricStats(min, max, avg, last.value, ratePerHour, samples.size)
    }

    @Synchronized
    fun clear() {
        highResSamples.clear()
        downsampledSamples.clear()
        lastDownsampledTime = 0L
    }
}

data class MetricStats(
    val min: Double,
    val max: Double,
    val avg: Double,
    val current: Double,
    val ratePerHour: Double,
    val sampleCount: Int
) {
    companion object {
        val EMPTY = MetricStats(0.0, 0.0, 0.0, 0.0, 0.0, 0)
    }
}

data class TelemetryMetricDefinition(
    val key: String,
    @StringRes val titleRes: Int,
    val category: MetricCategory,
    val spanSize: Int = 1,
    val isPortStarboardAngle: Boolean = false,
    val isDepth: Boolean = false,
    val isPressure: Boolean = false,
    val extractor: (MarineState) -> Double?,
    val secondaryExtractor: ((MarineState) -> Double?)? = null,
    val formatter: (context: Context, settings: OsmandSettings, value: Double?, secondaryValue: Double?) -> MetricValue
) {
    val ringBuffer = TelemetryRingBuffer()
}

object TelemetryRegistry {

    private val metricsMap = ConcurrentHashMap<String, TelemetryMetricDefinition>()
    private val metricsList = mutableListOf<TelemetryMetricDefinition>()

    const val PRESET_SAILING = "preset_sailing"
    const val PRESET_PILOTAGE = "preset_pilotage"
    const val PRESET_ANCHORAGE = "preset_anchorage"
    const val PRESET_PASSAGE = "preset_passage"

    init {
        registerAllMetrics()
    }

    private fun register(metric: TelemetryMetricDefinition) {
        metricsMap[metric.key] = metric
        metricsList.add(metric)
    }

    fun getMetric(key: String): TelemetryMetricDefinition? {
        val direct = metricsMap[key]
        if (direct != null) return direct
        val mappedPath = when (key) {
            "nautical_sog" -> SignalKPaths.NAV_SPEED_OVER_GROUND
            "nautical_stw" -> SignalKPaths.NAV_SPEED_THROUGH_WATER
            "nautical_cog" -> SignalKPaths.NAV_COURSE_OVER_GROUND
            "nautical_heading_magnetic", "nautical_hdg_mag" -> SignalKPaths.NAV_HEADING_MAG
            "nautical_heading_true", "nautical_hdg_true" -> SignalKPaths.NAV_HEADING_TRUE
            "nautical_vmg" -> SignalKPaths.PERF_VMG
            "nautical_rot" -> SignalKPaths.NAV_RATE_OF_TURN
            "nautical_log" -> SignalKPaths.NAV_LOG
            "nautical_trip_log" -> SignalKPaths.NAV_TRIP_LOG
            "nautical_xte" -> SignalKPaths.NAV_XTE
            "nautical_dtw" -> SignalKPaths.NAV_DTW
            "nautical_ttw" -> SignalKPaths.NAV_TTW
            "nautical_roll" -> "navigation.attitude.roll"
            "nautical_pitch" -> "navigation.attitude.pitch"
            "nautical_awa" -> SignalKPaths.ENV_WIND_ANGLE_APPARENT
            "nautical_aws" -> SignalKPaths.ENV_WIND_SPEED_APPARENT
            "nautical_twa" -> SignalKPaths.ENV_WIND_ANGLE_TRUE
            "nautical_tws", "nautical_wind" -> SignalKPaths.ENV_WIND_SPEED_TRUE
            "nautical_twd" -> SignalKPaths.ENV_WIND_DIRECTION_TRUE
            "nautical_depth" -> SignalKPaths.ENV_DEPTH_BELOW_TRANSDUCER
            "nautical_depth_keel" -> SignalKPaths.ENV_DEPTH_BELOW_KEEL
            "nautical_water_temp" -> SignalKPaths.ENV_WATER_TEMP
            "nautical_outside_temp" -> SignalKPaths.ENV_OUTSIDE_TEMP
            "nautical_pressure" -> SignalKPaths.ENV_OUTSIDE_PRESSURE
            "nautical_humidity" -> SignalKPaths.ENV_OUTSIDE_HUMIDITY
            "nautical_dew_point" -> SignalKPaths.ENV_AIR_DEW_POINT
            "nautical_rudder_angle_text", "nautical_rudder_angle" -> SignalKPaths.STEERING_RUDDER_ANGLE
            "nautical_engine_rpm" -> SignalKPaths.PROPULSION_PREFIX + "0.revolutions"
            "nautical_engine_temp" -> SignalKPaths.PROPULSION_PREFIX + "0.temperature"
            "nautical_oil_pressure" -> SignalKPaths.PROPULSION_PREFIX + "0.oilPressure"
            "nautical_engine_runtime" -> SignalKPaths.PROPULSION_PREFIX + "0.runTime"
            "nautical_fuel_level" -> "tanks.fuel.0.currentLevel"
            "nautical_fresh_water_level" -> "tanks.freshWater.0.currentLevel"
            "nautical_waste_water_level" -> "tanks.wasteWater.0.currentLevel"
            "nautical_battery_volt" -> "electrical.batteries.0.voltage"
            "nautical_battery_current" -> "electrical.batteries.0.current"
            "nautical_battery_soc" -> "electrical.batteries.0.stateOfCharge"
            "nautical_polar_ratio" -> SignalKPaths.PERF_POLAR_RATIO
            else -> null
        }
        return if (mappedPath != null) metricsMap[mappedPath] else null
    }

    fun getAllMetrics(): List<TelemetryMetricDefinition> = metricsList

    fun getMetricsByCategory(category: MetricCategory): List<TelemetryMetricDefinition> =
        metricsList.filter { it.category == category }

    fun getPresetKeys(presetId: String): List<String> {
        return when (presetId) {
            PRESET_SAILING -> listOf(
                "composite.wind.apparent",
                SignalKPaths.NAV_SPEED_THROUGH_WATER,
                SignalKPaths.NAV_SPEED_OVER_GROUND,
                SignalKPaths.PERF_VMG,
                SignalKPaths.ENV_DEPTH_BELOW_KEEL,
                SignalKPaths.NAV_HEADING_MAG,
                "navigation.attitude.roll"
            )
            PRESET_PILOTAGE -> listOf(
                SignalKPaths.NAV_SPEED_OVER_GROUND,
                SignalKPaths.NAV_COURSE_OVER_GROUND,
                SignalKPaths.ENV_DEPTH_BELOW_KEEL,
                SignalKPaths.NAV_HEADING_MAG,
                SignalKPaths.NAV_DTW,
                SignalKPaths.NAV_XTE,
                SignalKPaths.NAV_RATE_OF_TURN,
                SignalKPaths.STEERING_RUDDER_ANGLE
            )
            PRESET_ANCHORAGE -> listOf(
                SignalKPaths.ENV_DEPTH_BELOW_KEEL,
                SignalKPaths.ENV_WIND_SPEED_APPARENT,
                SignalKPaths.ENV_OUTSIDE_PRESSURE,
                SignalKPaths.ENV_OUTSIDE_TEMP,
                "electrical.batteries.0.voltage",
                "electrical.batteries.0.stateOfCharge",
                "tanks.freshWater.0.currentLevel",
                "tanks.fuel.0.currentLevel"
            )
            PRESET_PASSAGE -> listOf(
                SignalKPaths.NAV_SPEED_OVER_GROUND,
                SignalKPaths.NAV_COURSE_OVER_GROUND,
                "composite.wind.true",
                SignalKPaths.ENV_OUTSIDE_PRESSURE,
                SignalKPaths.NAV_LOG,
                SignalKPaths.NAV_TRIP_LOG,
                "electrical.batteries.0.voltage",
                SignalKPaths.PROPULSION_PREFIX + "0.runTime"
            )
            else -> listOf(
                SignalKPaths.NAV_SPEED_OVER_GROUND,
                SignalKPaths.NAV_COURSE_OVER_GROUND,
                SignalKPaths.ENV_DEPTH_BELOW_KEEL,
                "composite.wind.apparent",
                SignalKPaths.PERF_VMG,
                "electrical.batteries.0.voltage"
            )
        }
    }

    private fun registerAllMetrics() {
        // NAVIGATION
        register(
            TelemetryMetricDefinition(
                key = SignalKPaths.NAV_SPEED_OVER_GROUND,
                titleRes = R.string.nautical_sog,
                category = MetricCategory.NAVIGATION,
                extractor = { it.speedOverGround },
                formatter = { context, settings, value, _ -> formatSpeed(context, settings, value) }
            )
        )
        register(
            TelemetryMetricDefinition(
                key = SignalKPaths.NAV_SPEED_THROUGH_WATER,
                titleRes = R.string.nautical_stw,
                category = MetricCategory.NAVIGATION,
                extractor = { it.speedThroughWater },
                formatter = { context, settings, value, _ -> formatSpeed(context, settings, value) }
            )
        )
        register(
            TelemetryMetricDefinition(
                key = SignalKPaths.NAV_COURSE_OVER_GROUND,
                titleRes = R.string.nautical_cog,
                category = MetricCategory.NAVIGATION,
                extractor = { it.courseOverGroundTrue },
                formatter = { _, _, value, _ -> formatAngle(value) }
            )
        )
        register(
            TelemetryMetricDefinition(
                key = SignalKPaths.NAV_HEADING_MAG,
                titleRes = R.string.nautical_heading_magnetic,
                category = MetricCategory.NAVIGATION,
                extractor = { it.headingMagnetic },
                formatter = { _, _, value, _ -> formatAngle(value) }
            )
        )
        register(
            TelemetryMetricDefinition(
                key = SignalKPaths.NAV_HEADING_TRUE,
                titleRes = R.string.nautical_heading_true,
                category = MetricCategory.NAVIGATION,
                extractor = { it.headingTrue },
                formatter = { _, _, value, _ -> formatAngle(value) }
            )
        )
        register(
            TelemetryMetricDefinition(
                key = SignalKPaths.NAV_RATE_OF_TURN,
                titleRes = R.string.nautical_rot,
                category = MetricCategory.NAVIGATION,
                extractor = { it.rateOfTurn },
                formatter = { _, _, value, _ ->
                    if (value == null || value.isNaN()) return@TelemetryMetricDefinition MetricValue("---", isValid = false)
                    val degPerMin = Math.toDegrees(value) * 60.0
                    MetricValue(
                        primaryText = String.format(Locale.US, "%.1f", degPerMin),
                        unitText = "°/m",
                        rawNumericValue = degPerMin
                    )
                }
            )
        )
        register(
            TelemetryMetricDefinition(
                key = SignalKPaths.PERF_VMG,
                titleRes = R.string.nautical_vmg,
                category = MetricCategory.NAVIGATION,
                extractor = { it.velocityMadeGood },
                formatter = { context, settings, value, _ -> formatSpeed(context, settings, value) }
            )
        )
        register(
            TelemetryMetricDefinition(
                key = SignalKPaths.NAV_LOG,
                titleRes = R.string.nautical_log,
                category = MetricCategory.NAVIGATION,
                extractor = { it.log },
                formatter = { context, _, value, _ -> formatDistance(context, value) }
            )
        )
        register(
            TelemetryMetricDefinition(
                key = SignalKPaths.NAV_TRIP_LOG,
                titleRes = R.string.nautical_trip_log,
                category = MetricCategory.NAVIGATION,
                extractor = { it.tripLog },
                formatter = { context, _, value, _ -> formatDistance(context, value) }
            )
        )
        register(
            TelemetryMetricDefinition(
                key = SignalKPaths.NAV_XTE,
                titleRes = R.string.nautical_xte,
                category = MetricCategory.NAVIGATION,
                extractor = { it.crossTrackError },
                formatter = { context, _, value, _ -> formatDistance(context, value) }
            )
        )
        register(
            TelemetryMetricDefinition(
                key = SignalKPaths.NAV_DTW,
                titleRes = R.string.nautical_dtw,
                category = MetricCategory.NAVIGATION,
                extractor = { it.distanceToWaypoint },
                formatter = { context, _, value, _ -> formatDistance(context, value) }
            )
        )
        register(
            TelemetryMetricDefinition(
                key = "navigation.attitude.roll",
                titleRes = R.string.nautical_roll,
                category = MetricCategory.NAVIGATION,
                isPortStarboardAngle = true,
                extractor = { it.roll },
                formatter = { _, _, value, _ -> formatPortStarboardAngle(value) }
            )
        )
        register(
            TelemetryMetricDefinition(
                key = "navigation.attitude.pitch",
                titleRes = R.string.nautical_pitch,
                category = MetricCategory.NAVIGATION,
                extractor = { it.pitch },
                formatter = { _, _, value, _ -> formatAngle(value) }
            )
        )

        // WIND
        register(
            TelemetryMetricDefinition(
                key = SignalKPaths.ENV_WIND_ANGLE_APPARENT,
                titleRes = R.string.nautical_awa,
                category = MetricCategory.WIND,
                isPortStarboardAngle = true,
                extractor = { it.windDirectionApparent },
                formatter = { _, _, value, _ -> formatPortStarboardAngle(value) }
            )
        )
        register(
            TelemetryMetricDefinition(
                key = SignalKPaths.ENV_WIND_SPEED_APPARENT,
                titleRes = R.string.nautical_aws,
                category = MetricCategory.WIND,
                extractor = { it.windSpeedApparent },
                formatter = { context, settings, value, _ -> formatSpeed(context, settings, value) }
            )
        )
        register(
            TelemetryMetricDefinition(
                key = SignalKPaths.ENV_WIND_ANGLE_TRUE,
                titleRes = R.string.nautical_twa,
                category = MetricCategory.WIND,
                isPortStarboardAngle = true,
                extractor = { it.trueWindAngle },
                formatter = { _, _, value, _ -> formatPortStarboardAngle(value) }
            )
        )
        register(
            TelemetryMetricDefinition(
                key = SignalKPaths.ENV_WIND_SPEED_TRUE,
                titleRes = R.string.nautical_tws,
                category = MetricCategory.WIND,
                extractor = { it.windSpeedTrue },
                formatter = { context, settings, value, _ -> formatSpeed(context, settings, value) }
            )
        )
        register(
            TelemetryMetricDefinition(
                key = SignalKPaths.ENV_WIND_DIRECTION_TRUE,
                titleRes = R.string.nautical_twd,
                category = MetricCategory.WIND,
                extractor = { it.windDirectionTrue },
                formatter = { _, _, value, _ -> formatAngle(value) }
            )
        )
        register(
            TelemetryMetricDefinition(
                key = "composite.wind.apparent",
                titleRes = R.string.nautical_metric_dual_wind_app,
                category = MetricCategory.WIND,
                spanSize = 2,
                isPortStarboardAngle = true,
                extractor = { it.windDirectionApparent },
                secondaryExtractor = { it.windSpeedApparent },
                formatter = { context, settings, angleVal, speedVal ->
                    val angleMetric = formatPortStarboardAngle(angleVal)
                    val speedMetric = formatSpeed(context, settings, speedVal)
                    MetricValue(
                        primaryText = angleMetric.primaryText,
                        secondaryText = "${speedMetric.primaryText} ${speedMetric.unitText}",
                        unitText = angleMetric.unitText,
                        rawNumericValue = angleVal,
                        isPortStarboard = true,
                        angleSide = angleMetric.angleSide,
                        isValid = angleMetric.isValid && speedMetric.isValid
                    )
                }
            )
        )
        register(
            TelemetryMetricDefinition(
                key = "composite.wind.true",
                titleRes = R.string.nautical_metric_dual_wind_true,
                category = MetricCategory.WIND,
                spanSize = 2,
                isPortStarboardAngle = true,
                extractor = { it.trueWindAngle },
                secondaryExtractor = { it.windSpeedTrue },
                formatter = { context, settings, angleVal, speedVal ->
                    val angleMetric = formatPortStarboardAngle(angleVal)
                    val speedMetric = formatSpeed(context, settings, speedVal)
                    MetricValue(
                        primaryText = angleMetric.primaryText,
                        secondaryText = "${speedMetric.primaryText} ${speedMetric.unitText}",
                        unitText = angleMetric.unitText,
                        rawNumericValue = angleVal,
                        isPortStarboard = true,
                        angleSide = angleMetric.angleSide,
                        isValid = angleMetric.isValid && speedMetric.isValid
                    )
                }
            )
        )

        // ENVIRONMENT
        register(
            TelemetryMetricDefinition(
                key = SignalKPaths.ENV_DEPTH_BELOW_TRANSDUCER,
                titleRes = R.string.nautical_depth,
                category = MetricCategory.ENVIRONMENT,
                isDepth = true,
                extractor = { it.depthBelowTransducer },
                formatter = { context, settings, value, _ -> formatDepth(context, settings, value) }
            )
        )
        register(
            TelemetryMetricDefinition(
                key = SignalKPaths.ENV_DEPTH_BELOW_KEEL,
                titleRes = R.string.nautical_depth_keel,
                category = MetricCategory.ENVIRONMENT,
                isDepth = true,
                extractor = { it.depthBelowKeel },
                formatter = { context, settings, value, _ -> formatDepth(context, settings, value) }
            )
        )
        register(
            TelemetryMetricDefinition(
                key = SignalKPaths.ENV_DEPTH_SURFACE_TO_TRANSDUCER,
                titleRes = R.string.shared_string_nautical_depth,
                category = MetricCategory.ENVIRONMENT,
                isDepth = true,
                extractor = { it.depthSurfaceToTransducer },
                formatter = { context, settings, value, _ -> formatDepth(context, settings, value) }
            )
        )
        register(
            TelemetryMetricDefinition(
                key = SignalKPaths.ENV_WATER_TEMP,
                titleRes = R.string.nautical_water_temp,
                category = MetricCategory.ENVIRONMENT,
                extractor = { it.waterTemperature },
                formatter = { context, _, value, _ -> formatTemp(context, value) }
            )
        )
        register(
            TelemetryMetricDefinition(
                key = SignalKPaths.ENV_OUTSIDE_TEMP,
                titleRes = R.string.nautical_outside_temp,
                category = MetricCategory.ENVIRONMENT,
                extractor = { it.outsideTemperature },
                formatter = { context, _, value, _ -> formatTemp(context, value) }
            )
        )
        register(
            TelemetryMetricDefinition(
                key = SignalKPaths.ENV_OUTSIDE_PRESSURE,
                titleRes = R.string.nautical_pressure,
                category = MetricCategory.ENVIRONMENT,
                isPressure = true,
                extractor = { it.outsidePressure },
                formatter = { context, _, value, _ ->
                    if (value == null || value.isNaN()) return@TelemetryMetricDefinition MetricValue("---", isValid = false)
                    val hpa = SignalKUnitConverter.pascalToHpa(value)
                    MetricValue(
                        primaryText = String.format(Locale.US, "%.0f", hpa),
                        unitText = context.getString(R.string.nautical_unit_hpa),
                        rawNumericValue = hpa
                    )
                }
            )
        )
        register(
            TelemetryMetricDefinition(
                key = SignalKPaths.ENV_OUTSIDE_HUMIDITY,
                titleRes = R.string.nautical_humidity,
                category = MetricCategory.ENVIRONMENT,
                extractor = { it.outsideHumidity },
                formatter = { context, _, value, _ -> formatPercent(context, value) }
            )
        )
        register(
            TelemetryMetricDefinition(
                key = SignalKPaths.ENV_AIR_DEW_POINT,
                titleRes = R.string.nautical_dew_point,
                category = MetricCategory.ENVIRONMENT,
                extractor = { it.airDewPoint },
                formatter = { context, _, value, _ -> formatTemp(context, value) }
            )
        )

        // VESSEL
        register(
            TelemetryMetricDefinition(
                key = SignalKPaths.STEERING_RUDDER_ANGLE,
                titleRes = R.string.nautical_rudder_angle,
                category = MetricCategory.VESSEL,
                isPortStarboardAngle = true,
                extractor = { it.rudderAngle ?: it.simulatedRudderAngle },
                formatter = { _, _, value, _ -> formatPortStarboardAngle(value) }
            )
        )
        register(
            TelemetryMetricDefinition(
                key = SignalKPaths.PROPULSION_PREFIX + "0.revolutions",
                titleRes = R.string.nautical_engine_rpm,
                category = MetricCategory.VESSEL,
                extractor = { it.engines["0"]?.revolutions ?: it.engineRpm },
                formatter = { context, _, value, _ ->
                    if (value == null || value.isNaN()) return@TelemetryMetricDefinition MetricValue("---", isValid = false)
                    val rpm = SignalKUnitConverter.hertzToRpm(value)
                    MetricValue(
                        primaryText = String.format(Locale.US, "%.0f", rpm),
                        unitText = context.getString(R.string.nautical_unit_rpm),
                        rawNumericValue = rpm
                    )
                }
            )
        )
        register(
            TelemetryMetricDefinition(
                key = SignalKPaths.PROPULSION_PREFIX + "0.temperature",
                titleRes = R.string.nautical_engine_temp,
                category = MetricCategory.VESSEL,
                extractor = { it.engines["0"]?.coolantTemperature ?: it.engineCoolantTemperature },
                formatter = { context, _, value, _ -> formatTemp(context, value) }
            )
        )
        register(
            TelemetryMetricDefinition(
                key = SignalKPaths.PROPULSION_PREFIX + "0.oilPressure",
                titleRes = R.string.nautical_oil_pressure,
                category = MetricCategory.VESSEL,
                extractor = { it.engines["0"]?.oilPressure ?: it.engineOilPressure },
                formatter = { context, _, value, _ ->
                    if (value == null || value.isNaN()) return@TelemetryMetricDefinition MetricValue("---", isValid = false)
                    val bar = SignalKUnitConverter.pascalToBar(value)
                    MetricValue(
                        primaryText = String.format(Locale.US, "%.1f", bar),
                        unitText = context.getString(R.string.nautical_unit_bar),
                        rawNumericValue = bar
                    )
                }
            )
        )
        register(
            TelemetryMetricDefinition(
                key = SignalKPaths.PROPULSION_PREFIX + "0.runTime",
                titleRes = R.string.nautical_engine_runtime,
                category = MetricCategory.VESSEL,
                extractor = { it.engines["0"]?.runTime ?: it.engineRunTime },
                formatter = { context, _, value, _ ->
                    if (value == null || value.isNaN()) return@TelemetryMetricDefinition MetricValue("---", isValid = false)
                    val hours = value / 3600.0
                    MetricValue(
                        primaryText = String.format(Locale.US, "%.1f", hours),
                        unitText = context.getString(R.string.nautical_unit_hours),
                        rawNumericValue = hours
                    )
                }
            )
        )
        register(
            TelemetryMetricDefinition(
                key = "tanks.fuel.0.currentLevel",
                titleRes = R.string.nautical_fuel_level,
                category = MetricCategory.VESSEL,
                extractor = { it.tanks["fuel.0"]?.currentLevel ?: it.tanks["fuel"]?.currentLevel },
                formatter = { context, _, value, _ -> formatPercent(context, value) }
            )
        )
        register(
            TelemetryMetricDefinition(
                key = "tanks.freshWater.0.currentLevel",
                titleRes = R.string.nautical_fresh_water_level,
                category = MetricCategory.VESSEL,
                extractor = { it.tanks["freshWater.0"]?.currentLevel ?: it.tanks["freshWater"]?.currentLevel },
                formatter = { context, _, value, _ -> formatPercent(context, value) }
            )
        )
        register(
            TelemetryMetricDefinition(
                key = "tanks.wasteWater.0.currentLevel",
                titleRes = R.string.nautical_waste_water_level,
                category = MetricCategory.VESSEL,
                extractor = { it.tanks["wasteWater.0"]?.currentLevel ?: it.tanks["wasteWater"]?.currentLevel },
                formatter = { context, _, value, _ -> formatPercent(context, value) }
            )
        )

        // POWER
        register(
            TelemetryMetricDefinition(
                key = "electrical.batteries.0.voltage",
                titleRes = R.string.nautical_battery_volt,
                category = MetricCategory.POWER,
                extractor = { it.batteries["0"]?.voltage ?: it.batteries["house"]?.voltage },
                formatter = { context, _, value, _ ->
                    if (value == null || value.isNaN()) return@TelemetryMetricDefinition MetricValue("---", isValid = false)
                    MetricValue(
                        primaryText = String.format(Locale.US, "%.2f", value),
                        unitText = context.getString(R.string.nautical_unit_volt),
                        rawNumericValue = value
                    )
                }
            )
        )
        register(
            TelemetryMetricDefinition(
                key = "electrical.batteries.0.current",
                titleRes = R.string.nautical_battery_current,
                category = MetricCategory.POWER,
                extractor = { it.batteries["0"]?.current ?: it.batteries["house"]?.current },
                formatter = { context, _, value, _ ->
                    if (value == null || value.isNaN()) return@TelemetryMetricDefinition MetricValue("---", isValid = false)
                    MetricValue(
                        primaryText = String.format(Locale.US, "%+.1f", value),
                        unitText = context.getString(R.string.nautical_unit_ampere),
                        rawNumericValue = value
                    )
                }
            )
        )
        register(
            TelemetryMetricDefinition(
                key = "electrical.batteries.0.stateOfCharge",
                titleRes = R.string.nautical_battery_soc,
                category = MetricCategory.POWER,
                extractor = { it.batteries["0"]?.stateOfCharge ?: it.batteries["house"]?.stateOfCharge },
                formatter = { context, _, value, _ -> formatPercent(context, value) }
            )
        )
    }

    private fun formatSpeed(context: Context, settings: OsmandSettings, value: Double?): MetricValue {
        if (value == null || value.isNaN()) return MetricValue("---", isValid = false)
        val app = context.applicationContext as? OsmandApplication
        return if (app != null) {
            val fv = OsmAndFormatter.getFormattedSpeedValue(value.toFloat(), app)
            MetricValue(primaryText = fv.value, unitText = fv.unit, rawNumericValue = value)
        } else {
            val kn = SignalKUnitConverter.msToKnots(value)
            MetricValue(primaryText = String.format(Locale.US, "%.1f", kn), unitText = "kn", rawNumericValue = kn)
        }
    }

    private fun formatDepth(context: Context, settings: OsmandSettings, value: Double?): MetricValue {
        if (value == null || value.isNaN()) return MetricValue("---", isValid = false)
        val app = context.applicationContext as? OsmandApplication
        return if (app != null) {
            val fv = OsmAndFormatter.getFormattedAltitudeValue(value, app, settings.ALTITUDE_METRIC.get())
            MetricValue(primaryText = fv.value, unitText = fv.unit, rawNumericValue = value)
        } else {
            MetricValue(primaryText = String.format(Locale.US, "%.1f", value), unitText = "m", rawNumericValue = value)
        }
    }

    private fun formatDistance(context: Context, value: Double?): MetricValue {
        if (value == null || value.isNaN()) return MetricValue("---", isValid = false)
        val app = context.applicationContext as? OsmandApplication
        return if (app != null) {
            val fv = OsmAndFormatter.getFormattedDistanceValue(value.toFloat(), app)
            MetricValue(primaryText = fv.value, unitText = fv.unit, rawNumericValue = value)
        } else {
            val nm = value / 1852.0
            MetricValue(primaryText = String.format(Locale.US, "%.2f", nm), unitText = "nm", rawNumericValue = nm)
        }
    }

    private fun formatAngle(value: Double?): MetricValue {
        if (value == null || value.isNaN()) return MetricValue("---", isValid = false)
        val deg = (Math.toDegrees(value) % 360.0 + 360.0) % 360.0
        return MetricValue(primaryText = String.format(Locale.US, "%03.0f", deg), unitText = "°", rawNumericValue = deg)
    }

    private fun formatPortStarboardAngle(value: Double?): MetricValue {
        if (value == null || value.isNaN()) return MetricValue("---", isValid = false)
        var deg = Math.toDegrees(value)
        while (deg < -180.0) deg += 360.0
        while (deg > 180.0) deg -= 360.0
        val side = when {
            deg < -0.5 -> AngleSide.PORT
            deg > 0.5 -> AngleSide.STARBOARD
            else -> AngleSide.NONE
        }
        val prefix = when (side) {
            AngleSide.PORT -> "P "
            AngleSide.STARBOARD -> "S "
            AngleSide.NONE -> ""
        }
        return MetricValue(
            primaryText = "$prefix${String.format(Locale.US, "%.0f", abs(deg))}",
            unitText = "°",
            rawNumericValue = deg,
            isPortStarboard = true,
            angleSide = side
        )
    }

    private fun formatTemp(context: Context, value: Double?): MetricValue {
        if (value == null || value.isNaN()) return MetricValue("---", isValid = false)
        val celsius = SignalKUnitConverter.kelvinToCelsius(value)
        return MetricValue(
            primaryText = String.format(Locale.US, "%.1f", celsius),
            unitText = context.getString(R.string.nautical_unit_celsius),
            rawNumericValue = celsius
        )
    }

    private fun formatPercent(context: Context, value: Double?): MetricValue {
        if (value == null || value.isNaN()) return MetricValue("---", isValid = false)
        val pct = value * 100.0
        return MetricValue(
            primaryText = String.format(Locale.US, "%.0f", pct),
            unitText = context.getString(R.string.nautical_unit_percent),
            rawNumericValue = pct
        )
    }
}
