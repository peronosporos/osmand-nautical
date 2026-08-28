package net.osmand.plus.views.mapwidgets.widgets

import android.view.View
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.views.layers.base.OsmandMapLayer
import net.osmand.plus.views.mapwidgets.WidgetType
import net.osmand.plus.views.mapwidgets.WidgetsPanel
import java.util.Locale

class PolarSpeedRatioWidget(
    mapActivity: MapActivity,
    widgetType: WidgetType,
    customId: String?,
    panel: WidgetsPanel?,
) : SimpleWidget(mapActivity, widgetType, customId, panel) {

    private var dataJob: Job? = null

    init {
        setIcons(widgetType)
    }

    override fun setupView(view: View) {
        super.setupView(view)
        view.minimumHeight = (48f * view.resources.displayMetrics.density).toInt()
        view.addOnAttachStateChangeListener(
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    val broker = NauticalPlugin.engine?.dataBroker
                    if (broker != null) {
                        dataJob?.cancel()
                        dataJob = mapActivity.lifecycleScope.launch(Dispatchers.Main.immediate) {
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

                override fun onViewDetachedFromWindow(v: View) {
                    dataJob?.cancel()
                    dataJob = null
                }
            },
        )
    }

    override fun updateIcon() {
        val iconId = getIconId()
        if (iconId != 0) {
            val color = settings.applicationMode.getProfileColor(isNightMode)
            setImageDrawable(iconsCache.getPaintedIcon(iconId, color))
        }
    }

    override fun updateColors(textState: net.osmand.plus.views.layers.MapInfoLayer.TextState) {
        super.updateColors(textState)
        updateIcon()
    }

    override fun updateSimpleWidgetInfo(drawSettings: OsmandMapLayer.DrawSettings?) {
        updateIcon()
        val engine = NauticalPlugin.engine
        if (engine == null) {
            setText("--", "N/A")
            return
        }
        val state = engine.getCurrentState()

        val isNight = NauticalPlugin.isNightVision(app)
        if (isNight) {
            view?.findViewById<View>(R.id.widget_bg)?.setBackgroundColor(0xEE120000.toInt())
        }

        val twsMs = state.windSpeedTrue ?: 0.0
        val twsKn = twsMs * 1.94384
        val twaRad = state.trueWindAngle ?: state.windDirectionApparent ?: 0.0
        val twaDeg = Math.toDegrees(kotlin.math.abs(twaRad))
        val isUpwind = twaDeg < 90.0

        // Optimum Target TWA for max Upwind/Downwind VMG
        val targetTwaDeg = if (isUpwind) 42.0 else 142.0
        val targetSpeedKn = (state.polarTargetSpeed ?: (twsMs * 0.45)) * 1.94384

        // Reefing Advisory: When current TWS > sailMaxTws
        val currentReefs = state.reefs ?: 0
        val reefAdvisory = when {
            twsKn >= 24.0 && currentReefs < 2 -> "REEF 2"
            twsKn >= 18.0 && currentReefs < 1 -> "REEF 1"
            else -> null
        }

        val ratio = (state.polarSpeedRatio ?: (if (targetSpeedKn > 0.1) ((state.speedThroughWater ?: state.speedOverGround ?: 0.0) * 1.94384 / targetSpeedKn) else null))?.let { it * 100.0 }

        val mainText = if (ratio != null) String.format(Locale.US, "%.0f%%", ratio) else String.format(Locale.US, "%.1fkn", targetSpeedKn)
        val subText = if (reefAdvisory != null) {
            reefAdvisory
        } else {
            String.format(Locale.US, "TWA %.0f°", targetTwaDeg)
        }

        setText(mainText, subText)
    }
}
