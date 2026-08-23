package net.osmand.plus.views.mapwidgets.widgets

import android.annotation.SuppressLint
import android.view.View
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.plugins.nautical.engine.SignalKPaths
import net.osmand.plus.plugins.nautical.ui.NauticalWidgetHelper
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

    override fun getWidgetName(): String? = null

    override fun getAdditionalWidgetName(): String? = null

    override fun setContentTitle(messageId: Int) {
        super.setContentTitle("")
        widgetName?.visibility = View.GONE
        widgetName?.text = ""
    }

    override fun setContentTitle(text: String?) {
        super.setContentTitle("")
        widgetName?.visibility = View.GONE
        widgetName?.text = ""
    }

    override fun updateWidgetView() {
        super.updateWidgetView()
        widgetName?.visibility = View.GONE
        widgetName?.text = ""
    }

    override fun updateIcon() {
        val iconId = iconId
        if (iconId != 0) {
            val color = settings.applicationMode.getProfileColor(isNightMode)
            setImageDrawable(iconsCache.getPaintedIcon(iconId, color))
        }
    }

    override fun updateColors(textState: net.osmand.plus.views.layers.MapInfoLayer.TextState) {
        super.updateColors(textState)
        updateIcon()
    }

    private var dataJob: kotlinx.coroutines.Job? = null

    override fun setupView(view: View) {
        super.setupView(view)
        widgetName?.visibility = View.GONE
        widgetName?.text = ""

        view.layoutParams = view.layoutParams?.apply {
            width = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            if (this is android.widget.LinearLayout.LayoutParams) {
                weight = 0f
            }
        } ?: android.view.ViewGroup.LayoutParams(
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        view.setPadding(0, view.paddingTop, 0, view.paddingBottom)
        view.minimumWidth = 0

        container?.layoutParams = container?.layoutParams?.apply {
            width = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            if (this is android.widget.LinearLayout.LayoutParams) {
                weight = 0f
            }
        }
        container?.setPadding(0, container?.paddingTop ?: 0, 0, container?.paddingBottom ?: 0)
        container?.minimumWidth = 0

        val textContainer = textView
        if (textContainer != null) {
            for (i in 0 until textContainer.childCount) {
                val tv = textContainer.getChildAt(i) as? android.widget.TextView
                tv?.isSingleLine = true
                tv?.ellipsize = android.text.TextUtils.TruncateAt.END
            }
        }
        val unitsContainer = smallTextView
        if (unitsContainer != null) {
            for (i in 0 until unitsContainer.childCount) {
                val tv = unitsContainer.getChildAt(i) as? android.widget.TextView
                tv?.isSingleLine = true
                tv?.ellipsize = android.text.TextUtils.TruncateAt.END
            }
        }

        view.addOnAttachStateChangeListener(
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    v.post {
                        if (v.isAttachedToWindow && !mapActivity.isFinishing && !mapActivity.isDestroyed) {
                            val broker = NauticalPlugin.engine?.dataBroker
                            if (broker != null) {
                                dataJob?.cancel()
                                dataJob = mapActivity.lifecycleScope.launch {
                                    mapActivity.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                                        broker.marineState
                                            .sample(300L)
                                            .collect {
                                                updateInfo(null)
                                                updateWidgetView()
                                                v.invalidate()
                                            }
                                    }
                                }
                            }
                        }
                    }
                }

                override fun onViewDetachedFromWindow(v: View) {
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
                    WidgetType.NAUTICAL_ENGINE_STATE -> toggleEngineState()
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
        val instance = customId?.substringAfterLast("#") ?: "0"
        val isStarted = state.engines[instance]?.state?.lowercase(Locale.US) == "started"
        autopilot.setEngineState(instance, !isStarted)
    }

    private enum class IntegrityState {
        VALID, STALE, ALARM, DISCONNECTED
    }

    private fun getIntegrityState(state: MarineState?): IntegrityState {
        if (state == null) {
            return IntegrityState.VALID
        }

        val path = getSignalKPath()
        val timestamp = state.timestamps[path]
        val now = TemporalUtils.now()

        if (timestamp != null && timestamp > 0L) {
            val age = (now - timestamp) / 1000.0
            return when {
                state.stalePaths.contains(path) || age > 30.0 -> IntegrityState.STALE
                else -> IntegrityState.VALID
            }
        }

        if (state.connectionStatus == net.osmand.plus.plugins.nautical.engine.ConnectionStatus.DISCONNECTED &&
            settings.NAUTICAL_NMEA_SOURCE.get() == net.osmand.plus.settings.enums.NmeaSource.SIGNALK) {
            return IntegrityState.DISCONNECTED
        }

        return if (state.connectionStatus == net.osmand.plus.plugins.nautical.engine.ConnectionStatus.STALE) IntegrityState.STALE else IntegrityState.VALID
    }

    private fun getSignalKPath(): String {
        val instance = customId?.substringAfterLast("#") ?: "0"
        return when (widgetType) {
            WidgetType.NAUTICAL_DEPTH -> SignalKPaths.ENV_DEPTH_BELOW_TRANSDUCER
            WidgetType.NAUTICAL_WIND -> SignalKPaths.ENV_WIND_SPEED_TRUE
            WidgetType.NAUTICAL_SOG -> SignalKPaths.NAV_SPEED_OVER_GROUND
            WidgetType.NAUTICAL_COG -> SignalKPaths.NAV_COURSE_OVER_GROUND
            WidgetType.NAUTICAL_STW -> SignalKPaths.NAV_SPEED_THROUGH_WATER
            WidgetType.NAUTICAL_HEADING_MAGNETIC -> SignalKPaths.NAV_HEADING_MAG
            WidgetType.NAUTICAL_XTE -> SignalKPaths.NAV_XTE
            WidgetType.NAUTICAL_AWA -> SignalKPaths.ENV_WIND_ANGLE_APPARENT
            WidgetType.NAUTICAL_AWS -> SignalKPaths.ENV_WIND_SPEED_APPARENT
            WidgetType.NAUTICAL_VHF_CHANNEL -> SignalKPaths.COMMUNICATION_VHF_CHANNEL
            WidgetType.NAUTICAL_SALINITY -> SignalKPaths.ENV_WATER_SALINITY
            WidgetType.NAUTICAL_DEW_POINT -> SignalKPaths.ENV_AIR_DEW_POINT
            WidgetType.NAUTICAL_REEFS -> SignalKPaths.SAILS_REEFS
            WidgetType.NAUTICAL_RIGGING_LOAD -> "rigging.loads.0"
            WidgetType.NAUTICAL_MEDIA -> SignalKPaths.MEDIA_TITLE
            WidgetType.NAUTICAL_WIND_SHIFT_WIDGET -> SignalKPaths.PERF_WIND_SHIFT
            WidgetType.NAUTICAL_RACING_TIMER -> SignalKPaths.PERF_RACING_TIMER
            WidgetType.NAUTICAL_WATERMAKER -> "propulsion.watermaker.0.rate"
            WidgetType.NAUTICAL_LOG -> SignalKPaths.NAV_LOG
            WidgetType.NAUTICAL_TRIP_LOG -> SignalKPaths.NAV_TRIP_LOG
            WidgetType.NAUTICAL_ROLL, WidgetType.NAUTICAL_PITCH, WidgetType.NAUTICAL_YAW -> SignalKPaths.NAV_ATTITUDE
            WidgetType.NAUTICAL_DEPTH_KEEL -> SignalKPaths.ENV_DEPTH_BELOW_KEEL
            WidgetType.NAUTICAL_WATER_TEMP -> SignalKPaths.ENV_WATER_TEMP
            WidgetType.NAUTICAL_OUTSIDE_TEMP -> SignalKPaths.ENV_OUTSIDE_TEMP
            WidgetType.NAUTICAL_PRESSURE -> SignalKPaths.ENV_OUTSIDE_PRESSURE
            WidgetType.NAUTICAL_ENGINE_RPM -> "propulsion.$instance.revolutions"
            WidgetType.NAUTICAL_ENGINE_TEMP -> "propulsion.$instance.temperature"
            WidgetType.NAUTICAL_BATTERY_VOLT -> "electrical.batteries.$instance.voltage"
            WidgetType.NAUTICAL_BATTERY_SOC -> "electrical.batteries.$instance.stateOfCharge"
            WidgetType.NAUTICAL_FUEL_LEVEL -> "tanks.fuel.$instance.currentLevel"
            WidgetType.NAUTICAL_FRESH_WATER_LEVEL -> "tanks.freshWater.$instance.currentLevel"
            WidgetType.NAUTICAL_WASTE_WATER_LEVEL -> "tanks.wasteWater.$instance.currentLevel"
            WidgetType.NAUTICAL_POLAR_RATIO -> SignalKPaths.PERF_POLAR_RATIO
            WidgetType.NAUTICAL_ROT -> SignalKPaths.NAV_RATE_OF_TURN
            WidgetType.NAUTICAL_TTW -> SignalKPaths.NAV_TTW
            WidgetType.NAUTICAL_DTW -> SignalKPaths.NAV_DTW
            WidgetType.NAUTICAL_TWA -> SignalKPaths.ENV_WIND_ANGLE_TRUE
            WidgetType.NAUTICAL_TWD -> SignalKPaths.NAV_TWD
            WidgetType.NAUTICAL_OIL_PRESSURE -> "propulsion.$instance.oilPressure"
            WidgetType.NAUTICAL_ENGINE_LOAD -> "propulsion.$instance.engineLoad"
            WidgetType.NAUTICAL_BATTERY_CURRENT -> "electrical.batteries.$instance.current"
            WidgetType.NAUTICAL_SOLAR_CURRENT -> "electrical.solar.$instance.current"
            WidgetType.NAUTICAL_ENGINE_RUNTIME -> "propulsion.$instance.runTime"
            WidgetType.NAUTICAL_ENGINE_COOLANT -> "propulsion.$instance.coolantTemperature"
            WidgetType.NAUTICAL_MAG_VARIATION -> SignalKPaths.NAV_MAG_VARIATION
            WidgetType.NAUTICAL_CPA -> SignalKPaths.NAV_CLOSEST_APPROACH
            WidgetType.NAUTICAL_TCPA -> "navigation.cpa.tcpa"
            WidgetType.NAUTICAL_RUDDER_ANGLE_TEXT -> SignalKPaths.STEERING_RUDDER_ANGLE
            WidgetType.NAUTICAL_POLAR_TARGET_SPEED -> SignalKPaths.PERF_TARGET_SPEED
            WidgetType.NAUTICAL_RANGE -> "navigation.estimatedRange"
            WidgetType.NAUTICAL_SET_DRIFT -> SignalKPaths.NAV_SET_TRUE
            WidgetType.NAUTICAL_MOON_PHASE -> SignalKPaths.NAV_DATETIME_MOON_PHASE
            WidgetType.NAUTICAL_SUNLIGHT_MODE -> "environment.mode"
            WidgetType.NAUTICAL_AC_VOLTAGE -> "electrical.ac.$instance.voltage"
            WidgetType.NAUTICAL_AC_CURRENT -> "electrical.ac.$instance.current"
            WidgetType.NAUTICAL_AC_FREQUENCY -> "electrical.ac.$instance.frequency"
            WidgetType.NAUTICAL_GNSS_QUALITY -> "navigation.gnss.horizontalDilution"
            WidgetType.NAUTICAL_ILLUMINANCE -> SignalKPaths.ENV_OUTSIDE_ILLUMINANCE
            WidgetType.NAUTICAL_AC_SYSTEM -> "electrical.ac.0"
            WidgetType.NAUTICAL_NOTIFICATIONS_LIST -> "notifications"
            else -> customId ?: ""
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
        widgetName?.visibility = View.GONE
        widgetName?.text = ""
        val engine = NauticalPlugin.engine
        if (engine == null) {
            updateIcon()
            val unit = NauticalWidgetHelper.getDefaultUnit(mapActivity, settings, widgetType)
            setText("--", unit)
            contentView?.alpha = 0.5f
            return
        }
        if (dataJob == null && view?.isAttachedToWindow == true) {
            val broker = engine.dataBroker
            dataJob = mapActivity.lifecycleScope.launch(Dispatchers.Main.immediate) {
                broker.marineState.collect {
                    updateInfo(null)
                    updateWidgetView()
                    view?.invalidate()
                }
            }
        }
        val state = engine.getCurrentState()
        val integrity = getIntegrityState(state)

        updateIcon()

        if (integrity == IntegrityState.DISCONNECTED) {
            val unit = NauticalWidgetHelper.getDefaultUnit(mapActivity, settings, widgetType)
            setText("--", unit)
            contentView?.alpha = 0.5f
            return
        }

        contentView?.alpha = if (integrity == IntegrityState.STALE) 0.5f else 1.0f

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
                if (ttw != null && ttw > 0) {
                    val etaMs = System.currentTimeMillis() + (ttw * 1000).toLong()
                    val timeFormat = android.text.format.DateFormat.getTimeFormat(mapActivity)
                    timeFormat.format(Date(etaMs)) to ""
                } else {
                    "--:--" to ""
                }
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
                val loads = if (instance == "0") state.riggingLoads.values else state.riggingLoads.filterKeys { it.startsWith(instance) }.values
                if (loads.isNotEmpty()) {
                    val max = loads.max()
                    val unit = if (loads.size > 1) "kgf (${loads.size})" else "kgf"
                    String.format(Locale.US, "%.0f", max) to unit
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
            WidgetType.NAUTICAL_REEFS -> (state.reefs?.toString() ?: mapActivity.getString(R.string.n_a)) to ""
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
                                (widgetType == WidgetType.NAUTICAL_XTE)
            val msg = if (safetyCritical) mapActivity.getString(R.string.nautical_timeout) else mapActivity.getString(R.string.n_a)
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
}
