package net.osmand.plus.views.mapwidgets.widgets

import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.ui.widgets.NauticalElectricalDashboardBottomSheet
import net.osmand.plus.views.layers.base.OsmandMapLayer
import net.osmand.plus.views.mapwidgets.WidgetType
import net.osmand.plus.views.mapwidgets.WidgetsPanel

class NauticalElectricalWidget(
    mapActivity: MapActivity,
    widgetType: WidgetType,
    customId: String?,
    panel: WidgetsPanel?,
) : SimpleWidget(mapActivity, widgetType, customId, panel) {

    init {
        setIcons(widgetType)
    }

    private var dataJob: Job? = null

    override fun shouldShowIcon(): Boolean = true

    override fun getWidgetName(): String? = null

    override fun getAdditionalWidgetName(): String? = null

    override fun setContentTitle(messageId: Int) {
        super.setContentTitle("")
    }

    override fun setContentTitle(text: String?) {
        super.setContentTitle("")
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

    override fun updateWidgetView() {
        super.updateWidgetView()
        textView?.visibility = View.GONE
        smallTextView?.visibility = View.GONE
        widgetName?.visibility = View.GONE
    }

    override fun updateVisibility(visible: Boolean): Boolean {
        val shouldHide = shouldHide()
        val typeAllowed = widgetType != null && widgetType.isAllowed
        return super.updateVisibility(typeAllowed && !shouldHide)
    }

    override fun setupView(view: View) {
        super.setupView(view)
        textView?.visibility = View.GONE
        smallTextView?.visibility = View.GONE
        widgetName?.visibility = View.GONE

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
                                        broker.marineState.collect {
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
        
        view.setOnClickListener(getOnClickListener())
    }

    override fun getOnClickListener(): View.OnClickListener {
        return View.OnClickListener {
            if (!mapActivity.isFinishing && !mapActivity.isDestroyed) {
                NauticalElectricalDashboardBottomSheet.show(mapActivity.supportFragmentManager)
            }
        }
    }

    override fun updateSimpleWidgetInfo(drawSettings: OsmandMapLayer.DrawSettings?) {
        updateIcon()
        setText("\u200B", "")
        textView?.visibility = View.GONE
        smallTextView?.visibility = View.GONE
        widgetName?.visibility = View.GONE
    }
}
