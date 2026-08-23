package net.osmand.plus.views.mapwidgets.widgets

import android.view.View
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.ConnectionStatus
import net.osmand.plus.plugins.nautical.engine.SailingWorkflowState
import net.osmand.plus.views.layers.base.OsmandMapLayer
import net.osmand.plus.views.mapwidgets.WidgetType
import net.osmand.plus.views.mapwidgets.WidgetsPanel

class NauticalMasterTelemetryWidget(
    mapActivity: MapActivity,
    widgetType: WidgetType,
    customId: String?,
    panel: WidgetsPanel?
) : SimpleWidget(mapActivity, widgetType, customId, panel) {

    private var dataJob: Job? = null
    private var lastWorkflowState: SailingWorkflowState? = null

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

    override fun setupView(view: View) {
        super.setupView(view)
        widgetName?.visibility = View.GONE
        widgetName?.text = ""
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
        view.setOnClickListener(getOnClickListener())
    }

    override fun updateWidgetView() {
        super.updateWidgetView()
        widgetName?.visibility = View.GONE
        widgetName?.text = ""
    }

    override fun updateSimpleWidgetInfo(drawSettings: OsmandMapLayer.DrawSettings?) {
        widgetName?.visibility = View.GONE
        widgetName?.text = ""
        
        // Automatic Preset Switching
        val autoSwitch = mapActivity.app.settings.NAUTICAL_MASTER_TELEMETRY_AUTO_SWITCH.get()
        val workflowEngine = NauticalPlugin.getInstance()?.workflowEngine
        val currentState = if (autoSwitch) workflowEngine?.currentWorkflow?.value else null
        if (autoSwitch && currentState != null && currentState != lastWorkflowState) {
            applyPresetForState(currentState)
            lastWorkflowState = currentState
        }

        updateIcon()

        val mode = workflowEngine?.currentWorkflow?.value ?: SailingWorkflowState.TACTICAL_PASSAGE
        val mainText = when (mode) {
            SailingWorkflowState.TACTICAL_PASSAGE -> "PASS"
            SailingWorkflowState.CLOSE_QUARTERS -> "DOCK"
            SailingWorkflowState.STATIONARY_ANCHORED -> "ANCH"
        }

        val engine = NauticalPlugin.engine
        val state = engine?.getCurrentState()
        val isConnected = state != null && state.connectionStatus != ConnectionStatus.DISCONNECTED

        val subText = if (isConnected) "ON" else "OFF"
        setText(mainText, subText)
        contentView?.alpha = if (isConnected) 1.0f else 0.45f
    }

    override fun updateIcon() {
        val iconColor = mapActivity.app.settings.APPLICATION_MODE.get().getProfileColor(nightMode)
        setImageDrawable(iconsCache.getPaintedIcon(R.drawable.ic_action_nautical_perf, iconColor))
    }

    override fun updateColors(textState: net.osmand.plus.views.layers.MapInfoLayer.TextState) {
        super.updateColors(textState)
        updateIcon()
    }

    private fun applyPresetForState(state: SailingWorkflowState) {
        val settings = mapActivity.app.settings
        val preset = when (state) {
            SailingWorkflowState.TACTICAL_PASSAGE -> 
                settings.NAUTICAL_MASTER_TELEMETRY_ITEMS_PASSAGE.get()
            SailingWorkflowState.CLOSE_QUARTERS -> 
                settings.NAUTICAL_MASTER_TELEMETRY_ITEMS_DOCKING.get()
            SailingWorkflowState.STATIONARY_ANCHORED -> 
                settings.NAUTICAL_MASTER_TELEMETRY_ITEMS_ANCHORED.get()
        }
        settings.NAUTICAL_MASTER_TELEMETRY_ITEMS.set(preset)
    }

    override fun getOnClickListener(): View.OnClickListener {
        return View.OnClickListener {
            if (!mapActivity.isFinishing && !mapActivity.isDestroyed) {
                net.osmand.plus.plugins.nautical.ui.MasterTelemetryBottomSheet.show(
                    mapActivity.supportFragmentManager,
                    customId ?: widgetType.id
                )
            }
        }
    }
}
