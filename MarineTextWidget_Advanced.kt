package net.osmand.plus.views.mapwidgets.widgets

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.View
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.plugins.nautical.engine.SignalKPaths
import net.osmand.plus.views.layers.base.OsmandMapLayer
import net.osmand.plus.views.mapwidgets.WidgetType
import net.osmand.plus.views.mapwidgets.WidgetsPanel
import net.osmand.plus.plugins.nautical.engine.SignalKUnitConverter
import net.osmand.plus.plugins.nautical.utils.TemporalUtils
import java.util.*
import kotlin.math.abs

class MarineTextWidget(
    mapActivity: MapActivity,
    widgetType: WidgetType,
    customId: String?,
    panel: WidgetsPanel?,
) : SimpleWidget(mapActivity, widgetType, customId, panel) {

    init {
        setIcons(widgetType)
    }

    override fun updateWidgetView() {
        super.updateWidgetView()
        widgetName?.visibility = View.GONE
    }

    override fun updateIcon() {
        val iconId = iconId
        if (iconId != 0) {
            val engine = NauticalPlugin.engine
            val state = engine?.getCurrentState()
            val isStale = state?.let { isWidgetDataStale(it) } ?: false
            
            val color = if (isStale) {
                ContextCompat.getColor(app, R.color.icon_color_warning)
            } else {
                ContextCompat.getColor(app, R.color.map_widget_icon_color)
            }
            setImageDrawable(iconsCache.getPaintedIcon(iconId, color))
        }
    }

    private val marineStateListener: (MarineState) -> Unit = {
        mapActivity.runOnUiThread {
            updateInfo(null)
        }
    }

    private var dataJob: kotlinx.coroutines.Job? = null

    override fun setupView(view: View) {
        super.setupView(view)

        view.addOnAttachStateChangeListener(
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    val engine = NauticalPlugin.engine
                    engine?.registerListener(marineStateListener)
                    
                    val broker = engine?.dataBroker
                    if (broker != null) {
                        dataJob?.cancel()
                        dataJob = broker.marineState
                            .onEach {
                                mapActivity.runOnUiThread {
                                    updateInfo(null)
                                }
                            }
                            .launchIn(mapActivity.lifecycleScope)
                    }
                }

                override fun onViewDetachedFromWindow(v: View) {
                    NauticalPlugin.engine?.unregisterListener(marineStateListener)
                    dataJob?.cancel()
                    dataJob = null
                }
            },
        )
    }

    override fun getOnClickListener(): View.OnClickListener {
        return View.OnClickListener {
            if (!mapActivity.isFinishing) {
                when (widgetType) {
                    WidgetType.NAUTICAL_ENGINE_STATE -> {
                        toggleEngineState()
                    }
                    WidgetType.NAUTICAL_VHF_CHANNEL -> {
                        net.osmand.plus.plugins.nautical.ui.VhfChannelPickerDialog.show(mapActivity.supportFragmentManager)
                    }
                    else -> {
                        val dialog = NauticalDataBottomSheet.newInstance(this.widgetType)
                        dialog.show(mapActivity.supportFragmentManager, "nautical_graph")
                    }
                }
            }
        }
    }

    private fun toggleEngineState() {
        val engine = NauticalPlugin.engine ?: return
        val autopilot = NauticalPlugin.autopilot ?: return
        val state = engine.getCurrentState()
        val instance = "0" // Default to instance 0 for single-engine toggle
        val isStarted = state.engines[instance]?.state?.lowercase(Locale.US) == "started"
        autopilot.setEngineState(instance, !isStarted)
    }

    private enum class IntegrityState {
        VALID, STALE, ALARM, DISCONNECTED
    }

    private fun getIntegrityState(state: MarineState?): IntegrityState {
        if ((state == null) || (state.connectionStatus == net.osmand.plus.plugins.nautical.engine.ConnectionStatus.DISCONNECTED)) {
            return IntegrityState.DISCONNECTED
        }

        val path = when (widgetType) {
            WidgetType.NAUTICAL_DEPTH -> SignalKPaths.ENV_DEPTH_BELOW_TRANSDUCER
            WidgetType.NAUTICAL_WIND -> SignalKPaths.ENV_WIND_SPEED_TRUE
            WidgetType.NAUTICAL_SOG -> SignalKPaths.NAV_SPEED_OVER_GROUND
            WidgetType.NAUTICAL_COG -> SignalKPaths.NAV_COURSE_OVER_GROUND
            WidgetType.NAUTICAL_STW -> SignalKPaths.NAV_SPEED_THROUGH_WATER
            WidgetType.NAUTICAL_HEADING_MAGNETIC -> SignalKPaths.NAV_HEADING_MAG
            WidgetType.NAUTICAL_XTE -> SignalKPaths.NAV_XTE
            WidgetType.NAUTICAL_AWA -> SignalKPaths.ENV_WIND_ANGLE_APPARENT
            WidgetType.NAUTICAL_AWS -> SignalKPaths.ENV_WIND_SPEED_APPARENT
            WidgetType.NAUTICAL_HUMIDITY -> SignalKPaths.ENV_OUTSIDE_HUMIDITY
            WidgetType.NAUTICAL_AC_VOLTAGE -> "electrical.ac.0.voltage"
            WidgetType.NAUTICAL_AC_CURRENT -> "electrical.ac.0.current"
            WidgetType.NAUTICAL_AC_FREQUENCY -> "electrical.ac.0.frequency"
            WidgetType.NAUTICAL_VHF_CHANNEL -> SignalKPaths.COMMUNICATION_VHF_CHANNEL
            WidgetType.NAUTICAL_GNSS_QUALITY -> "navigation.gnss.horizontalDilution"
            WidgetType.NAUTICAL_SALINITY -> SignalKPaths.ENV_WATER_SALINITY
            WidgetType.NAUTICAL_DEW_POINT -> SignalKPaths.ENV_AIR_DEW_POINT
            WidgetType.NAUTICAL_ILLUMINANCE -> SignalKPaths.ENV_OUTSIDE_ILLUMINANCE
            WidgetType.NAUTICAL_RIGGING_LOAD -> "rigging.loads.0"
            WidgetType.NAUTICAL_MEDIA -> SignalKPaths.MEDIA_TITLE
            WidgetType.NAUTICAL_WIND_SHIFT_WIDGET -> SignalKPaths.PERF_WIND_SHIFT
            WidgetType.NAUTICAL_RACING_TIMER -> SignalKPaths.PERF_RACING_TIMER
            WidgetType.NAUTICAL_WATERMAKER -> "propulsion.watermaker.0.rate"
            WidgetType.NAUTICAL_BOOST_PRESSURE -> "propulsion.0.boostPressure"
            WidgetType.NAUTICAL_EXHAUST_TEMP -> "propulsion.0.exhaustTemperature"
            WidgetType.NAUTICAL_ALTERNATOR_VOLT -> "propulsion.0.alternatorVoltage"
            WidgetType.NAUTICAL_ALTERNATOR_CURR -> "propulsion.0.alternatorCurrent"
            WidgetType.NAUTICAL_TRANS_GEAR -> "propulsion.0.transmissionGear"
            WidgetType.NAUTICAL_TRANS_PRESS -> "propulsion.0.transmissionPressure"
            WidgetType.NAUTICAL_TRANS_OIL_TEMP -> "propulsion.0.transmissionOilTemperature"
            WidgetType.NAUTICAL_INV_STATE -> "electrical.inverters.0.state"
            WidgetType.NAUTICAL_CHG_STATE -> "electrical.chargers.0.state"
            WidgetType.NAUTICAL_REEFS -> SignalKPaths.SAILS_REEFS
            else -> customId ?: ""
        }

        val now = TemporalUtils.now()
        val timestamp = state.timestamps[path] ?: 0L
        val age = (now - timestamp) / 1000.0

        return when {
            age > 10.0 -> IntegrityState.ALARM
            (age > 3.0) || state.stalePaths.contains(path) || (state.connectionStatus == net.osmand.plus.plugins.nautical.engine.ConnectionStatus.STALE) -> IntegrityState.STALE
            else -> IntegrityState.VALID
        }
    }

    private var lastFormattedMain: String? = null
    private var lastFormattedSub: String? = null
    private var lastIntegrity: IntegrityState? = null

    private var lastSog: Double? = null
    private var lastStw: Double? = null
    private var lastDepth: Double? = null

    private fun getTrend(current: Double?, last: Double?): String {
        return when {
            (current == null) || (last == null) || (abs(current - last) < 0.01) -> ""
            current > last -> mapActivity.getString(R.string.nautical_trend_up)
            else -> mapActivity.getString(R.string.nautical_trend_down)
        }
    }

    @SuppressLint("DefaultLocale")
    override fun updateSimpleWidgetInfo(drawSettings: OsmandMapLayer.DrawSettings?) {
        val engine = NauticalPlugin.engine
        if (engine == null) {
            setText("--", "N/A")
            return
        }
        val state = engine.getCurrentState()
        val integrity = getIntegrityState(state)

        updateIcon()
        applyIntegrityStyling(integrity)

        if (integrity == IntegrityState.DISCONNECTED) {
            setText(mapActivity.getString(R.string.nautical_status_off), mapActivity.getString(R.string.n_a))
            return
        }

        val instance = customId?.substringAfterLast("#") ?: "0"
        val variation = state.magneticVariation

        val (mainValue, formattedSub) = when (widgetType) {
            WidgetType.NAUTICAL_DEPTH -> {
                val depth = state.depthBelowTransducer
                val trend = getTrend(depth, lastDepth).also { lastDepth = depth }
                val (v, u) = SignalKUnitConverter.formatValue(mapActivity, settings, depth, SignalKPaths.ENV_DEPTH_BELOW_TRANSDUCER)
                (if (v == mapActivity.getString(R.string.n_a)) v else "$v$trend") to u
            }
            WidgetType.NAUTICAL_WIND -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.windSpeedTrue, SignalKPaths.ENV_WIND_SPEED_TRUE)
            WidgetType.NAUTICAL_VMG -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.velocityMadeGood, SignalKPaths.PERF_VMG)
            WidgetType.NAUTICAL_COG -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.courseOverGroundTrue, SignalKPaths.NAV_COURSE_OVER_GROUND, variation)
            WidgetType.NAUTICAL_SOG -> {
                val trend = getTrend(state.speedOverGround, lastSog).also { lastSog = state.speedOverGround }
                val (v, u) = SignalKUnitConverter.formatValue(mapActivity, settings,
                    state.speedOverGround, SignalKPaths.NAV_SPEED_OVER_GROUND)
                (if (v == mapActivity.getString(R.string.n_a)) v else "$v$trend") to u
            }
            WidgetType.NAUTICAL_STW -> {
                val speed = if (state.isStwUnreliable) state.speedOverGround else state.speedThroughWater
                val trend = getTrend(speed, lastStw).also { lastStw = speed }
                val (v, u) = SignalKUnitConverter.formatValue(mapActivity, settings,
                    speed, SignalKPaths.NAV_SPEED_THROUGH_WATER)
                (if (v == mapActivity.getString(R.string.n_a)) v else "$v$trend") to u
            }
            WidgetType.NAUTICAL_HEADING_MAGNETIC -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.headingMagnetic, SignalKPaths.NAV_HEADING_MAG, variation)
            WidgetType.NAUTICAL_LOG -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.log, SignalKPaths.NAV_LOG)
            WidgetType.NAUTICAL_TRIP_LOG -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.tripLog, SignalKPaths.NAV_TRIP_LOG)
            WidgetType.NAUTICAL_ROLL -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.roll, SignalKPaths.NAV_ATTITUDE)
            WidgetType.NAUTICAL_PITCH -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.pitch, SignalKPaths.NAV_ATTITUDE)
            WidgetType.NAUTICAL_DEPTH_KEEL -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.depthBelowKeel, SignalKPaths.ENV_DEPTH_BELOW_KEEL)
            WidgetType.NAUTICAL_WATER_TEMP -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.waterTemperature, SignalKPaths.ENV_WATER_TEMP)
            WidgetType.NAUTICAL_OUTSIDE_TEMP -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.outsideTemperature, SignalKPaths.ENV_OUTSIDE_TEMP)
            WidgetType.NAUTICAL_PRESSURE -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.outsidePressure, SignalKPaths.ENV_OUTSIDE_PRESSURE)
            WidgetType.NAUTICAL_ENGINE_RPM -> SignalKUnitConverter.formatValue(mapActivity, settings, state.engines[instance]?.revolutions, "revolutions")
            WidgetType.NAUTICAL_ENGINE_TEMP -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.engines[instance]?.temperature, SignalKPaths.ENV_WATER_TEMP)
            WidgetType.NAUTICAL_BATTERY_VOLT -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.batteries[instance]?.voltage, "voltage")
            WidgetType.NAUTICAL_BATTERY_SOC -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.batteries[instance]?.stateOfCharge, "stateOfCharge")
            WidgetType.NAUTICAL_FUEL_LEVEL -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.tanks["fuel.$instance"]?.currentLevel ?: state.tanks[instance]?.currentLevel, "currentLevel")
            WidgetType.NAUTICAL_FRESH_WATER_LEVEL -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.tanks["freshWater.$instance"]?.currentLevel ?: state.tanks[instance]?.currentLevel, "currentLevel")
            WidgetType.NAUTICAL_WASTE_WATER_LEVEL -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.tanks["wasteWater.$instance"]?.currentLevel ?: state.tanks[instance]?.currentLevel, "currentLevel")
            WidgetType.NAUTICAL_POLAR_RATIO -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.polarSpeedRatio, SignalKPaths.PERF_POLAR_RATIO)
            WidgetType.NAUTICAL_ROT -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.rateOfTurn, SignalKPaths.NAV_RATE_OF_TURN)
            WidgetType.NAUTICAL_XTE -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.crossTrackError, SignalKPaths.NAV_XTE)
            WidgetType.NAUTICAL_TTW -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.timeToWaypoint, SignalKPaths.NAV_TTW)
            WidgetType.NAUTICAL_DTW -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.distanceToWaypoint, SignalKPaths.NAV_DTW)
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
            WidgetType.NAUTICAL_AWA -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.windDirectionApparent, SignalKPaths.ENV_WIND_ANGLE_APPARENT)
            WidgetType.NAUTICAL_AWS -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.windSpeedApparent, SignalKPaths.ENV_WIND_SPEED_APPARENT)
            WidgetType.NAUTICAL_TWA -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.trueWindAngle, SignalKPaths.ENV_WIND_ANGLE_TRUE)
            WidgetType.NAUTICAL_TWD -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.windDirectionTrue, SignalKPaths.NAV_TWD, variation)
            WidgetType.NAUTICAL_OIL_PRESSURE -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.engines[instance]?.oilPressure, "oilPressure")
            WidgetType.NAUTICAL_ENGINE_LOAD -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.engines[instance]?.load, "engineLoad")
            WidgetType.NAUTICAL_BATTERY_CURRENT -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.batteries[instance]?.current, "current")
            WidgetType.NAUTICAL_SOLAR_CURRENT -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.customValues["electrical.solar.$instance.current"], "current")
            WidgetType.NAUTICAL_ENGINE_RUNTIME -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.engines[instance]?.runTime, "runTime")
            WidgetType.NAUTICAL_ENGINE_COOLANT -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.engines[instance]?.coolantTemperature, SignalKPaths.ENV_WATER_TEMP)
            WidgetType.NAUTICAL_ENGINE_STATE -> (state.engines[instance]?.state?.uppercase(Locale.US) ?: mapActivity.getString(R.string.n_a)) to ""
            WidgetType.NAUTICAL_MAG_VARIATION -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.magneticVariation, SignalKPaths.NAV_MAG_VARIATION)
            WidgetType.NAUTICAL_YAW -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.yaw, SignalKPaths.NAV_ATTITUDE)
            WidgetType.NAUTICAL_CPA -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.cpa, SignalKPaths.NAV_CLOSEST_APPROACH)
            WidgetType.NAUTICAL_TCPA -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.tcpa, "tcpa")
            WidgetType.NAUTICAL_RUDDER_ANGLE_TEXT -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.rudderAngle, SignalKPaths.STEERING_RUDDER_ANGLE)
            WidgetType.NAUTICAL_POLAR_TARGET_SPEED -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.polarTargetSpeed, SignalKPaths.PERF_TARGET_SPEED)
            WidgetType.NAUTICAL_RANGE -> {
                val range = state.estimatedRange
                val unit = mapActivity.getString(R.string.nautical_range_unit_km)
                if (range != null) {
                    val km = range / 1000.0
                    String.format(Locale.US, "%.1f", km) to unit
                } else mapActivity.getString(R.string.n_a) to unit
            }
            WidgetType.NAUTICAL_SET_DRIFT -> {
                val set = state.setTrue
                val drift = state.drift
                if (set != null && drift != null) {
                    val (sV, _) = SignalKUnitConverter.formatValue(mapActivity, settings, set, SignalKPaths.NAV_SET_TRUE, variation)
                    val (dV, dU) = SignalKUnitConverter.formatValue(mapActivity, settings, drift, SignalKPaths.NAV_DRIFT)
                    "$sV/$dV" to dU
                } else mapActivity.getString(R.string.n_a) to mapActivity.getString(R.string.nautical_unit_knots)
            }
            WidgetType.NAUTICAL_HUMIDITY -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.outsideHumidity, SignalKPaths.ENV_OUTSIDE_HUMIDITY)
            WidgetType.NAUTICAL_MOON_PHASE -> {
                val phase = state.moonPhase ?: state.customValues[SignalKPaths.NAV_DATETIME_MOON_PHASE]
                if (phase != null) {
                    String.format(Locale.US, "%.0f%%", phase * 100.0) to ""
                } else mapActivity.getString(R.string.n_a) to ""
            }
            WidgetType.NAUTICAL_SUNLIGHT_MODE -> (state.sunlightMode?.uppercase(Locale.US) ?: mapActivity.getString(R.string.n_a)) to ""
            WidgetType.NAUTICAL_AC_VOLTAGE -> SignalKUnitConverter.formatValue(
                mapActivity, settings, state.inverters[instance]?.acVoltage ?: state.customValues["electrical.ac.$instance.voltage"], "voltage",
            )
            WidgetType.NAUTICAL_AC_CURRENT -> SignalKUnitConverter.formatValue(
                mapActivity, settings, state.inverters[instance]?.acCurrent ?: state.customValues["electrical.ac.$instance.current"], "current",
            )
            WidgetType.NAUTICAL_AC_FREQUENCY -> SignalKUnitConverter.formatValue(
                mapActivity, settings, state.inverters[instance]?.acFrequency ?: state.customValues["electrical.ac.$instance.frequency"], "frequency",
            )
            WidgetType.NAUTICAL_VHF_CHANNEL -> (state.vhfChannel ?: mapActivity.getString(R.string.n_a)) to ""
            WidgetType.NAUTICAL_GNSS_QUALITY -> {
                val gnss = state.gnss
                if (gnss != null) {
                    val sats = gnss.satellites ?: 0
                    val hdop = gnss.horizontalDilution
                    val hdopStr = if (hdop != null) String.format(Locale.US, "%.1f", hdop) else "N/A"
                    "$sats/$hdopStr" to "S/H"
                } else mapActivity.getString(R.string.n_a) to ""
            }
            WidgetType.NAUTICAL_SALINITY -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.waterSalinity, SignalKPaths.ENV_WATER_SALINITY)
            WidgetType.NAUTICAL_DEW_POINT -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.airDewPoint, SignalKPaths.ENV_AIR_DEW_POINT)
            WidgetType.NAUTICAL_ILLUMINANCE -> {
                val illum = state.outsideIlluminance
                if (illum != null) {
                    String.format(Locale.US, "%.0f", illum) to "lux"
                } else mapActivity.getString(R.string.n_a) to "lux"
            }
            WidgetType.NAUTICAL_RIGGING_LOAD -> {
                val load = if (instance == "0") state.riggingLoads.values.maxOrNull() else state.riggingLoads[instance]
                if (load != null) {
                    String.format(Locale.US, "%.0f", load) to "kgf"
                } else mapActivity.getString(R.string.n_a) to "kgf"
            }
            WidgetType.NAUTICAL_MEDIA -> {
                val info = state.mediaInfo
                if (info != null) {
                    val text = if (info.title != null) {
                        if (info.artist != null) "${info.title} - ${info.artist}" else info.title
                    } else mapActivity.getString(R.string.nautical_no_media)
                    text to (info.playbackState ?: "")
                } else mapActivity.getString(R.string.n_a) to ""
            }
            WidgetType.NAUTICAL_WIND_SHIFT_WIDGET -> {
                val shift = state.windShift
                if (shift != null) {
                    val deg = Math.toDegrees(shift)
                    val sign = if (deg > 0) "+" else ""
                    String.format(Locale.US, "%s%.1f°", sign, deg) to mapActivity.getString(R.string.nautical_unit_degree)
                } else mapActivity.getString(R.string.n_a) to ""
            }
            WidgetType.NAUTICAL_RACING_TIMER -> {
                val timer = state.racingTimer
                if (timer != null) {
                    val absTimer = abs(timer).toInt()
                    val m = absTimer / 60
                    val s = absTimer % 60
                    val sign = if (timer < 0) "-" else ""
                    String.format(Locale.US, "%s%02d:%02d", sign, m, s) to ""
                } else mapActivity.getString(R.string.n_a) to ""
            }
            WidgetType.NAUTICAL_WATERMAKER -> {
                val watermaker = state.watermakers[instance] ?: state.watermakers.values.firstOrNull()
                if (watermaker != null) {
                    val rate = watermaker.rate ?: 0.0
                    String.format(Locale.US, "%.1f", rate) to "L/h"
                } else mapActivity.getString(R.string.n_a) to "L/h"
            }
            WidgetType.NAUTICAL_BOOST_PRESSURE -> SignalKUnitConverter.formatValue(mapActivity, settings, state.engines[instance]?.boostPressure, "pressure")
            WidgetType.NAUTICAL_EXHAUST_TEMP -> SignalKUnitConverter.formatValue(mapActivity, settings, state.engines[instance]?.exhaustTemperature, SignalKPaths.ENV_WATER_TEMP)
            WidgetType.NAUTICAL_ALTERNATOR_VOLT -> SignalKUnitConverter.formatValue(mapActivity, settings, state.engines[instance]?.alternatorVoltage, "voltage")
            WidgetType.NAUTICAL_ALTERNATOR_CURR -> SignalKUnitConverter.formatValue(mapActivity, settings, state.engines[instance]?.alternatorCurrent, "current")
            WidgetType.NAUTICAL_TRANS_GEAR -> (state.engines[instance]?.transmissionGear ?: mapActivity.getString(R.string.n_a)) to ""
            WidgetType.NAUTICAL_TRANS_PRESS -> SignalKUnitConverter.formatValue(mapActivity, settings, state.engines[instance]?.transmissionPressure, "pressure")
            WidgetType.NAUTICAL_TRANS_OIL_TEMP -> SignalKUnitConverter.formatValue(mapActivity, settings, state.engines[instance]?.transmissionOilTemperature, SignalKPaths.ENV_WATER_TEMP)
            WidgetType.NAUTICAL_INV_STATE -> (state.inverters[instance]?.state ?: mapActivity.getString(R.string.n_a)) to ""
            WidgetType.NAUTICAL_CHG_STATE -> (state.chargers[instance]?.state ?: mapActivity.getString(R.string.n_a)) to ""
            WidgetType.NAUTICAL_WATERMAKER_RATE -> SignalKUnitConverter.formatValue(mapActivity, settings, state.watermakers[instance]?.rate, "watermakerRate")
            WidgetType.NAUTICAL_WATERMAKER_TOTAL -> SignalKUnitConverter.formatValue(mapActivity, settings, state.watermakers[instance]?.totalProduction, "volume")
            WidgetType.NAUTICAL_WATERMAKER_SALINITY -> SignalKUnitConverter.formatValue(mapActivity, settings, state.watermakers[instance]?.salinity, "salinity")
            WidgetType.NAUTICAL_REEFS -> (state.reefs?.toString() ?: mapActivity.getString(R.string.n_a)) to ""
            WidgetType.NAUTICAL_RIGGING_LOADS -> {
                val loads = if (instance == "0") state.riggingLoads.values else state.riggingLoads.filterKeys { it.startsWith(instance) }.values
                if (loads.isNotEmpty()) {
                    val max = loads.max()
                    String.format(Locale.US, "%.0f", max) to "kgf (${loads.size})"
                } else mapActivity.getString(R.string.n_a) to "kgf"
            }
            WidgetType.NAUTICAL_AC_SYSTEM -> {
                val inv = state.inverters[instance] ?: state.inverters["0"]
                val chg = state.chargers[instance] ?: state.chargers["0"]
                val v = inv?.acVoltage ?: 0.0
                if (v > 50) {
                    String.format(Locale.US, "%.0f", v) to "V AC"
                } else if (chg != null) {
                    (chg.state?.uppercase(Locale.US) ?: "CHG") to "AC"
                } else mapActivity.getString(R.string.n_a) to "AC"
            }
            WidgetType.NAUTICAL_NOTIFICATIONS_LIST -> {
                val count = state.notifications.size
                if (count > 0) {
                    count.toString() to mapActivity.getString(R.string.nautical_notifications)
                } else "OK" to ""
            }
            else -> {
                val customVal = state.customValues[customId]
                if (customVal != null) {
                    SignalKUnitConverter.formatValue(mapActivity, settings, customVal, customId ?: "")
                } else {
                    "" to ""
                }
            }
        }
        
        val main = mainValue
        val sub = formattedSub

        if (integrity == lastIntegrity && main == lastFormattedMain && sub == lastFormattedSub) {
            return
        }
        lastIntegrity = integrity
        lastFormattedMain = main
        lastFormattedSub = sub

        if (integrity == IntegrityState.ALARM) {
            val safetyCritical = (widgetType == WidgetType.NAUTICAL_DEPTH) || 
                                (widgetType == WidgetType.NAUTICAL_DEPTH_KEEL) || 
                                (widgetType == WidgetType.NAUTICAL_XTE)
            val msg = if (safetyCritical) mapActivity.getString(R.string.nautical_timeout) else "X"
            setText(msg, "")
        } else {
            setText(main, sub)
        }
        updateAccessibilityDescription(main, sub, integrity)
    }

    private fun updateAccessibilityDescription(main: String, sub: String, integrity: IntegrityState) {
        val label = when (widgetType) {
            WidgetType.NAUTICAL_DEPTH -> mapActivity.getString(R.string.nautical_widget_depth_label)
            WidgetType.NAUTICAL_WIND -> mapActivity.getString(R.string.nautical_widget_wind_label)
            WidgetType.NAUTICAL_SOG -> mapActivity.getString(R.string.nautical_sog)
            WidgetType.NAUTICAL_COG -> mapActivity.getString(R.string.nautical_widget_cog_label)
            WidgetType.NAUTICAL_STW -> mapActivity.getString(R.string.nautical_stw)
            WidgetType.NAUTICAL_HEADING_MAGNETIC -> mapActivity.getString(R.string.nautical_accessibility_heading)
            WidgetType.NAUTICAL_XTE -> mapActivity.getString(R.string.nautical_xte)
            WidgetType.NAUTICAL_AWA -> mapActivity.getString(R.string.nautical_awa)
            WidgetType.NAUTICAL_AWS -> mapActivity.getString(R.string.nautical_aws)
            WidgetType.NAUTICAL_TWA -> mapActivity.getString(R.string.nautical_twa)
            WidgetType.NAUTICAL_TWD -> mapActivity.getString(R.string.nautical_twd)
            WidgetType.NAUTICAL_DEPTH_KEEL -> mapActivity.getString(R.string.nautical_depth_keel)
            WidgetType.NAUTICAL_WATER_TEMP -> mapActivity.getString(R.string.nautical_water_temp)
            WidgetType.NAUTICAL_OUTSIDE_TEMP -> mapActivity.getString(R.string.nautical_outside_temp)
            WidgetType.NAUTICAL_PRESSURE -> mapActivity.getString(R.string.nautical_pressure)
            WidgetType.NAUTICAL_ENGINE_RPM -> mapActivity.getString(R.string.nautical_engine_rpm)
            WidgetType.NAUTICAL_BATTERY_VOLT -> mapActivity.getString(R.string.nautical_battery_volt)
            WidgetType.NAUTICAL_BATTERY_SOC -> mapActivity.getString(R.string.nautical_battery_soc)
            WidgetType.NAUTICAL_FUEL_LEVEL -> mapActivity.getString(R.string.nautical_fuel_level)
            WidgetType.NAUTICAL_TTW -> mapActivity.getString(R.string.nautical_ttw)
            WidgetType.NAUTICAL_DTW -> mapActivity.getString(R.string.nautical_dtw)
            WidgetType.NAUTICAL_ETA -> mapActivity.getString(R.string.nautical_eta)
            WidgetType.NAUTICAL_ROT -> mapActivity.getString(R.string.nautical_rot)
            WidgetType.NAUTICAL_CPA -> mapActivity.getString(R.string.nautical_cpa)
            WidgetType.NAUTICAL_TCPA -> mapActivity.getString(R.string.nautical_tcpa)
            WidgetType.NAUTICAL_RUDDER_ANGLE_TEXT -> mapActivity.getString(R.string.nautical_rudder_angle)
            WidgetType.NAUTICAL_VHF_CHANNEL -> mapActivity.getString(R.string.nautical_vhf_channel)
            else -> widgetType.id
        }

        val integrityLabel = when (integrity) {
            IntegrityState.VALID -> mapActivity.getString(R.string.nautical_integrity_valid)
            IntegrityState.STALE -> mapActivity.getString(R.string.nautical_integrity_stale)
            IntegrityState.ALARM -> mapActivity.getString(R.string.nautical_integrity_alarm)
            IntegrityState.DISCONNECTED -> mapActivity.getString(R.string.nautical_integrity_disconnected)
        }

        val valueText = "$main $sub"
        contentView?.contentDescription = "$label, $valueText, $integrityLabel"
    }

    private fun applyIntegrityStyling(integrity: IntegrityState) {
        val view = getView()
        val bgView = view.findViewById(R.id.widget_bg) ?: view
        val text = textView ?: return

        // Reset strike-through
        text.paint.isStrikeThruText = false

        when (integrity) {
            IntegrityState.VALID -> {
                contentView?.alpha = 1.0f
                if (text.textColor == Color.RED || text.textColor == ContextCompat.getColor(app, R.color.nautical_status_yellow)) {
                    text.textColor = if (nightMode) ContextCompat.getColor(app, R.color.text_color_primary_dark) else ContextCompat.getColor(app, R.color.text_color_primary_light)
                }
                bgView.setBackgroundColor(Color.TRANSPARENT)
            }
            IntegrityState.STALE -> {
                contentView?.alpha = 0.8f
                text.textColor = ContextCompat.getColor(app, R.color.nautical_status_yellow)
                bgView.setBackgroundColor(Color.TRANSPARENT)
            }
            IntegrityState.ALARM -> {
                contentView?.alpha = 1.0f
                text.textColor = Color.RED
                bgView.setBackgroundColor(Color.TRANSPARENT)
            }
            IntegrityState.DISCONNECTED -> {
                contentView?.alpha = 0.5f
                text.textColor = ContextCompat.getColor(app, R.color.text_color_secondary_light)
                bgView.setBackgroundColor(Color.TRANSPARENT)
            }
        }
    }


    private fun isWidgetDataStale(state: MarineState): Boolean {
        val paths = when (widgetType) {
            WidgetType.NAUTICAL_DEPTH -> listOf(SignalKPaths.ENV_DEPTH_BELOW_TRANSDUCER)
            WidgetType.NAUTICAL_WIND -> listOf(SignalKPaths.ENV_WIND_SPEED_TRUE, SignalKPaths.ENV_WIND_ANGLE_TRUE)
            WidgetType.NAUTICAL_SOG -> listOf(SignalKPaths.NAV_SPEED_OVER_GROUND)
            WidgetType.NAUTICAL_COG -> listOf(SignalKPaths.NAV_COURSE_OVER_GROUND)
            WidgetType.NAUTICAL_STW -> listOf(SignalKPaths.NAV_SPEED_THROUGH_WATER)
            WidgetType.NAUTICAL_HEADING_MAGNETIC -> listOf(SignalKPaths.NAV_HEADING_MAG)
            WidgetType.NAUTICAL_XTE -> listOf(SignalKPaths.NAV_XTE, SignalKPaths.NAV_XTE_RHUMB)
            WidgetType.NAUTICAL_AWA -> listOf(SignalKPaths.ENV_WIND_ANGLE_APPARENT)
            WidgetType.NAUTICAL_AWS -> listOf(SignalKPaths.ENV_WIND_SPEED_APPARENT)
            WidgetType.NAUTICAL_BOOST_PRESSURE -> listOf("propulsion.0.boostPressure")
            WidgetType.NAUTICAL_EXHAUST_TEMP -> listOf("propulsion.0.exhaustTemperature")
            WidgetType.NAUTICAL_ALTERNATOR_VOLT -> listOf("propulsion.0.alternatorVoltage")
            WidgetType.NAUTICAL_ALTERNATOR_CURR -> listOf("propulsion.0.alternatorCurrent")
            WidgetType.NAUTICAL_TRANS_GEAR -> listOf("propulsion.0.transmissionGear")
            WidgetType.NAUTICAL_TRANS_PRESS -> listOf("propulsion.0.transmissionPressure")
            WidgetType.NAUTICAL_TRANS_OIL_TEMP -> listOf("propulsion.0.transmissionOilTemperature")
            WidgetType.NAUTICAL_INV_STATE -> listOf("electrical.inverters.0.state")
            WidgetType.NAUTICAL_CHG_STATE -> listOf("electrical.chargers.0.state")
            WidgetType.NAUTICAL_WATERMAKER_RATE -> listOf("propulsion.watermaker.0.rate")
            WidgetType.NAUTICAL_WATERMAKER_TOTAL -> listOf("propulsion.watermaker.0.totalProduction")
            WidgetType.NAUTICAL_WATERMAKER_SALINITY -> listOf("propulsion.watermaker.0.salinity")
            WidgetType.NAUTICAL_REEFS -> listOf(SignalKPaths.SAILS_REEFS)
            else -> emptyList()
        }
        return paths.any { state.stalePaths.contains(it) }
    }
}
