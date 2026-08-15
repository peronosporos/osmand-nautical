package net.osmand.plus.views.mapwidgets.widgets

import android.view.View
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.views.layers.base.OsmandMapLayer
import net.osmand.plus.views.mapwidgets.WidgetType
import net.osmand.plus.views.mapwidgets.WidgetsPanel

class NauticalMasterTelemetryWidget(
    mapActivity: MapActivity,
    widgetType: WidgetType,
    customId: String?,
    panel: WidgetsPanel?
) : SimpleWidget(mapActivity, widgetType, customId, panel) {

    private var lastWorkflowState: net.osmand.plus.plugins.nautical.engine.SailingWorkflowState? = null

    override fun updateSimpleWidgetInfo(drawSettings: OsmandMapLayer.DrawSettings?) {
        updateIcon()
        
        // TASK-053: Automatic Preset Switching
        val autoSwitch = mapActivity.app.settings.NAUTICAL_MASTER_TELEMETRY_AUTO_SWITCH.get()
        val engine = net.osmand.plus.plugins.nautical.NauticalPlugin.getInstance()?.workflowEngine
        val currentState = if (autoSwitch) engine?.currentWorkflow?.value else null
        if (autoSwitch && currentState != null && currentState != lastWorkflowState) {
            applyPresetForState(currentState)
            lastWorkflowState = currentState
        }

        setText(mapActivity.getString(R.string.nautical_master_telemetry), "")
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

    private fun getPresetName(state: net.osmand.plus.plugins.nautical.engine.SailingWorkflowState?): String {
        return when (state) {
            net.osmand.plus.plugins.nautical.engine.SailingWorkflowState.TACTICAL_PASSAGE -> mapActivity.getString(R.string.nautical_workflow_tactical)
            net.osmand.plus.plugins.nautical.engine.SailingWorkflowState.CLOSE_QUARTERS -> mapActivity.getString(R.string.nautical_workflow_close_quarters)
            net.osmand.plus.plugins.nautical.engine.SailingWorkflowState.STATIONARY_ANCHORED -> mapActivity.getString(R.string.nautical_workflow_anchored)
            else -> ""
        }
    }

    override fun getOnClickListener(): View.OnClickListener {
        return View.OnClickListener {
            if (!mapActivity.isFinishing) {
                NauticalTelemetryGridBottomSheet.show(mapActivity.supportFragmentManager, customId ?: widgetType.id)
            }
        }
    }
}
