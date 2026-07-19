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
import net.osmand.shared.units.SpeedUnits
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
        if (now - lastUpdateTime > 200) { // Throttle 5Hz
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
            }
        )
    }

    override fun getOnClickListener(): View.OnClickListener {
        return View.OnClickListener {
            if (!mapActivity.isFinishing) {
                val dialog = NauticalDataBottomSheet.newInstance(this.widgetType)
                dialog.show(mapActivity.supportFragmentManager, "nautical_graph")
            }
        }
    }

    @SuppressLint("DefaultLocale")
    override fun updateSimpleWidgetInfo(drawSettings: OsmandMapLayer.DrawSettings?) {
        val engine = NauticalPlugin.engine ?: return
        val state = engine.getCurrentState()

        updateIcon()

        if (state == null || state.connectionStatus == net.osmand.plus.plugins.nautical.engine.ConnectionStatus.DISCONNECTED) {
            setText(mapActivity.getString(R.string.nautical_status_off), mapActivity.getString(R.string.n_a))
        } else if (state.connectionStatus == net.osmand.plus.plugins.nautical.engine.ConnectionStatus.STALE) {
            setText(mapActivity.getString(R.string.nautical_connection_stale), "")
        } else {
            when (widgetType) {
                WidgetType.NAUTICAL_DEPTH -> handleDepthUpdate(state)
                WidgetType.NAUTICAL_WIND -> handleWindUpdate(state)
                WidgetType.NAUTICAL_VMG -> handleVmgUpdate(state)
                WidgetType.NAUTICAL_COG -> handleCogUpdate(state)
                WidgetType.NAUTICAL_SOG -> handleSogUpdate(state)
                WidgetType.NAUTICAL_STW -> handleStwUpdate(state)
                WidgetType.NAUTICAL_SET_DRIFT -> handleSetDriftUpdate(state)
                WidgetType.NAUTICAL_HEADING_MAGNETIC -> handleHeadingMagneticUpdate(state)
                WidgetType.NAUTICAL_LOG -> handleLogUpdate(state)
                WidgetType.NAUTICAL_TRIP_LOG -> handleTripLogUpdate(state)
                WidgetType.NAUTICAL_ROLL -> handleRollUpdate(state)
                WidgetType.NAUTICAL_PITCH -> handlePitchUpdate(state)
                WidgetType.NAUTICAL_DEPTH_KEEL -> handleDepthKeelUpdate(state)
                WidgetType.NAUTICAL_WATER_TEMP -> handleWaterTempUpdate(state)
                WidgetType.NAUTICAL_OUTSIDE_TEMP -> handleOutsideTempUpdate(state)
                WidgetType.NAUTICAL_PRESSURE -> handlePressureUpdate(state)
                WidgetType.NAUTICAL_ENGINE_RPM -> handleRpmUpdate(state)
                WidgetType.NAUTICAL_ENGINE_TEMP -> handleEngineTempUpdate(state)
                WidgetType.NAUTICAL_BATTERY_VOLT -> handleBatteryVoltUpdate(state)
                WidgetType.NAUTICAL_BATTERY_SOC -> handleBatterySocUpdate(state)
                WidgetType.NAUTICAL_FUEL_LEVEL -> handleFuelLevelUpdate(state)
                WidgetType.NAUTICAL_FRESH_WATER_LEVEL -> handleFreshWaterLevelUpdate(state)
                WidgetType.NAUTICAL_WASTE_WATER_LEVEL -> handleWasteWaterLevelUpdate(state)
                WidgetType.NAUTICAL_POLAR_RATIO -> handlePolarRatioUpdate(state)
                WidgetType.NAUTICAL_ROT -> handleRotUpdate(state)
                WidgetType.NAUTICAL_XTE -> handleXteUpdate(state)
                WidgetType.NAUTICAL_TTW -> handleTtwUpdate(state)
                WidgetType.NAUTICAL_DTW -> handleDtwUpdate(state)
                WidgetType.NAUTICAL_ETA -> handleEtaUpdate(state)
                WidgetType.NAUTICAL_AWA -> handleAwaUpdate(state)
                WidgetType.NAUTICAL_AWS -> handleAwsUpdate(state)
                WidgetType.NAUTICAL_TWA -> handleTwaUpdate(state)
                WidgetType.NAUTICAL_TWD -> handleTwdUpdate(state)
                WidgetType.NAUTICAL_OIL_PRESSURE -> handleOilPressureUpdate(state)
                WidgetType.NAUTICAL_ENGINE_LOAD -> handleEngineLoadUpdate(state)
                WidgetType.NAUTICAL_BATTERY_CURRENT -> handleBatteryCurrentUpdate(state)
                WidgetType.NAUTICAL_SOLAR_CURRENT -> handleSolarCurrentUpdate(state)
                WidgetType.NAUTICAL_ENGINE_RUNTIME -> handleEngineRuntimeUpdate(state)
                else -> {}
            }
        }
    }

    private fun handleOilPressureUpdate(state: MarineState) {
        val press = state.engineOilPressure
        val unit = mapActivity.getString(R.string.nautical_unit_bar)
        if (press != null) {
            setText(String.format(Locale.US, "%.1f", press / 100000.0), unit)
        } else {
            setText(mapActivity.getString(R.string.n_a), unit)
        }
    }

    private fun handleEngineLoadUpdate(state: MarineState) {
        val load = state.engineLoad
        val unit = mapActivity.getString(R.string.nautical_unit_percent)
        if (load != null) {
            setText(String.format(Locale.US, "%.0f", load * 100.0), unit)
        } else {
            setText(mapActivity.getString(R.string.n_a), unit)
        }
    }

    private fun handleBatteryCurrentUpdate(state: MarineState) {
        val curr = state.batteryCurrent
        val unit = mapActivity.getString(R.string.nautical_unit_ampere)
        if (curr != null) {
            setText(String.format(Locale.US, "%.1f", curr), unit)
        } else {
            setText(mapActivity.getString(R.string.n_a), unit)
        }
    }

    private fun handleSolarCurrentUpdate(state: MarineState) {
        val curr = state.solarCurrent
        val unit = mapActivity.getString(R.string.nautical_unit_ampere)
        if (curr != null) {
            setText(String.format(Locale.US, "%.1f", curr), unit)
        } else {
            setText(mapActivity.getString(R.string.n_a), unit)
        }
    }

    private fun handleEngineRuntimeUpdate(state: MarineState) {
        val t = state.engineRunTime
        val unit = mapActivity.getString(R.string.nautical_unit_hour_short)
        if (t != null) {
            setText(String.format(Locale.US, "%.1f", t / 3600.0), unit)
        } else {
            setText(mapActivity.getString(R.string.n_a), unit)
        }
    }

    private fun handleRotUpdate(state: MarineState) {
        val rot = state.rateOfTurn
        val unit = mapActivity.getString(R.string.nautical_unit_rot_short)
        if (rot != null) {
            setText(String.format(Locale.US, "%.1f", Math.toDegrees(rot) * 60.0), unit)
        } else {
            setText(mapActivity.getString(R.string.n_a), unit)
        }
    }

    private fun handleXteUpdate(state: MarineState) {
        val xte = state.crossTrackError
        val unit = mapActivity.getString(R.string.nautical_unit_nm)
        if (xte != null) {
            val nm = xte / 1852.0
            setText(String.format(Locale.US, "%.3f", nm), unit)
        } else {
            setText(mapActivity.getString(R.string.n_a), unit)
        }
    }

    private fun handleTtwUpdate(state: MarineState) {
        val ttw = state.timeToWaypoint
        val unit = mapActivity.getString(R.string.nautical_unit_hour_short)
        if (ttw != null) {
            val h = (ttw / 3600).toInt()
            val m = ((ttw % 3600) / 60).toInt()
            setText(String.format(Locale.US, "%02d:%02d", h, m), unit)
        } else {
            setText(mapActivity.getString(R.string.n_a), unit)
        }
    }

    private fun handleDtwUpdate(state: MarineState) {
        val dtw = state.distanceToWaypoint
        val unit = mapActivity.getString(R.string.nautical_unit_nm)
        if (dtw != null) {
            val nm = dtw / 1852.0
            setText(String.format(Locale.US, "%.2f", nm), unit)
        } else {
            setText(mapActivity.getString(R.string.n_a), unit)
        }
    }

    private fun handleEtaUpdate(state: MarineState) {
        val ttw = state.timeToWaypoint
        if (ttw != null) {
            val etaMs = System.currentTimeMillis() + (ttw * 1000).toLong()
            val sdf = java.text.SimpleDateFormat("HH:mm", Locale.US)
            setText(sdf.format(Date(etaMs)), "")
        } else {
            setText(mapActivity.getString(R.string.n_a), "")
        }
    }

    private fun handleAwaUpdate(state: MarineState) {
        val awa = state.windDirectionApparent
        val unit = mapActivity.getString(R.string.nautical_unit_apparent_short)
        if (awa != null) {
            setText(String.format(Locale.US, "%.0f°", Math.toDegrees(awa)), unit)
        } else {
            setText(mapActivity.getString(R.string.n_a), unit)
        }
    }

    private fun handleAwsUpdate(state: MarineState) {
        val aws = state.windSpeedApparent
        val unit = mapActivity.getString(R.string.nautical_unit_knots)
        if (aws != null) {
            val knots = aws * SpeedUnits.KNOTS.conversionCoefficient
            setText(String.format(Locale.US, "%.1f", knots), unit)
        } else {
            setText(mapActivity.getString(R.string.n_a), unit)
        }
    }

    private fun handleTwaUpdate(state: MarineState) {
        val twa = state.trueWindAngle
        val unit = mapActivity.getString(R.string.nautical_unit_true_short)
        if (twa != null) {
            setText(String.format(Locale.US, "%.0f°", Math.toDegrees(twa)), unit)
        } else {
            setText(mapActivity.getString(R.string.n_a), unit)
        }
    }

    private fun handleTwdUpdate(state: MarineState) {
        var twd = state.windDirectionTrue
        val unit = mapActivity.getString(R.string.nautical_unit_true_short)
        if (twd == null) {
            val hdg = state.headingTrue
            val twa = state.trueWindAngle
            if (hdg != null && twa != null) {
                twd = (hdg + twa + 2 * Math.PI) % (2 * Math.PI)
            }
        }
        if (twd != null) {
            setText(String.format(Locale.US, "%03.0f°", Math.toDegrees(twd)), unit)
        } else {
            setText(mapActivity.getString(R.string.n_a), unit)
        }
    }

    private fun handleHeadingMagneticUpdate(state: MarineState) {
        val hdg = state.headingMagnetic
        val unit = mapActivity.getString(R.string.nautical_unit_mag_short)
        if (hdg != null) {
            setText(String.format(Locale.US, "%03.0f°", Math.toDegrees(hdg)), unit)
        } else {
            setText(mapActivity.getString(R.string.n_a), unit)
        }
    }

    private fun handleLogUpdate(state: MarineState) {
        val log = state.log
        val unit = mapActivity.getString(R.string.nautical_unit_nm)
        if (log != null) {
            val nm = log / 1852.0
            setText(String.format(Locale.US, "%.1f", nm), unit)
        } else {
            setText(mapActivity.getString(R.string.n_a), unit)
        }
    }

    private fun handleTripLogUpdate(state: MarineState) {
        val log = state.tripLog
        val unit = mapActivity.getString(R.string.nautical_unit_nm)
        if (log != null) {
            val nm = log / 1852.0
            setText(String.format(Locale.US, "%.1f", nm), unit)
        } else {
            setText(mapActivity.getString(R.string.n_a), unit)
        }
    }

    private fun handleRollUpdate(state: MarineState) {
        val roll = state.roll
        if (roll != null) {
            setText(String.format(Locale.US, "%.1f°", Math.toDegrees(roll)), "")
        } else {
            setText(mapActivity.getString(R.string.n_a), "")
        }
    }

    private fun handlePitchUpdate(state: MarineState) {
        val pitch = state.pitch
        if (pitch != null) {
            setText(String.format(Locale.US, "%.1f°", Math.toDegrees(pitch)), "")
        } else {
            setText(mapActivity.getString(R.string.n_a), "")
        }
    }

    private fun handleDepthKeelUpdate(state: MarineState) {
        val depth = state.depthBelowKeel
        val unit = mapActivity.getString(R.string.nautical_unit_meters)
        if (depth != null) {
            setText(String.format(Locale.US, "%.1f", depth), unit)
        } else {
            setText(mapActivity.getString(R.string.n_a), unit)
        }
    }

    private fun handleWaterTempUpdate(state: MarineState) {
        val temp = state.waterTemperature
        val unit = mapActivity.getString(R.string.nautical_unit_celsius)
        if (temp != null) {
            setText(String.format(Locale.US, "%.1f", temp - 273.15), unit)
        } else {
            setText(mapActivity.getString(R.string.n_a), unit)
        }
    }

    private fun handleOutsideTempUpdate(state: MarineState) {
        val temp = state.outsideTemperature
        val unit = mapActivity.getString(R.string.nautical_unit_celsius)
        if (temp != null) {
            setText(String.format(Locale.US, "%.1f", temp - 273.15), unit)
        } else {
            setText(mapActivity.getString(R.string.n_a), unit)
        }
    }

    private fun handlePressureUpdate(state: MarineState) {
        val press = state.outsidePressure
        val unit = mapActivity.getString(R.string.nautical_unit_hpa)
        if (press != null) {
            setText(String.format(Locale.US, "%.0f", press / 100.0), unit)
        } else {
            setText(mapActivity.getString(R.string.n_a), unit)
        }
    }

    private fun handleRpmUpdate(state: MarineState) {
        val rpm = state.engineRpm
        val unit = mapActivity.getString(R.string.nautical_unit_rpm)
        if (rpm != null) {
            setText(String.format(Locale.US, "%.0f", rpm), unit)
        } else {
            setText(mapActivity.getString(R.string.n_a), unit)
        }
    }

    private fun handleEngineTempUpdate(state: MarineState) {
        val temp = state.engineTemperature
        val unit = mapActivity.getString(R.string.nautical_unit_celsius)
        if (temp != null) {
            setText(String.format(Locale.US, "%.0f", temp - 273.15), unit)
        } else {
            setText(mapActivity.getString(R.string.n_a), unit)
        }
    }

    private fun handleBatteryVoltUpdate(state: MarineState) {
        val volt = state.batteryVoltage
        val unit = mapActivity.getString(R.string.nautical_unit_volt)
        if (volt != null) {
            setText(String.format(Locale.US, "%.2f", volt), unit)
        } else {
            setText(mapActivity.getString(R.string.n_a), unit)
        }
    }

    private fun handleBatterySocUpdate(state: MarineState) {
        val soc = state.batterySoc
        val unit = mapActivity.getString(R.string.nautical_unit_percent)
        if (soc != null) {
            setText(String.format(Locale.US, "%.0f", soc * 100.0), unit)
        } else {
            setText(mapActivity.getString(R.string.n_a), unit)
        }
    }

    private fun handleFuelLevelUpdate(state: MarineState) {
        val fuel = state.fuelLevel
        val unit = mapActivity.getString(R.string.nautical_unit_percent)
        if (fuel != null) {
            setText(String.format(Locale.US, "%.0f", fuel * 100.0), unit)
        } else {
            setText(mapActivity.getString(R.string.n_a), unit)
        }
    }

    private fun handleFreshWaterLevelUpdate(state: MarineState) {
        val water = state.freshWaterLevel
        val unit = mapActivity.getString(R.string.nautical_unit_percent)
        if (water != null) {
            setText(String.format(Locale.US, "%.0f", water * 100.0), unit)
        } else {
            setText(mapActivity.getString(R.string.n_a), unit)
        }
    }

    private fun handleWasteWaterLevelUpdate(state: MarineState) {
        val waste = state.wasteWaterLevel
        val unit = mapActivity.getString(R.string.nautical_unit_percent)
        if (waste != null) {
            setText(String.format(Locale.US, "%.0f", waste * 100.0), unit)
        } else {
            setText(mapActivity.getString(R.string.n_a), unit)
        }
    }

    private fun handlePolarRatioUpdate(state: MarineState) {
        val ratio = state.polarSpeedRatio
        val unit = mapActivity.getString(R.string.nautical_unit_percent)
        if (ratio != null) {
            setText(String.format(Locale.US, "%.0f", ratio * 100.0), unit)
        } else {
            setText(mapActivity.getString(R.string.n_a), unit)
        }
    }

    private var lastSog: Double? = null
    private fun handleSogUpdate(state: MarineState) {
        val sog = state.speedOverGround
        val unitStr = mapActivity.getString(R.string.nautical_unit_knots)
        if (sog != null) {
            val converted = sog * SpeedUnits.KNOTS.conversionCoefficient
            val trend = when {
                lastSog == null || Math.abs(sog - lastSog!!) < 0.01 -> ""
                sog > lastSog!! -> " ↑"
                else -> " ↓"
            }
            lastSog = sog
            setText(String.format(Locale.US, "%.1f%s", converted, trend), unitStr)
        } else {
            setText(mapActivity.getString(R.string.n_a), unitStr)
        }
    }

    private var lastStw: Double? = null
    private fun handleStwUpdate(state: MarineState) {
        val stw = state.speedThroughWater
        val unitStr = mapActivity.getString(R.string.nautical_unit_knots)
        if (stw != null) {
            val converted = stw * SpeedUnits.KNOTS.conversionCoefficient
            val trend = when {
                lastStw == null || Math.abs(stw - lastStw!!) < 0.01 -> ""
                stw > lastStw!! -> " ↑"
                else -> " ↓"
            }
            lastStw = stw
            setText(String.format(Locale.US, "%.1f%s", converted, trend), unitStr)
        } else {
            setText(mapActivity.getString(R.string.n_a), unitStr)
        }
    }

    private fun handleVmgUpdate(state: MarineState) {
        val vmg = state.velocityMadeGood
        val unitStr = mapActivity.getString(R.string.nautical_unit_knots)
        if (vmg != null) {
            val converted = vmg * SpeedUnits.KNOTS.conversionCoefficient
            setText(String.format(Locale.US, "%.1f", converted), unitStr)
        } else {
            setText(mapActivity.getString(R.string.n_a), unitStr)
        }
    }

    private fun handleCogUpdate(state: MarineState) {
        val cog = state.courseOverGroundTrue
        if (cog != null) {
            val cogDeg = Math.toDegrees(cog)
            setText(String.format(Locale.US, "%.0f°", cogDeg), "")
        } else {
            setText(mapActivity.getString(R.string.n_a), "")
        }
    }

    private fun handleSetDriftUpdate(state: MarineState) {
        val set = state.setTrue
        val drift = state.drift
        val unitStr = mapActivity.getString(R.string.nautical_unit_knots)
        if (set != null && drift != null) {
            val setDeg = Math.toDegrees(set)
            val driftKnots = drift * SpeedUnits.KNOTS.conversionCoefficient
            setText(String.format(Locale.US, "%03.0f°/%.1f", setDeg, driftKnots), unitStr)
        } else {
            setText(mapActivity.getString(R.string.n_a), unitStr)
        }
    }

    private fun handleDepthUpdate(state: MarineState) {
        val depth = state.depthBelowTransducer
        val unit = mapActivity.getString(R.string.nautical_unit_meters)
        if (depth != null) {
            setText(String.format(Locale.US, "%.1f", depth), unit)
        } else {
            setText(mapActivity.getString(R.string.n_a), unit)
        }
    }

    private fun handleWindUpdate(state: MarineState) {
        val wind = state.windSpeedTrue
        val unitStr = mapActivity.getString(R.string.nautical_unit_knots)
        if (wind != null) {
            val converted = wind * SpeedUnits.KNOTS.conversionCoefficient
            setText(String.format(Locale.US, "%.1f", converted), unitStr)
        } else {
            setText(mapActivity.getString(R.string.n_a), unitStr)
        }
    }
}
