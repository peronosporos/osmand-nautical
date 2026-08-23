package net.osmand.plus.views.mapwidgets.widgets

import android.view.View
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.ConnectionStatus
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.plugins.nautical.engine.SailingWorkflowState
import net.osmand.plus.plugins.nautical.engine.SignalKUnitConverter
import net.osmand.plus.plugins.nautical.ui.NauticalWidgetHelper
import net.osmand.plus.views.layers.base.OsmandMapLayer
import net.osmand.plus.views.mapwidgets.WidgetType
import net.osmand.plus.views.mapwidgets.WidgetsPanel
import net.osmand.plus.settings.backend.OsmandSettings
import java.util.Locale

class NauticalMasterTelemetryWidget(
    mapActivity: MapActivity,
    widgetType: WidgetType,
    customId: String?,
    panel: WidgetsPanel?
) : SimpleWidget(mapActivity, widgetType, customId, panel) {

    private var dataJob: Job? = null
    private var lastWorkflowState: net.osmand.plus.plugins.nautical.engine.SailingWorkflowState? = null

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
                    val broker = net.osmand.plus.plugins.nautical.NauticalPlugin.engine?.dataBroker
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
        // TASK-053: Automatic Preset Switching
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
            SailingWorkflowState.TACTICAL_PASSAGE -> "PASSAGE"
            SailingWorkflowState.CLOSE_QUARTERS -> "DOCKING"
            SailingWorkflowState.STATIONARY_ANCHORED -> "ANCHORED"
        }

        val engine = NauticalPlugin.engine
        val state = engine?.getCurrentState()
        val isConnected = state != null && state.connectionStatus != ConnectionStatus.DISCONNECTED

        val subText = if (isConnected) "ACTIVE" else "STANDBY"
        setText(mainText, subText)
        contentView?.alpha = if (isConnected) 1.0f else 0.5f
    }

    override fun updateIcon() {
        val workflowEngine = NauticalPlugin.getInstance()?.workflowEngine
        val mode = workflowEngine?.currentWorkflow?.value ?: net.osmand.plus.plugins.nautical.engine.SailingWorkflowState.TACTICAL_PASSAGE
        val primaryItemId = getPrimaryItemIdForMode(mode)
        val primaryWidget = WidgetType.getById(primaryItemId) ?: getDefaultPrimaryWidgetForMode(mode)

        val iconId = primaryWidget.getIconId(nightMode)
        val iconColor = mapActivity.app.settings.APPLICATION_MODE.get().getProfileColor(nightMode)
        setImageDrawable(iconsCache.getPaintedIcon(if (iconId != 0) iconId else R.drawable.ic_action_nautical_perf, iconColor))
    }

    override fun updateColors(textState: net.osmand.plus.views.layers.MapInfoLayer.TextState) {
        super.updateColors(textState)
        updateIcon()
    }

    private fun getDefaultPrimaryWidgetForMode(mode: net.osmand.plus.plugins.nautical.engine.SailingWorkflowState): WidgetType {
        return when (mode) {
            net.osmand.plus.plugins.nautical.engine.SailingWorkflowState.TACTICAL_PASSAGE -> WidgetType.NAUTICAL_POLAR_RATIO
            net.osmand.plus.plugins.nautical.engine.SailingWorkflowState.CLOSE_QUARTERS -> WidgetType.NAUTICAL_DEPTH
            net.osmand.plus.plugins.nautical.engine.SailingWorkflowState.STATIONARY_ANCHORED -> WidgetType.NAUTICAL_DEPTH
        }
    }

    private fun getPrimaryItemIdForMode(mode: net.osmand.plus.plugins.nautical.engine.SailingWorkflowState): String {
        val settings = mapActivity.app.settings
        return when (mode) {
            net.osmand.plus.plugins.nautical.engine.SailingWorkflowState.TACTICAL_PASSAGE -> settings.NAUTICAL_MASTER_TELEMETRY_PRIMARY_ITEM_PASSAGE.get()
            net.osmand.plus.plugins.nautical.engine.SailingWorkflowState.CLOSE_QUARTERS -> settings.NAUTICAL_MASTER_TELEMETRY_PRIMARY_ITEM_DOCKING.get()
            net.osmand.plus.plugins.nautical.engine.SailingWorkflowState.STATIONARY_ANCHORED -> settings.NAUTICAL_MASTER_TELEMETRY_PRIMARY_ITEM_ANCHORED.get()
        }
    }

    private fun applyPresetForState(state: net.osmand.plus.plugins.nautical.engine.SailingWorkflowState) {
        val settings = mapActivity.app.settings
        val preset = when (state) {
            net.osmand.plus.plugins.nautical.engine.SailingWorkflowState.TACTICAL_PASSAGE -> 
                settings.NAUTICAL_MASTER_TELEMETRY_ITEMS_PASSAGE.get()
            net.osmand.plus.plugins.nautical.engine.SailingWorkflowState.CLOSE_QUARTERS -> 
                settings.NAUTICAL_MASTER_TELEMETRY_ITEMS_DOCKING.get()
            net.osmand.plus.plugins.nautical.engine.SailingWorkflowState.STATIONARY_ANCHORED -> 
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
