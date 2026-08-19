package net.osmand.plus.plugins.nautical.ui

import android.content.Context
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.plugins.nautical.engine.SignalKUnitConverter
import net.osmand.plus.settings.backend.OsmandSettings
import net.osmand.plus.views.mapwidgets.WidgetType

object NauticalWidgetHelper {

    fun formatTelemetry(context: Context, settings: OsmandSettings, widget: WidgetType, state: MarineState?): Pair<String, String> {
        if (state == null) return context.getString(R.string.n_a) to ""
        
        return when (widget) {
            WidgetType.NAUTICAL_SOG -> SignalKUnitConverter.formatValue(context, settings, state.speedOverGround, "speed")
            WidgetType.NAUTICAL_STW -> SignalKUnitConverter.formatValue(context, settings, state.speedThroughWater, "speed")
            WidgetType.NAUTICAL_COG -> SignalKUnitConverter.formatValue(context, settings, state.courseOverGroundTrue, "course")
            WidgetType.NAUTICAL_HEADING_MAGNETIC -> SignalKUnitConverter.formatValue(context, settings, state.headingMagnetic, "heading")
            WidgetType.NAUTICAL_DEPTH_KEEL -> SignalKUnitConverter.formatValue(context, settings, state.depthBelowKeel, "depth")
            WidgetType.NAUTICAL_WIND -> SignalKUnitConverter.formatValue(context, settings, state.windSpeedTrue, "speed")
            WidgetType.NAUTICAL_VMG -> SignalKUnitConverter.formatValue(context, settings, state.velocityMadeGood, "speed")
            WidgetType.NAUTICAL_BATTERY_VOLT -> SignalKUnitConverter.formatValue(context, settings, state.batteries["0"]?.voltage, "voltage")
            WidgetType.NAUTICAL_BATTERY_SOC -> SignalKUnitConverter.formatValue(context, settings, state.batteries["0"]?.stateOfCharge, "stateOfCharge")
            WidgetType.NAUTICAL_WATER_TEMP -> SignalKUnitConverter.formatValue(context, settings, state.waterTemperature, "temperature")
            WidgetType.NAUTICAL_OUTSIDE_TEMP -> SignalKUnitConverter.formatValue(context, settings, state.outsideTemperature, "temperature")
            WidgetType.NAUTICAL_PRESSURE -> SignalKUnitConverter.formatValue(context, settings, state.outsidePressure, "pressure")
            WidgetType.NAUTICAL_ROLL -> SignalKUnitConverter.formatValue(context, settings, state.roll, "roll")
            WidgetType.NAUTICAL_PITCH -> SignalKUnitConverter.formatValue(context, settings, state.pitch, "pitch")
            WidgetType.NAUTICAL_LOG -> SignalKUnitConverter.formatValue(context, settings, state.log, "log")
            WidgetType.NAUTICAL_TRIP_LOG -> SignalKUnitConverter.formatValue(context, settings, state.tripLog, "log")
            WidgetType.NAUTICAL_ENGINE_RUNTIME -> SignalKUnitConverter.formatValue(context, settings, state.engineHours?.let { it * 3600.0 } ?: state.engines["0"]?.runTime, "runTime")
            WidgetType.NAUTICAL_XTE -> SignalKUnitConverter.formatValue(context, settings, state.crossTrackError, "distance")
            WidgetType.NAUTICAL_DTW -> SignalKUnitConverter.formatValue(context, settings, state.distanceToWaypoint, "distance")
            WidgetType.NAUTICAL_BOOST_PRESSURE -> SignalKUnitConverter.formatValue(context, settings, state.engines["0"]?.boostPressure, "pressure")
            WidgetType.NAUTICAL_EXHAUST_TEMP -> SignalKUnitConverter.formatValue(context, settings, state.engines["0"]?.exhaustTemperature, "temperature")
            WidgetType.NAUTICAL_ALTERNATOR_VOLT -> SignalKUnitConverter.formatValue(context, settings, state.engines["0"]?.alternatorVoltage, "voltage")
            WidgetType.NAUTICAL_ALTERNATOR_CURR -> SignalKUnitConverter.formatValue(context, settings, state.engines["0"]?.alternatorCurrent, "current")
            WidgetType.NAUTICAL_TRANS_GEAR -> (state.engines["0"]?.transmissionGear ?: context.getString(R.string.n_a)) to ""
            WidgetType.NAUTICAL_TRANS_PRESS -> SignalKUnitConverter.formatValue(context, settings, state.engines["0"]?.transmissionPressure, "pressure")
            WidgetType.NAUTICAL_TRANS_OIL_TEMP -> SignalKUnitConverter.formatValue(context, settings, state.engines["0"]?.transmissionOilTemperature, "temperature")
            WidgetType.NAUTICAL_INV_STATE -> (state.inverters["0"]?.state ?: context.getString(R.string.n_a)) to ""
            WidgetType.NAUTICAL_CHG_STATE -> (state.chargers["0"]?.state ?: context.getString(R.string.n_a)) to ""
            WidgetType.NAUTICAL_WATERMAKER_RATE -> SignalKUnitConverter.formatValue(context, settings, state.watermakers["0"]?.rate, "watermakerRate")
            WidgetType.NAUTICAL_WATERMAKER_TOTAL -> SignalKUnitConverter.formatValue(context, settings, state.watermakers["0"]?.totalProduction, "volume")
            WidgetType.NAUTICAL_WATERMAKER_SALINITY -> SignalKUnitConverter.formatValue(context, settings, state.watermakers["0"]?.salinity, "salinity")
            WidgetType.NAUTICAL_REEFS -> (state.reefs?.toString() ?: context.getString(R.string.n_a)) to ""
            
            // System widgets support
            WidgetType.NAUTICAL_MEDIA -> context.getString(R.string.nautical_media_title) to ""
            WidgetType.NAUTICAL_CAMERA -> context.getString(R.string.nautical_camera) to ""
            WidgetType.NAUTICAL_RACING_TIMER -> context.getString(R.string.nautical_racing_timer_title) to ""
            WidgetType.NAUTICAL_WIND_SHIFT_WIDGET -> context.getString(R.string.nautical_wind_shift_title) to ""
            
            else -> context.getString(R.string.n_a) to ""
        }
    }
}
