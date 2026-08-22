package net.osmand.plus.plugins.nautical.ui

import android.content.Context
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.plugins.nautical.engine.SignalKUnitConverter
import net.osmand.plus.settings.backend.OsmandSettings
import net.osmand.plus.views.mapwidgets.WidgetType

object NauticalWidgetHelper {

    fun getDefaultUnit(context: Context, settings: OsmandSettings, widget: WidgetType): String {
        val app = context.applicationContext as net.osmand.plus.OsmandApplication
        return when (widget) {
            WidgetType.NAUTICAL_SOG, WidgetType.NAUTICAL_STW, WidgetType.NAUTICAL_WIND,
            WidgetType.NAUTICAL_AWS, WidgetType.NAUTICAL_VMG, WidgetType.NAUTICAL_POLAR_TARGET_SPEED -> {
                net.osmand.plus.utils.OsmAndFormatter.getFormattedSpeedValue(0f, app).unit
            }
            WidgetType.NAUTICAL_DEPTH, WidgetType.NAUTICAL_DEPTH_KEEL -> {
                net.osmand.plus.utils.OsmAndFormatter.getFormattedAltitudeValue(0.0, app, settings.ALTITUDE_METRIC.get()).unit
            }
            WidgetType.NAUTICAL_COG, WidgetType.NAUTICAL_HEADING_MAGNETIC, WidgetType.NAUTICAL_TWD,
            WidgetType.NAUTICAL_AWA, WidgetType.NAUTICAL_TWA, WidgetType.NAUTICAL_MAG_VARIATION,
            WidgetType.NAUTICAL_ROLL, WidgetType.NAUTICAL_PITCH, WidgetType.NAUTICAL_YAW -> context.getString(R.string.nautical_unit_deg)
            WidgetType.NAUTICAL_LOG, WidgetType.NAUTICAL_TRIP_LOG, WidgetType.NAUTICAL_DTW,
            WidgetType.NAUTICAL_XTE, WidgetType.NAUTICAL_CPA, WidgetType.NAUTICAL_RANGE -> {
                net.osmand.plus.utils.OsmAndFormatter.getFormattedDistanceValue(0f, app).unit
            }
            WidgetType.NAUTICAL_BATTERY_VOLT, WidgetType.NAUTICAL_AC_VOLTAGE, WidgetType.NAUTICAL_ALTERNATOR_VOLT -> context.getString(R.string.nautical_unit_volt)
            WidgetType.NAUTICAL_BATTERY_CURRENT, WidgetType.NAUTICAL_AC_CURRENT, WidgetType.NAUTICAL_SOLAR_CURRENT, WidgetType.NAUTICAL_ALTERNATOR_CURR -> context.getString(R.string.nautical_unit_ampere)
            WidgetType.NAUTICAL_BATTERY_SOC, WidgetType.NAUTICAL_FUEL_LEVEL, WidgetType.NAUTICAL_FRESH_WATER_LEVEL,
            WidgetType.NAUTICAL_WASTE_WATER_LEVEL, WidgetType.NAUTICAL_POLAR_RATIO, WidgetType.NAUTICAL_ENGINE_LOAD,
            WidgetType.NAUTICAL_HUMIDITY -> context.getString(R.string.nautical_unit_percent)
            WidgetType.NAUTICAL_PRESSURE, WidgetType.NAUTICAL_BOOST_PRESSURE -> context.getString(R.string.nautical_unit_hpa)
            WidgetType.NAUTICAL_OIL_PRESSURE, WidgetType.NAUTICAL_TRANS_PRESS -> context.getString(R.string.nautical_unit_bar)
            WidgetType.NAUTICAL_WATER_TEMP, WidgetType.NAUTICAL_OUTSIDE_TEMP, WidgetType.NAUTICAL_ENGINE_TEMP,
            WidgetType.NAUTICAL_ENGINE_COOLANT, WidgetType.NAUTICAL_EXHAUST_TEMP, WidgetType.NAUTICAL_TRANS_OIL_TEMP,
            WidgetType.NAUTICAL_DEW_POINT -> context.getString(R.string.nautical_unit_celsius)
            WidgetType.NAUTICAL_ENGINE_RPM, WidgetType.NAUTICAL_ROT -> context.getString(R.string.nautical_unit_rpm)
            WidgetType.NAUTICAL_ENGINE_RUNTIME -> context.getString(R.string.nautical_unit_hours)
            WidgetType.NAUTICAL_ETA -> "ETA"
            WidgetType.NAUTICAL_TTW, WidgetType.NAUTICAL_TCPA -> ""
            else -> ""
        }
    }

    fun formatTelemetry(context: Context, settings: OsmandSettings, widget: WidgetType, state: MarineState?): Pair<String, String> {
        if (state == null) {
            return "--" to getDefaultUnit(context, settings, widget)
        }
        
        return when (widget) {
            WidgetType.NAUTICAL_SOG -> SignalKUnitConverter.formatValue(context, settings, state.speedOverGround, "speed")
            WidgetType.NAUTICAL_STW -> SignalKUnitConverter.formatValue(context, settings, state.speedThroughWater, "speed")
            WidgetType.NAUTICAL_DEPTH -> {
                val depth = state.depthBelowTransducer ?: state.depthSurfaceToTransducer ?: state.depthBelowKeel
                SignalKUnitConverter.formatValue(context, settings, depth, "depth")
            }
            WidgetType.NAUTICAL_DEPTH_KEEL -> SignalKUnitConverter.formatValue(context, settings, state.depthBelowKeel, "depth")
            WidgetType.NAUTICAL_WIND -> SignalKUnitConverter.formatValue(context, settings, state.windSpeedTrue, "speed")
            WidgetType.NAUTICAL_AWA -> SignalKUnitConverter.formatValue(context, settings, state.windDirectionApparent, "angle")
            WidgetType.NAUTICAL_AWS -> SignalKUnitConverter.formatValue(context, settings, state.windSpeedApparent, "speed")
            WidgetType.NAUTICAL_TWA -> SignalKUnitConverter.formatValue(context, settings, state.trueWindAngle, "angle")
            WidgetType.NAUTICAL_TWD -> SignalKUnitConverter.formatValue(context, settings, state.windDirectionTrue, "angle", state.magneticVariation)
            WidgetType.NAUTICAL_VMG -> SignalKUnitConverter.formatValue(context, settings, state.velocityMadeGood, "speed")
            WidgetType.NAUTICAL_COG -> SignalKUnitConverter.formatValue(context, settings, state.courseOverGroundTrue, "course", state.magneticVariation)
            WidgetType.NAUTICAL_HEADING_MAGNETIC -> SignalKUnitConverter.formatValue(context, settings, state.headingMagnetic ?: state.headingTrue, "heading", state.magneticVariation)
            WidgetType.NAUTICAL_MAG_VARIATION -> SignalKUnitConverter.formatValue(context, settings, state.magneticVariation, "angle")
            WidgetType.NAUTICAL_ROT -> SignalKUnitConverter.formatValue(context, settings, state.rateOfTurn?.let { Math.toDegrees(it) * 60.0 }, "revolutions")
            WidgetType.NAUTICAL_XTE -> SignalKUnitConverter.formatValue(context, settings, state.crossTrackError, "distance")
            WidgetType.NAUTICAL_TTW -> SignalKUnitConverter.formatValue(context, settings, state.timeToWaypoint, "timeToWaypoint")
            WidgetType.NAUTICAL_DTW -> SignalKUnitConverter.formatValue(context, settings, state.distanceToWaypoint, "distance")
            WidgetType.NAUTICAL_ETA -> {
                val ttw = state.timeToWaypoint
                if (ttw != null && ttw > 0) {
                    val etaMs = System.currentTimeMillis() + (ttw * 1000).toLong()
                    val timeFormat = android.text.format.DateFormat.getTimeFormat(context)
                    timeFormat.format(java.util.Date(etaMs)) to ""
                } else {
                    "--:--" to ""
                }
            }
            WidgetType.NAUTICAL_POLAR_RATIO -> SignalKUnitConverter.formatValue(context, settings, state.polarSpeedRatio, "polarSpeedRatio")
            WidgetType.NAUTICAL_POLAR_TARGET_SPEED -> SignalKUnitConverter.formatValue(context, settings, state.polarTargetSpeed, "speed")
            WidgetType.NAUTICAL_BATTERY_VOLT -> SignalKUnitConverter.formatValue(context, settings, state.batteries.values.firstOrNull()?.voltage, "voltage")
            WidgetType.NAUTICAL_BATTERY_SOC -> SignalKUnitConverter.formatValue(context, settings, state.batteries.values.firstOrNull()?.stateOfCharge, "stateOfCharge")
            WidgetType.NAUTICAL_BATTERY_CURRENT -> SignalKUnitConverter.formatValue(context, settings, state.batteries.values.firstOrNull()?.current, "current")
            WidgetType.NAUTICAL_SOLAR_CURRENT -> SignalKUnitConverter.formatValue(context, settings, state.solarCurrent, "current")
            WidgetType.NAUTICAL_WATER_TEMP -> SignalKUnitConverter.formatValue(context, settings, state.waterTemperature, "temperature")
            WidgetType.NAUTICAL_OUTSIDE_TEMP -> SignalKUnitConverter.formatValue(context, settings, state.outsideTemperature, "temperature")
            WidgetType.NAUTICAL_PRESSURE -> SignalKUnitConverter.formatValue(context, settings, state.outsidePressure, "pressure")
            WidgetType.NAUTICAL_HUMIDITY -> SignalKUnitConverter.formatValue(context, settings, state.outsideHumidity, "humidity")
            WidgetType.NAUTICAL_ROLL -> SignalKUnitConverter.formatValue(context, settings, state.roll, "roll")
            WidgetType.NAUTICAL_PITCH -> SignalKUnitConverter.formatValue(context, settings, state.pitch, "pitch")
            WidgetType.NAUTICAL_LOG -> SignalKUnitConverter.formatValue(context, settings, state.log, "log")
            WidgetType.NAUTICAL_TRIP_LOG -> SignalKUnitConverter.formatValue(context, settings, state.tripLog, "log")
            WidgetType.NAUTICAL_ENGINE_RPM -> SignalKUnitConverter.formatValue(context, settings, state.engines.values.firstOrNull()?.revolutions, "revolutions")
            WidgetType.NAUTICAL_ENGINE_TEMP -> SignalKUnitConverter.formatValue(context, settings, state.engines.values.firstOrNull()?.temperature, "temperature")
            WidgetType.NAUTICAL_ENGINE_RUNTIME -> SignalKUnitConverter.formatValue(context, settings, state.engineHours?.let { it * 3600.0 } ?: state.engines.values.firstOrNull()?.runTime, "runTime")
            WidgetType.NAUTICAL_FUEL_LEVEL -> SignalKUnitConverter.formatValue(context, settings, state.tanks["fuel.0"]?.currentLevel ?: state.tanks.values.firstOrNull { it.type == "fuel" }?.currentLevel, "currentLevel")
            WidgetType.NAUTICAL_FRESH_WATER_LEVEL -> SignalKUnitConverter.formatValue(context, settings, state.tanks["freshWater.0"]?.currentLevel ?: state.tanks.values.firstOrNull { it.type == "freshWater" }?.currentLevel, "currentLevel")
            WidgetType.NAUTICAL_WASTE_WATER_LEVEL -> SignalKUnitConverter.formatValue(context, settings, state.tanks["wasteWater.0"]?.currentLevel ?: state.tanks.values.firstOrNull { it.type == "wasteWater" || it.type == "blackWater" }?.currentLevel, "currentLevel")
            WidgetType.NAUTICAL_OIL_PRESSURE -> SignalKUnitConverter.formatValue(context, settings, state.engines.values.firstOrNull()?.oilPressure, "oilPressure")
            WidgetType.NAUTICAL_ENGINE_LOAD -> SignalKUnitConverter.formatValue(context, settings, state.engines.values.firstOrNull()?.load, "engineLoad")
            WidgetType.NAUTICAL_BOOST_PRESSURE -> SignalKUnitConverter.formatValue(context, settings, state.engines.values.firstOrNull()?.boostPressure, "pressure")
            WidgetType.NAUTICAL_EXHAUST_TEMP -> SignalKUnitConverter.formatValue(context, settings, state.engines.values.firstOrNull()?.exhaustTemperature, "temperature")
            WidgetType.NAUTICAL_ALTERNATOR_VOLT -> SignalKUnitConverter.formatValue(context, settings, state.engines.values.firstOrNull()?.alternatorVoltage, "voltage")
            WidgetType.NAUTICAL_ALTERNATOR_CURR -> SignalKUnitConverter.formatValue(context, settings, state.engines.values.firstOrNull()?.alternatorCurrent, "current")
            WidgetType.NAUTICAL_TRANS_GEAR -> (state.engines.values.firstOrNull()?.transmissionGear ?: "--") to ""
            WidgetType.NAUTICAL_TRANS_PRESS -> SignalKUnitConverter.formatValue(context, settings, state.engines.values.firstOrNull()?.transmissionPressure, "pressure")
            WidgetType.NAUTICAL_TRANS_OIL_TEMP -> SignalKUnitConverter.formatValue(context, settings, state.engines.values.firstOrNull()?.transmissionOilTemperature, "temperature")
            WidgetType.NAUTICAL_INV_STATE -> (state.inverters.values.firstOrNull()?.state ?: "--") to ""
            WidgetType.NAUTICAL_CHG_STATE -> (state.chargers.values.firstOrNull()?.state ?: "--") to ""
            WidgetType.NAUTICAL_WATERMAKER_RATE -> SignalKUnitConverter.formatValue(context, settings, state.watermakers.values.firstOrNull()?.rate, "watermakerRate")
            WidgetType.NAUTICAL_WATERMAKER_TOTAL -> SignalKUnitConverter.formatValue(context, settings, state.watermakers.values.firstOrNull()?.totalProduction, "volume")
            WidgetType.NAUTICAL_WATERMAKER_SALINITY -> SignalKUnitConverter.formatValue(context, settings, state.watermakers.values.firstOrNull()?.salinity, "salinity")
            WidgetType.NAUTICAL_REEFS -> (state.reefs?.toString() ?: "--") to ""
            WidgetType.NAUTICAL_RUDDER_ANGLE_TEXT -> {
                val deg = state.rudderAngle?.let { Math.toDegrees(it) }
                if (deg != null) {
                    val side = if (deg < -0.5) "P" else if (deg > 0.5) "S" else "C"
                    String.format(java.util.Locale.US, "%.0f° %s", kotlin.math.abs(deg), side) to ""
                } else {
                    "--" to ""
                }
            }
            WidgetType.NAUTICAL_CPA -> SignalKUnitConverter.formatValue(context, settings, state.cpa, "cpa")
            WidgetType.NAUTICAL_TCPA -> SignalKUnitConverter.formatValue(context, settings, state.tcpa, "tcpa")
            else -> "--" to getDefaultUnit(context, settings, widget)
        }
    }
}
