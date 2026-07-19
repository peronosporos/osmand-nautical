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
        })
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
                else -> {}
            }
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
