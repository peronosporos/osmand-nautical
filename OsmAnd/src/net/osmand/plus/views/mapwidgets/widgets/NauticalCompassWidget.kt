package net.osmand.plus.views.mapwidgets.widgets

import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.views.layers.base.OsmandMapLayer
import net.osmand.plus.views.mapwidgets.WidgetType
import net.osmand.plus.views.mapwidgets.WidgetsPanel
import java.util.Locale

class NauticalCompassWidget(
    mapActivity: MapActivity,
    widgetType: WidgetType,
    customId: String?,
    panel: WidgetsPanel?,
) : SimpleWidget(mapActivity, widgetType, customId, panel) {

    init {
        setIcons(widgetType)
    }

    private var dataJob: kotlinx.coroutines.Job? = null

    override fun getWidgetName(): String = mapActivity.getString(R.string.map_widget_compass)

    override fun updateIcon() {
        val iconId = iconId
        if (iconId != 0) {
            val isNight = NauticalPlugin.isNightVision(mapActivity.app)
            val color = if (isNight) 0xFFFF1744.toInt() else settings.applicationMode.getProfileColor(isNightMode)
            setImageDrawable(iconsCache.getPaintedIcon(iconId, color))
        }
    }

    override fun updateColors(textState: net.osmand.plus.views.layers.MapInfoLayer.TextState) {
        super.updateColors(textState)
        updateIcon()
    }

    override fun setupView(view: View) {
        super.setupView(view)
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

    override fun updateSimpleWidgetInfo(drawSettings: OsmandMapLayer.DrawSettings?) {
        updateIcon()
        val state = NauticalPlugin.engine?.getCurrentState()
        if (state == null) {
            setText("--", "")
            return
        }

        val heading = state.headingMagnetic ?: state.headingTrue
        val mainText = heading?.let { String.format(Locale.US, "%.0f°", Math.toDegrees(it)) } ?: "--"

        val hasThreat = state.threatName != null && state.cpa != null && state.cpa < 1.0
        val subText = if (hasThreat) {
            "⚠ EVADE"
        } else if (state.rudderAngle != null) {
            val rDeg = Math.toDegrees(state.rudderAngle)
            val absR = kotlin.math.abs(rDeg)
            val dir = if (rDeg < -0.5) "◀" else if (rDeg > 0.5) "▶" else ""
            val warn = if (absR > 25.0) "⚠" else if (absR > 15.0) "⚡" else ""
            String.format(Locale.US, "%sRUD %s%.0f°", warn, dir, absR)
        } else {
            state.magneticVariation?.let { 
                val deg = Math.toDegrees(it)
                val dir = if (deg >= 0) "E" else "W"
                String.format(Locale.US, "%.1f°%s", Math.abs(deg), dir)
            } ?: ""
        }

        setText(mainText, subText)
        updateIcon()
    }

    override fun getOnClickListener(): View.OnClickListener {
        return View.OnClickListener {
            if (!mapActivity.isFinishing) {
                NauticalCompassWizardDialog().show(mapActivity.supportFragmentManager, NauticalCompassWizardDialog.TAG)
            }
        }
    }
}
