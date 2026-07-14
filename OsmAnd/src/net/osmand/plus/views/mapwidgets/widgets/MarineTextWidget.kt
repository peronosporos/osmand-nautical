package net.osmand.plus.views.mapwidgets.widgets

import android.annotation.SuppressLint
import android.view.View
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.settings.backend.preferences.OsmandPreference
import net.osmand.plus.settings.enums.WidgetSize
import net.osmand.plus.views.layers.base.OsmandMapLayer
import net.osmand.plus.views.mapwidgets.WidgetType
import net.osmand.plus.views.mapwidgets.WidgetsPanel
import net.osmand.plus.views.mapwidgets.widgetinterfaces.ISupportWidgetResizing
import net.osmand.plus.views.mapwidgets.widgetstates.ResizableWidgetState
import net.osmand.shared.settings.enums.MetricsConstants
import net.osmand.shared.units.SpeedUnits
import java.util.*

class MarineTextWidget(
    mapActivity: MapActivity,
    widgetType: WidgetType,
    customId: String?,
    panel: WidgetsPanel?
) : TextInfoWidget(mapActivity, widgetType, customId, panel), ISupportWidgetResizing {

    private val widgetSizePref: OsmandPreference<WidgetSize> = ResizableWidgetState.registerWidgetSizePref(
        mapActivity.app,
        customId,
        widgetType,
        WidgetSize.MEDIUM
    )

    private var lastDisplayedText = ""
    private var lastDisplayedSmallText = ""

    private var lastUpdateTime = 0L
    private val marineStateListener: (MarineState) -> Unit = {
        val now = System.currentTimeMillis()
        if (now - lastUpdateTime > 200) { // Throttle 5Hz
            lastUpdateTime = now
            mapActivity.runOnUiThread {
                updateInfo(view, null)
            }
        }
    }

    override fun getWidgetSizePref(): OsmandPreference<WidgetSize> = widgetSizePref

    override fun allowResize(): Boolean = true

    override fun recreateView() {
        setupView(view)
        updateInfo(view, null)
    }

    override fun setupView(view: View) {
        super.setupView(view)

        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                NauticalPlugin.engine?.registerListener(marineStateListener)
            }

            override fun onViewDetachedFromWindow(v: View) {
                NauticalPlugin.engine?.unregisterListener(marineStateListener)
            }
        })

        view.setOnClickListener {
            if (!mapActivity.isFinishing) {
                val dialog = NauticalDataBottomSheet.newInstance(this.widgetType)
                dialog.show(mapActivity.supportFragmentManager, "nautical_graph")
            }
        }
    }

    @SuppressLint("DefaultLocale")
    override fun updateInfo(view: View, drawSettings: OsmandMapLayer.DrawSettings?) {
        super.updateInfo(view, drawSettings)

        val engine = NauticalPlugin.engine ?: return
        val state = engine.getCurrentState()

        if (state == null || state.connectionStatus == net.osmand.plus.plugins.nautical.engine.ConnectionStatus.DISCONNECTED) {
            updateText(mapActivity.getString(R.string.nautical_status_off), mapActivity.getString(R.string.n_a))
        } else if (state.connectionStatus == net.osmand.plus.plugins.nautical.engine.ConnectionStatus.STALE) {
            updateText(mapActivity.getString(R.string.nautical_connection_stale), "")
        } else {
            val settings = mapActivity.app.settings
            val metrics = settings.METRIC_SYSTEM.get()

            when (widgetType) {
                WidgetType.NAUTICAL_DEPTH -> handleDepthUpdate(state, metrics)
                WidgetType.NAUTICAL_WIND -> handleWindUpdate(state, metrics)
                WidgetType.NAUTICAL_PILOT -> handlePilotUpdate(state)
                WidgetType.NAUTICAL_VMG -> handleVmgUpdate(state, metrics)
                WidgetType.NAUTICAL_COG -> handleCogUpdate(state)
                else -> {}
            }
        }
        view.invalidate()
    }

    private fun handleVmgUpdate(state: MarineState, metrics: MetricsConstants) {
        val vmg = state.velocityMadeGood
        if (vmg != null) {
            val speedUnit = metrics.getSpeedUnit()
            val converted = vmg * speedUnit.conversionCoefficient
            val unitStr = when (speedUnit) {
                SpeedUnits.KNOTS -> mapActivity.getString(R.string.nautical_unit_knots)
                SpeedUnits.KILOMETERS_PER_HOUR -> "km/h"
                SpeedUnits.MILES_PER_HOUR -> "mph"
                else -> "m/s"
            }
            updateText(String.format(Locale.US, "%.1f", converted), unitStr)
        } else {
            updateText(mapActivity.getString(R.string.n_a), mapActivity.getString(R.string.nautical_unit_knots))
        }
    }

    private fun handleCogUpdate(state: MarineState) {
        val cog = state.courseOverGroundTrue
        if (cog != null) {
            // SignalK angles are in radians
            val cogDeg = Math.toDegrees(cog)
            updateText(String.format(Locale.US, "%.0f°", cogDeg), "COG")
        } else {
            updateText(mapActivity.getString(R.string.n_a), "COG")
        }
    }

    private fun updateText(text: String?, smallText: String?) {
        val t = text ?: ""
        val st = smallText ?: ""
        if (lastDisplayedText != t || lastDisplayedSmallText != st) {
            setText(t, st)
            lastDisplayedText = t
            lastDisplayedSmallText = st
        }
    }

    private fun handlePilotUpdate(state: MarineState) {
        val mode = state.autopilotMode
        updateText(mode.uppercase(Locale.US), "")
    }

    private fun handleDepthUpdate(state: MarineState, metrics: MetricsConstants) {
        var depth = state.depthBelowTransducer
        if (depth != null) {
            val useFeet = metrics.shouldUseFeet()
            val unit = if (useFeet) mapActivity.getString(R.string.nautical_unit_feet) else mapActivity.getString(R.string.nautical_unit_meters)

            if (useFeet) depth *= 3.28084
            updateText(String.format(Locale.US, "%.1f", depth), unit)
        } else {
            updateText(mapActivity.getString(R.string.n_a), mapActivity.getString(R.string.nautical_unit_meters))
        }
    }

    private fun handleWindUpdate(state: MarineState, metrics: MetricsConstants) {
        val wind = state.windSpeedTrue
        if (wind != null) {
            val speedUnit = metrics.getSpeedUnit()
            val converted = wind * speedUnit.conversionCoefficient
            val unitStr = when (speedUnit) {
                SpeedUnits.KNOTS -> mapActivity.getString(R.string.nautical_unit_knots)
                SpeedUnits.KILOMETERS_PER_HOUR -> "km/h"
                SpeedUnits.MILES_PER_HOUR -> "mph"
                else -> "m/s"
            }
            updateText(String.format(Locale.US, "%.1f", converted), unitStr)
        } else {
            updateText(mapActivity.getString(R.string.n_a), mapActivity.getString(R.string.nautical_unit_knots))
        }
    }
}
