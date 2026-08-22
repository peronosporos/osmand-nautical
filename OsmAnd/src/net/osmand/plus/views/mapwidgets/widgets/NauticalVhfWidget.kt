package net.osmand.plus.views.mapwidgets.widgets

import android.graphics.Color
import android.view.View
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.network.VhfStatus
import net.osmand.plus.plugins.nautical.ui.VhfHistoryBottomSheet
import net.osmand.plus.views.layers.base.OsmandMapLayer
import net.osmand.plus.views.mapwidgets.WidgetType
import net.osmand.plus.views.mapwidgets.WidgetsPanel

class NauticalVhfWidget(
    mapActivity: MapActivity,
    widgetType: WidgetType,
    customId: String?,
    panel: WidgetsPanel?,
) : SimpleWidget(mapActivity, widgetType, customId, panel) {

    private var isPulseActive = false

    override fun updateIcon() {
        val vhf = NauticalPlugin.getInstance()?.vhfManager
        val status = vhf?.status?.value ?: VhfStatus.IDLE
        val error = vhf?.errorFlow?.value
        
        val iconId = when {
            error != null -> R.drawable.ic_action_alert
            status == VhfStatus.LIVE -> R.drawable.ic_action_antenna
            status == VhfStatus.REPLAYING -> R.drawable.ic_action_play_dark
            else -> R.drawable.ic_action_antenna
        }

        val color = when {
            error != null -> ContextCompat.getColor(app, R.color.nautical_status_red)
            status == VhfStatus.LIVE -> if (isPulseActive) Color.RED else ContextCompat.getColor(app, R.color.map_widget_icon_color)
            status == VhfStatus.REPLAYING -> ContextCompat.getColor(app, R.color.nautical_status_yellow)
            else -> ContextCompat.getColor(app, R.color.map_widget_icon_color)
        }

        setImageDrawable(iconsCache.getPaintedIcon(iconId, color))
    }

    override fun updateColors(textState: net.osmand.plus.views.layers.MapInfoLayer.TextState) {
        super.updateColors(textState)
        updateIcon()
    }

    override fun setupView(view: View) {
        super.setupView(view)

        view.setOnLongClickListener {
            VhfHistoryBottomSheet.show(mapActivity.supportFragmentManager)
            true
        }

        view.addOnAttachStateChangeListener(
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    val plugin = NauticalPlugin.getInstance()
                    plugin?.vhfManager?.status?.onEach { 
                        updateInfo(null)
                    }?.launchIn(mapActivity.lifecycleScope)

                    plugin?.vhfManager?.errorFlow?.onEach {
                        updateInfo(null)
                    }?.launchIn(mapActivity.lifecycleScope)
                    
                    NauticalPlugin.engine?.pulseFlow?.onEach { 
                        if (isPulseActive != it) {
                            isPulseActive = it
                            updateIcon()
                        }
                    }?.launchIn(mapActivity.lifecycleScope)
                }

                override fun onViewDetachedFromWindow(v: View) {}
            },
        )
    }

    override fun getOnClickListener(): View.OnClickListener {
        return View.OnClickListener {
            val vhf = NauticalPlugin.getInstance()?.vhfManager ?: return@OnClickListener
            if (vhf.status.value == VhfStatus.LIVE) {
                vhf.stopAudio()
            } else {
                vhf.toggleLiveStream()
            }
        }
    }

    override fun updateSimpleWidgetInfo(drawSettings: OsmandMapLayer.DrawSettings?) {
        val vhf = NauticalPlugin.getInstance()?.vhfManager ?: return
        val status = vhf.status.value
        val last = vhf.lastTransmission.value
        val error = vhf.errorFlow.value

        updateIcon()

        if (error != null) {
            setText("VHF", mapActivity.getString(R.string.shared_string_io_error))
            return
        }

        when (status) {
            VhfStatus.LIVE -> {
                setText(mapActivity.getString(R.string.nautical_vhf_live), last?.vesselName ?: "")
            }
            VhfStatus.REPLAYING -> {
                setText(mapActivity.getString(R.string.nautical_vhf_replay), last?.vesselName ?: "")
            }
            VhfStatus.IDLE -> {
                val channel = last?.channel ?: "---"
                setText("VHF", "CH $channel")
            }
        }
    }
}
