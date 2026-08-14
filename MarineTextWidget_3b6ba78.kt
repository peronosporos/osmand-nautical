package net.osmand.plus.views.mapwidgets.widgets

import android.annotation.SuppressLint
import android.view.View
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.views.layers.base.OsmandMapLayer
import net.osmand.plus.views.mapwidgets.WidgetType
import net.osmand.plus.views.mapwidgets.WidgetsPanel
import net.osmand.plus.plugins.nautical.engine.SignalKUnitConverter
import java.util.*

class MarineTextWidget(
    mapActivity: MapActivity,
    widgetType: WidgetType,
    customId: String?,
    panel: WidgetsPanel?,
) : SimpleWidget(mapActivity, widgetType, customId, panel) {

    init {
        setIcons(widgetType)
    }

    override fun updateIcon() {
        val iconId = iconId
        if (iconId != 0) {
            if ((widgetType == WidgetType.NAUTICAL_DEPTH) || (widgetType == WidgetType.NAUTICAL_WIND)) {
                setImageDrawable(iconId)
            } else {
                val color = settings.applicationMode.getProfileColor(isNightMode)
                setImageDrawable(iconsCache.getPaintedIcon(iconId, color))
            }
        }
    }

    private var lastUpdateTime = 0L
    private val marineStateListener: (MarineState) -> Unit = {
        val now = System.currentTimeMillis()
        if ((now - lastUpdateTime) > 200) { // Throttle 5Hz
            lastUpdateTime = now
            mapActivity.runOnUiThread {
                updateInfo(null)
            }
        }
    }

    override fun setupView(view: View) {
        super.setupView(view)

        view.addOnAttachStateChangeListener(
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    NauticalPlugin.engine?.registerListener(marineStateListener)
                }

                override fun onViewDetachedFromWindow(v: View) {
                    NauticalPlugin.engine?.unregisterListener(marineStateListener)
                }
            },
        )
    }

    override fun getOnClickListener(): View.OnClickListener {
        return View.OnClickListener {
            if (!mapActivity.isFinishing) {
                if (widgetType == WidgetType.NAUTICAL_ENGINE_STATE) {
                    toggleEngineState()
                } else {
                    val dialog = NauticalDataBottomSheet.newInstance(this.widgetType)
                    dialog.show(mapActivity.supportFragmentManager, "nautical_graph")
                }
            }
        }
    }

    private fun toggleEngineState() {
        val engine = NauticalPlugin.engine ?: return
        val autopilot = NauticalPlugin.autopilot ?: return
        val state = engine.getCurrentState() ?: return
        val instance = state.engineInstance ?: "0"
        val isStarted = state.engineState?.lowercase(Locale.US) == "started"
        autopilot.setEngineState(instance, !isStarted)
    }

    @SuppressLint("DefaultLocale")
    override fun updateSimpleWidgetInfo(drawSettings: OsmandMapLayer.DrawSettings?) {
        val engine = NauticalPlugin.engine ?: return
        val state = engine.getCurrentState()

        updateIcon()

        if ((state == null) || (state.connectionStatus == net.osmand.plus.plugins.nautical.engine.ConnectionStatus.DISCONNECTED)) {
            setText(mapActivity.getString(R.string.nautical_status_off), mapActivity.getString(R.string.n_a))
            return
        }

        if (state.connectionStatus == net.osmand.plus.plugins.nautical.engine.ConnectionStatus.STALE) {
            contentView?.alpha = 0.5f
        } else {
            contentView?.alpha = 1.0f
        }

        val (main, sub) = when (widgetType) {
            WidgetType.NAUTICAL_DEPTH -> SignalKUnitConverter.formatValue(mapActivity, settings, state.depthBelowTransducer, "depth")
            WidgetType.NAUTICAL_WIND -> SignalKUnitConverter.formatValue(mapActivity, settings, state.windSpeedTrue, "speed")
            WidgetType.NAUTICAL_VMG -> SignalKUnitConverter.formatValue(mapActivity, settings, state.velocityMadeGood, "speed")
            WidgetType.NAUTICAL_COG -> SignalKUnitConverter.formatValue(mapActivity, settings, state.courseOverGroundTrue, "course")
            WidgetType.NAUTICAL_SOG -> {
                val trend = getTrend(state.speedOverGround, lastSog).also { lastSog = state.speedOverGround }
                val (v, u) = SignalKUnitConverter.formatValue(mapActivity, settings, state.speedOverGround, "speed")
                if (v == mapActivity.getString(R.string.n_a)) v to u else "$v$trend" to u
            }
            WidgetType.NAUTICAL_STW -> {
                val trend = getTrend(state.speedThroughWater, lastStw).also { lastStw = state.speedThroughWater }
                val (v, u) = SignalKUnitConverter.formatValue(mapActivity, settings, state.speedThroughWater, "speed")
                if (v == mapActivity.getString(R.string.n_a)) v to u else "$v$trend" to u
            }
            WidgetType.NAUTICAL_HEADING_MAGNETIC -> SignalKUnitConverter.formatValue(mapActivity, settings, state.headingMagnetic, "heading")
            WidgetType.NAUTICAL_LOG -> SignalKUnitConverter.formatValue(mapActivity, settings, state.log, "log")
            WidgetType.NAUTICAL_TRIP_LOG -> SignalKUnitConverter.formatValue(mapActivity, settings, state.tripLog, "log")
            WidgetType.NAUTICAL_ROLL -> SignalKUnitConverter.formatValue(mapActivity, settings, state.roll, "roll")
            WidgetType.NAUTICAL_PITCH -> SignalKUnitConverter.formatValue(mapActivity, settings, state.pitch, "pitch")
            WidgetType.NAUTICAL_DEPTH_KEEL -> SignalKUnitConverter.formatValue(mapActivity, settings, state.depthBelowKeel, "depth")
            WidgetType.NAUTICAL_WATER_TEMP -> SignalKUnitConverter.formatValue(mapActivity, settings, state.waterTemperature, "temperature")
            WidgetType.NAUTICAL_OUTSIDE_TEMP -> SignalKUnitConverter.formatValue(mapActivity, settings, state.outsideTemperature, "temperature")
            WidgetType.NAUTICAL_PRESSURE -> SignalKUnitConverter.formatValue(mapActivity, settings, state.outsidePressure, "pressure")
            WidgetType.NAUTICAL_ENGINE_RPM -> SignalKUnitConverter.formatValue(mapActivity, settings, state.engineRpm?.let { it / 60.0 }, "revolutions")
            WidgetType.NAUTICAL_ENGINE_TEMP -> SignalKUnitConverter.formatValue(mapActivity, settings, state.engineTemperature, "temperature")
            WidgetType.NAUTICAL_BATTERY_VOLT -> SignalKUnitConverter.formatValue(mapActivity, settings, state.batteryVoltage, "voltage")
            WidgetType.NAUTICAL_BATTERY_SOC -> SignalKUnitConverter.formatValue(mapActivity, settings, state.batterySoc, "stateOfCharge")
            WidgetType.NAUTICAL_FUEL_LEVEL -> SignalKUnitConverter.formatValue(mapActivity, settings, state.fuelLevel, "currentLevel")
            WidgetType.NAUTICAL_FRESH_WATER_LEVEL -> SignalKUnitConverter.formatValue(mapActivity, settings, state.freshWaterLevel, "currentLevel")
            WidgetType.NAUTICAL_WASTE_WATER_LEVEL -> SignalKUnitConverter.formatValue(mapActivity, settings, state.wasteWaterLevel, "currentLevel")
            WidgetType.NAUTICAL_POLAR_RATIO -> SignalKUnitConverter.formatValue(mapActivity, settings, state.polarSpeedRatio, "polarSpeedRatio")
            WidgetType.NAUTICAL_ROT -> SignalKUnitConverter.formatValue(mapActivity, settings, state.rateOfTurn, "angle")
            WidgetType.NAUTICAL_XTE -> SignalKUnitConverter.formatValue(mapActivity, settings, state.crossTrackError, "distance")
            WidgetType.NAUTICAL_TTW -> {
                val ttw = state.timeToWaypoint
                val unit = mapActivity.getString(R.string.nautical_unit_hour_short)
                if (ttw != null) {
                    val h = (ttw / 3600).toInt()
                    val m = ((ttw % 3600) / 60).toInt()
                    String.format(Locale.US, "%02d:%02d", h, m) to unit
                } else mapActivity.getString(R.string.n_a) to unit
            }
            WidgetType.NAUTICAL_DTW -> SignalKUnitConverter.formatValue(mapActivity, settings, state.distanceToWaypoint, "distance")
            WidgetType.NAUTICAL_ETA -> {
                val ttw = state.timeToWaypoint
                if (ttw != null) {
                    val etaMs = System.currentTimeMillis() + (ttw * 1000).toLong()
                    val is24 = android.text.format.DateFormat.is24HourFormat(mapActivity)
                    val pattern = if (is24) "HH:mm" else "h:mm a"
                    val sdf = java.text.SimpleDateFormat(pattern, Locale.US)
                    sdf.format(Date(etaMs)) to ""
                } else mapActivity.getString(R.string.n_a) to ""
            }
            WidgetType.NAUTICAL_AWA -> SignalKUnitConverter.formatValue(mapActivity, settings, state.windDirectionApparent, "angle")
            WidgetType.NAUTICAL_AWS -> SignalKUnitConverter.formatValue(mapActivity, settings, state.windSpeedApparent, "speed")
            WidgetType.NAUTICAL_TWA -> SignalKUnitConverter.formatValue(mapActivity, settings, state.trueWindAngle, "angle")
            WidgetType.NAUTICAL_TWD -> SignalKUnitConverter.formatValue(mapActivity, settings, state.windDirectionTrue, "angle")
            WidgetType.NAUTICAL_OIL_PRESSURE -> SignalKUnitConverter.formatValue(mapActivity, settings, state.engineOilPressure, "oilPressure")
            WidgetType.NAUTICAL_ENGINE_LOAD -> SignalKUnitConverter.formatValue(mapActivity, settings, state.engineLoad, "engineLoad")
            WidgetType.NAUTICAL_BATTERY_CURRENT -> SignalKUnitConverter.formatValue(mapActivity, settings, state.batteryCurrent, "current")
            WidgetType.NAUTICAL_SOLAR_CURRENT -> SignalKUnitConverter.formatValue(mapActivity, settings, state.solarCurrent, "current")
            WidgetType.NAUTICAL_ENGINE_RUNTIME -> SignalKUnitConverter.formatValue(mapActivity, settings, state.engineRunTime, "distance") // reuse distance for hours/nm coeff? No, needs its own.
            WidgetType.NAUTICAL_ENGINE_COOLANT -> SignalKUnitConverter.formatValue(mapActivity, settings, state.engineCoolantTemperature, "temperature")
            WidgetType.NAUTICAL_ENGINE_STATE -> (state.engineState?.uppercase(Locale.US) ?: mapActivity.getString(R.string.n_a)) to ""
            WidgetType.NAUTICAL_SET_DRIFT -> {
                val set = state.setTrue
                val drift = state.drift
                if (set != null && drift != null) {
                    val (sV, _) = SignalKUnitConverter.formatValue(mapActivity, settings, set, "angle")
                    val (dV, dU) = SignalKUnitConverter.formatValue(mapActivity, settings, drift, "speed")
                    "$sV/$dV" to dU
                } else mapActivity.getString(R.string.n_a) to mapActivity.getString(R.string.nautical_unit_knots)
            }
            else -> {
                // Handle custom paths if widgetType.customId matches a path
                val customVal = state.customValues[customId]
                if (customVal != null) {
                    SignalKUnitConverter.formatValue(mapActivity, settings, customVal, customId ?: "")
                } else {
                    "" to ""
                }
            }
        }
        setText(main, sub)
    }

    private var lastSog: Double? = null
    private var lastStw: Double? = null

    private fun getTrend(current: Double?, last: Double?): String {
        return when {
            (current == null) || (last == null) || (kotlin.math.abs(current - last) < 0.01) -> ""
            current > last -> mapActivity.getString(R.string.nautical_trend_up)
            else -> mapActivity.getString(R.string.nautical_trend_down)
        }
    }
}
