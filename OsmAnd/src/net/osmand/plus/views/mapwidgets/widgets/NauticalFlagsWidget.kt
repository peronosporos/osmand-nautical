package net.osmand.plus.views.mapwidgets.widgets

import android.view.View
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.views.layers.base.OsmandMapLayer
import net.osmand.plus.views.mapwidgets.WidgetType
import net.osmand.plus.views.mapwidgets.WidgetsPanel
import net.osmand.plus.plugins.nautical.NauticalPlugin

/**
 * Visual guide for International Maritime Signal Flags.
 */
class NauticalFlagsWidget(
    mapActivity: MapActivity,
    widgetType: WidgetType,
    customId: String?,
    panel: WidgetsPanel?
) : SimpleWidget(mapActivity, widgetType, customId, panel) {

    init {
        setIcons(widgetType)
    }

    private var dataJob: Job? = null

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

    private val allFlags = mapOf(
        "A" to "Diver Down",
        "B" to "Dangerous Cargo",
        "O" to "Man Overboard",
        "Q" to "Quarantine",
        "V" to "Require Assistance",
        "W" to "Medical Assistance"
    )

    private var currentFlagIndex = 0
    private val flagKeys = allFlags.keys.toList()

    override fun setupView(view: View) {
        super.setupView(view)
        view.addOnAttachStateChangeListener(
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    val broker = NauticalPlugin.engine?.dataBroker
                    if (broker != null) {
                        dataJob?.cancel()
                        dataJob = mapActivity.lifecycleScope.launch(Dispatchers.Main.immediate) {
                            broker.marineState.collect {
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

    override fun updateSimpleWidgetInfo(drawSettings: OsmandMapLayer.DrawSettings?) {
        val state = NauticalPlugin.engine?.getCurrentState()
        val activeFlags = state?.flags ?: emptyList()
        
        updateIcon()
        
        if (activeFlags.isNotEmpty()) {
            val key = activeFlags[currentFlagIndex % activeFlags.size]
            setText(key, allFlags[key] ?: "Active Flag")
        } else {
            val key = flagKeys[currentFlagIndex % flagKeys.size]
            setText(key, allFlags[key] ?: "")
        }
    }

    override fun getOnClickListener(): View.OnClickListener {
        return View.OnClickListener {
            val state = NauticalPlugin.engine?.getCurrentState()
            val activeFlags = state?.flags ?: emptyList()
            
            val listSize = if (activeFlags.isNotEmpty()) activeFlags.size else flagKeys.size
            currentFlagIndex = (currentFlagIndex + 1) % listSize
            updateInfo(null)
        }
    }
}
