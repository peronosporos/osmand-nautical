package net.osmand.plus.views.mapwidgets.widgets

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Typeface
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
import net.osmand.plus.utils.ColorUtilities
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

    override fun updateWidgetView() {
        super.updateWidgetView()
        widgetName?.visibility = View.GONE
    }

    override fun updateIcon() {
        val iconId = iconId
        if (iconId != 0) {
            if ((widgetType == WidgetType.NAUTICAL_DEPTH) || (widgetType == WidgetType.NAUTICAL_WIND)) {
                setImageDrawable(iconId)
            } else {
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
    }

    private var isPulseActive = false

    private val marineStateListener: (MarineState) -> Unit = {
        mapActivity.runOnUiThread {
            updateInfo(null)
        }
    }

    private val pulseListener: (Boolean) -> Unit = { pulse ->
        if (isPulseActive != pulse) {
            isPulseActive = pulse
            mapActivity.runOnUiThread {
                updateInfo(null)
            }
        }
    }

    private var pulseJob: kotlinx.coroutines.Job? = null

    private var dataJob: kotlinx.coroutines.Job? = null

    override fun setupView(view: View) {
        super.setupView(view)

        view.addOnAttachStateChangeListener(
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    val engine = NauticalPlugin.engine
                    engine?.registerListener(marineStateListener)
                    
                    pulseJob?.cancel()
                    pulseJob = engine?.pulseFlow?.onEach { pulseListener(it) }?.launchIn(mapActivity.lifecycleScope)
                    
                    val broker = engine?.dataBroker
                    val filterService = net.osmand.plus.plugins.nautical.di.SailingDependencyContainer.environmentalFilterService
                    if (broker != null) {
                        dataJob?.cancel()
                        dataJob = when (widgetType) {
                            WidgetType.NAUTICAL_DEPTH_KEEL -> broker.depthBelowKeel.onEach { updateInfo(null) }.launchIn(mapActivity.lifecycleScope)
                            WidgetType.NAUTICAL_BATTERY_SOC -> broker.batterySoc.onEach { updateInfo(null) }.launchIn(mapActivity.lifecycleScope)
                            WidgetType.NAUTICAL_AWS -> {
                                // Prefer motion-corrected wind speed if available
                                filterService?.correctedWindSpeedApparent?.onEach { updateInfo(null) }?.launchIn(mapActivity.lifecycleScope)
                                    ?: broker.windSpeedApparent.onEach { updateInfo(null) }.launchIn(mapActivity.lifecycleScope)
                            }
                            WidgetType.NAUTICAL_AWA -> {
                                // Prefer motion-corrected wind angle
                                filterService?.correctedWindAngleApparent?.onEach { updateInfo(null) }?.launchIn(mapActivity.lifecycleScope)
                                    ?: broker.windAngleApparent.onEach { updateInfo(null) }.launchIn(mapActivity.lifecycleScope)
                            }
                            WidgetType.NAUTICAL_RIGGING_LOADS -> broker.riggingLoads.onEach { updateInfo(null) }.launchIn(mapActivity.lifecycleScope)
                            WidgetType.NAUTICAL_AC_SYSTEM -> broker.acSystems.onEach { updateInfo(null) }.launchIn(mapActivity.lifecycleScope)
                            WidgetType.NAUTICAL_MAG_VARIATION -> broker.magneticVariation.onEach { updateInfo(null) }.launchIn(mapActivity.lifecycleScope)
                            WidgetType.NAUTICAL_YAW -> broker.yaw.onEach { updateInfo(null) }.launchIn(mapActivity.lifecycleScope)
                            WidgetType.NAUTICAL_CPA -> broker.cpa.onEach { updateInfo(null) }.launchIn(mapActivity.lifecycleScope)
                            WidgetType.NAUTICAL_TCPA -> broker.tcpa.onEach { updateInfo(null) }.launchIn(mapActivity.lifecycleScope)
                            else -> null
                        }
                    }
                }

                override fun onViewDetachedFromWindow(v: View) {
                    NauticalPlugin.engine?.unregisterListener(marineStateListener)
                    pulseJob?.cancel()
                    pulseJob = null
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
            WidgetType.NAUTICAL_DEPTH -> "environment.depth.belowTransducer"
            WidgetType.NAUTICAL_WIND -> "environment.wind.speedTrue"
            WidgetType.NAUTICAL_SOG -> "navigation.speedOverGround"
            WidgetType.NAUTICAL_COG -> "navigation.courseOverGroundTrue"
            WidgetType.NAUTICAL_STW -> "navigation.speedThroughWater"
            WidgetType.NAUTICAL_HEADING_MAGNETIC -> "navigation.headingMagnetic"
            WidgetType.NAUTICAL_XTE -> "navigation.crossTrackError"
            WidgetType.NAUTICAL_AWA -> "environment.wind.angleApparent"
            WidgetType.NAUTICAL_AWS -> "environment.wind.speedApparent"
            WidgetType.NAUTICAL_HUMIDITY -> "environment.outside.relativeHumidity"
            WidgetType.NAUTICAL_AC_VOLTAGE -> "electrical.ac.voltage"
            WidgetType.NAUTICAL_AC_CURRENT -> "electrical.ac.current"
            WidgetType.NAUTICAL_AC_FREQUENCY -> "electrical.ac.frequency"
            WidgetType.NAUTICAL_VHF_CHANNEL -> "communication.vhf.channel"
            WidgetType.NAUTICAL_GNSS_QUALITY -> "navigation.gnss.horizontalDilution"
            WidgetType.NAUTICAL_SALINITY -> "environment.water.salinity"
            WidgetType.NAUTICAL_DEW_POINT -> "environment.air.dewPoint"
            WidgetType.NAUTICAL_ILLUMINANCE -> "environment.outside.illuminance"
            WidgetType.NAUTICAL_RIGGING_LOAD -> "rigging.loads"
            WidgetType.NAUTICAL_MEDIA -> "entertainment.device.fusion.title"
            WidgetType.NAUTICAL_WIND_SHIFT_WIDGET -> "performance.windShift"
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
            WidgetType.NAUTICAL_WATERMAKER_RATE -> "propulsion.watermaker.0.rate"
            WidgetType.NAUTICAL_WATERMAKER_TOTAL -> "propulsion.watermaker.0.totalProduction"
            WidgetType.NAUTICAL_WATERMAKER_SALINITY -> "propulsion.watermaker.0.salinity"
            WidgetType.NAUTICAL_REEFS -> "sails.reefs"
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

        val (main, formattedSub) = when (widgetType) {
            WidgetType.NAUTICAL_DEPTH -> {
                val depth = state.depthBelowTransducer
                val (v, u) = SignalKUnitConverter.formatValue(mapActivity, settings, depth, "depth")
                val trend = getTrend(depth, lastDepth).also { lastDepth = depth }
                v + trend to u
            }
            WidgetType.NAUTICAL_WIND -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.windSpeedTrue, "speed")
            WidgetType.NAUTICAL_VMG -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.velocityMadeGood, "speed")
            WidgetType.NAUTICAL_COG -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.courseOverGroundTrue, "course")
            WidgetType.NAUTICAL_SOG -> {
                val trend = getTrend(state.speedOverGround, lastSog).also { lastSog =
                    state.speedOverGround
                }
                val (v, u) = SignalKUnitConverter.formatValue(mapActivity, settings,
                    state.speedOverGround, "speed")
                if (v == mapActivity.getString(R.string.n_a)) v to u else "$v$trend" to u
            }
            WidgetType.NAUTICAL_STW -> {
                val speed = if (state.isStwUnreliable) state.speedOverGround else state.speedThroughWater
                val trend = getTrend(speed, lastStw).also { lastStw = speed }
                val (v, u) = SignalKUnitConverter.formatValue(mapActivity, settings,
                    speed, "speed")
                val fallbackSuffix = if (state.isStwUnreliable) " (COG)" else ""
                if (v == mapActivity.getString(R.string.n_a)) v to u else "$v$trend$fallbackSuffix" to u
            }
            WidgetType.NAUTICAL_HEADING_MAGNETIC -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.headingMagnetic, "heading")
            WidgetType.NAUTICAL_LOG -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.log, "log")
            WidgetType.NAUTICAL_TRIP_LOG -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.tripLog, "log")
            WidgetType.NAUTICAL_ROLL -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.roll, "roll")
            WidgetType.NAUTICAL_PITCH -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.pitch, "pitch")
            WidgetType.NAUTICAL_DEPTH_KEEL -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.depthBelowKeel, "depth")
            WidgetType.NAUTICAL_WATER_TEMP -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.waterTemperature, "temperature")
            WidgetType.NAUTICAL_OUTSIDE_TEMP -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.outsideTemperature, "temperature")
            WidgetType.NAUTICAL_PRESSURE -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.outsidePressure, "pressure")
            WidgetType.NAUTICAL_ENGINE_RPM -> SignalKUnitConverter.formatValue(mapActivity, settings, state.engines["0"]?.revolutions, "revolutions")
            WidgetType.NAUTICAL_ENGINE_TEMP -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.engines["0"]?.temperature, "temperature")
            WidgetType.NAUTICAL_BATTERY_VOLT -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.batteries["0"]?.voltage, "voltage")
            WidgetType.NAUTICAL_BATTERY_SOC -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.batteries["0"]?.stateOfCharge, "stateOfCharge")
            WidgetType.NAUTICAL_FUEL_LEVEL -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.tanks["fuel.0"]?.currentLevel, "currentLevel")
            WidgetType.NAUTICAL_FRESH_WATER_LEVEL -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.tanks["freshWater.0"]?.currentLevel, "currentLevel")
            WidgetType.NAUTICAL_WASTE_WATER_LEVEL -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.tanks["wasteWater.0"]?.currentLevel, "currentLevel")
            WidgetType.NAUTICAL_POLAR_RATIO -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.polarSpeedRatio, "polarSpeedRatio")
            WidgetType.NAUTICAL_ROT -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.rateOfTurn, "angle")
            WidgetType.NAUTICAL_XTE -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.crossTrackError, "distance")
            WidgetType.NAUTICAL_TTW -> {
                val ttw = state.timeToWaypoint
                val unit = mapActivity.getString(R.string.nautical_unit_hour_short)
                if (ttw != null) {
                    val h = (ttw / 3600).toInt()
                    val m = ((ttw % 3600) / 60).toInt()
                    String.format(Locale.US, "%02d:%02d", h, m) to unit
                } else mapActivity.getString(R.string.n_a) to unit
            }
            WidgetType.NAUTICAL_DTW -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.distanceToWaypoint, "distance")
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
                state.windDirectionApparent, "angle")
            WidgetType.NAUTICAL_AWS -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.windSpeedApparent, "speed")
            WidgetType.NAUTICAL_TWA -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.trueWindAngle, "angle")
            WidgetType.NAUTICAL_TWD -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.windDirectionTrue, "angle")
            WidgetType.NAUTICAL_OIL_PRESSURE -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.engines["0"]?.oilPressure, "oilPressure")
            WidgetType.NAUTICAL_ENGINE_LOAD -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.engines["0"]?.load, "engineLoad")
            WidgetType.NAUTICAL_BATTERY_CURRENT -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.batteries["0"]?.current, "current")
            WidgetType.NAUTICAL_SOLAR_CURRENT -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.customValues["electrical.solar.0.current"], "current")
            WidgetType.NAUTICAL_ENGINE_RUNTIME -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.engines["0"]?.runTime, "distance")
            WidgetType.NAUTICAL_ENGINE_COOLANT -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.engines["0"]?.coolantTemperature, "temperature")
            WidgetType.NAUTICAL_ENGINE_STATE -> (state.engines["0"]?.state?.uppercase(Locale.US) ?: mapActivity.getString(R.string.n_a)) to ""
            WidgetType.NAUTICAL_MAG_VARIATION -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.magneticVariation, "angle")
            WidgetType.NAUTICAL_YAW -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.yaw, "angle")
            WidgetType.NAUTICAL_CPA -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.cpa, "distance")
            WidgetType.NAUTICAL_TCPA -> {
                val tcpa = state.tcpa
                if (tcpa != null) {
                    val m = (tcpa / 60).toInt()
                    val s = (tcpa % 60).toInt()
                    String.format(Locale.US, "%02d:%02d", m, s) to "min"
                } else mapActivity.getString(R.string.n_a) to ""
            }
            WidgetType.NAUTICAL_RUDDER_ANGLE_TEXT -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.rudderAngle, "angle")
            WidgetType.NAUTICAL_POLAR_TARGET_SPEED -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.polarTargetSpeed, "speed")
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
                    val (sV, _) = SignalKUnitConverter.formatValue(mapActivity, settings, set, "angle")
                    val (dV, dU) = SignalKUnitConverter.formatValue(mapActivity, settings, drift, "speed")
                    "$sV/$dV" to dU
                } else mapActivity.getString(R.string.n_a) to mapActivity.getString(R.string.nautical_unit_knots)
            }
            WidgetType.NAUTICAL_HUMIDITY -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.outsideHumidity, "humidity")
            WidgetType.NAUTICAL_MOON_PHASE -> {
                val phase = state.moonPhase
                if (phase != null) {
                    String.format(Locale.US, "%.0f%%", phase * 100.0) to ""
                } else mapActivity.getString(R.string.n_a) to ""
            }
            WidgetType.NAUTICAL_SUNLIGHT_MODE -> (state.sunlightMode?.uppercase(Locale.US) ?: mapActivity.getString(R.string.n_a)) to ""
            WidgetType.NAUTICAL_AC_VOLTAGE -> SignalKUnitConverter.formatValue(
                mapActivity, settings, state.inverters["0"]?.acVoltage, "voltage",
            )
            WidgetType.NAUTICAL_AC_CURRENT -> SignalKUnitConverter.formatValue(
                mapActivity, settings, state.inverters["0"]?.acCurrent, "current",
            )
            WidgetType.NAUTICAL_AC_FREQUENCY -> SignalKUnitConverter.formatValue(
                mapActivity, settings, state.customValues["electrical.ac.0.frequency"], "frequency",
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
                state.waterSalinity, "salinity")
            WidgetType.NAUTICAL_DEW_POINT -> SignalKUnitConverter.formatValue(mapActivity, settings,
                state.airDewPoint, "temperature")
            WidgetType.NAUTICAL_ILLUMINANCE -> {
                val illum = state.outsideIlluminance
                if (illum != null) {
                    String.format(Locale.US, "%.0f", illum) to "lux"
                } else mapActivity.getString(R.string.n_a) to "lux"
            }
            WidgetType.NAUTICAL_RIGGING_LOAD -> {
                val load = state.riggingLoads.values.maxOrNull()
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
                    val m = (timer / 60).toInt()
                    val s = (timer % 60).toInt()
                    String.format(Locale.US, "%02d:%02d", m, s) to ""
                } else mapActivity.getString(R.string.n_a) to ""
            }
            WidgetType.NAUTICAL_WATERMAKER -> {
                val watermaker = state.watermakers.values.firstOrNull()
                if (watermaker != null) {
                    val rate = watermaker.rate ?: 0.0
                    String.format(Locale.US, "%.1f", rate) to "L/h"
                } else mapActivity.getString(R.string.n_a) to "L/h"
            }
            WidgetType.NAUTICAL_BOOST_PRESSURE -> SignalKUnitConverter.formatValue(mapActivity, settings, state.engines["0"]?.boostPressure, "pressure")
            WidgetType.NAUTICAL_EXHAUST_TEMP -> SignalKUnitConverter.formatValue(mapActivity, settings, state.engines["0"]?.exhaustTemperature, "temperature")
            WidgetType.NAUTICAL_ALTERNATOR_VOLT -> SignalKUnitConverter.formatValue(mapActivity, settings, state.engines["0"]?.alternatorVoltage, "voltage")
            WidgetType.NAUTICAL_ALTERNATOR_CURR -> SignalKUnitConverter.formatValue(mapActivity, settings, state.engines["0"]?.alternatorCurrent, "current")
            WidgetType.NAUTICAL_TRANS_GEAR -> (state.engines["0"]?.transmissionGear ?: mapActivity.getString(R.string.n_a)) to ""
            WidgetType.NAUTICAL_TRANS_PRESS -> SignalKUnitConverter.formatValue(mapActivity, settings, state.engines["0"]?.transmissionPressure, "pressure")
            WidgetType.NAUTICAL_TRANS_OIL_TEMP -> SignalKUnitConverter.formatValue(mapActivity, settings, state.engines["0"]?.transmissionOilTemperature, "temperature")
            WidgetType.NAUTICAL_INV_STATE -> (state.inverters["0"]?.state ?: mapActivity.getString(R.string.n_a)) to ""
            WidgetType.NAUTICAL_CHG_STATE -> (state.chargers["0"]?.state ?: mapActivity.getString(R.string.n_a)) to ""
            WidgetType.NAUTICAL_WATERMAKER_RATE -> SignalKUnitConverter.formatValue(mapActivity, settings, state.watermakers["0"]?.rate, "watermakerRate")
            WidgetType.NAUTICAL_WATERMAKER_TOTAL -> SignalKUnitConverter.formatValue(mapActivity, settings, state.watermakers["0"]?.totalProduction, "volume")
            WidgetType.NAUTICAL_WATERMAKER_SALINITY -> SignalKUnitConverter.formatValue(mapActivity, settings, state.watermakers["0"]?.salinity, "salinity")
            WidgetType.NAUTICAL_REEFS -> (state.reefs?.toString() ?: mapActivity.getString(R.string.n_a)) to ""
            WidgetType.NAUTICAL_RIGGING_LOADS -> {
                val loads = state.riggingLoads.values
                if (loads.isNotEmpty()) {
                    val max = loads.max()
                    String.format(Locale.US, "%.0f", max) to "kgf (${loads.size})"
                } else mapActivity.getString(R.string.n_a) to "kgf"
            }
            WidgetType.NAUTICAL_AC_SYSTEM -> {
                val inv = state.inverters["0"]
                val chg = state.chargers["0"]
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
        
        val sub = formattedSub

        if (integrity == lastIntegrity && main == lastFormattedMain && sub == lastFormattedSub && !isPulseActive) {
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
            else -> widgetType.id
        }

        val integrityLabel = when (integrity) {
            IntegrityState.VALID -> mapActivity.getString(R.string.nautical_integrity_valid)
            IntegrityState.STALE -> mapActivity.getString(R.string.nautical_integrity_stale)
            IntegrityState.ALARM -> mapActivity.getString(R.string.nautical_integrity_alarm)
            IntegrityState.DISCONNECTED -> mapActivity.getString(R.string.nautical_integrity_disconnected)
        }

        val valueText = if (integrity == IntegrityState.ALARM) "Alarm" else "$main $sub"
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
                bgView.background = null
                text.textColor = if (nightMode) ContextCompat.getColor(app, R.color.text_color_primary_dark) else ContextCompat.getColor(app, R.color.text_color_primary_light)
            }
            IntegrityState.STALE -> {
                contentView?.alpha = 1.0f
                text.textColor = ContextCompat.getColor(app, R.color.nautical_status_yellow)
                bgView.setBackgroundColor(ColorUtilities.getColorWithAlpha(ContextCompat.getColor(app, R.color.nautical_status_yellow), 0.1f))
            }
            IntegrityState.ALARM -> {
                contentView?.alpha = 1.0f
                // OpenBridge: High Contrast Emergency Red (Synchronized Flash)
                if (isPulseActive) {
                    text.textColor = Color.WHITE
                    bgView.setBackgroundColor(Color.RED)
                } else {
                    text.textColor = Color.RED
                    bgView.setBackgroundColor(Color.TRANSPARENT)
                }
                text.paint.isStrikeThruText = true
                text.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            IntegrityState.DISCONNECTED -> {
                contentView?.alpha = 0.5f
                bgView.background = null
                text.textColor = ContextCompat.getColor(app, R.color.text_color_secondary_light)
            }
        }
    }


    private fun isWidgetDataStale(state: MarineState): Boolean {
        val paths = when (widgetType) {
            WidgetType.NAUTICAL_DEPTH -> listOf("environment.depth.belowTransducer")
            WidgetType.NAUTICAL_WIND -> listOf("environment.wind.speedTrue", "environment.wind.angleTrue")
            WidgetType.NAUTICAL_SOG -> listOf("navigation.speedOverGround")
            WidgetType.NAUTICAL_COG -> listOf("navigation.courseOverGroundTrue")
            WidgetType.NAUTICAL_STW -> listOf("navigation.speedThroughWater")
            WidgetType.NAUTICAL_HEADING_MAGNETIC -> listOf("navigation.headingMagnetic")
            WidgetType.NAUTICAL_XTE -> listOf("navigation.crossTrackError", "navigation.courseRhumbline.crossTrackError")
            WidgetType.NAUTICAL_AWA -> listOf("environment.wind.angleApparent")
            WidgetType.NAUTICAL_AWS -> listOf("environment.wind.speedApparent")
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
            WidgetType.NAUTICAL_REEFS -> listOf("sails.reefs")
            else -> emptyList()
        }
        return paths.any { state.stalePaths.contains(it) }
    }

    private var lastSog: Double? = null
    private var lastStw: Double? = null
    private var lastDepth: Double? = null

    private fun getTrend(current: Double?, last: Double?): String {
        return when {
            (current == null) || (last == null) || (kotlin.math.abs(current - last) < 0.01) -> ""
            current > last -> mapActivity.getString(R.string.nautical_trend_up)
            else -> mapActivity.getString(R.string.nautical_trend_down)
        }
    }
}
