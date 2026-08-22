package net.osmand.plus.views.mapwidgets.widgets

import android.view.View
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.plugins.nautical.engine.SignalKUnitConverter
import net.osmand.plus.plugins.nautical.ui.NauticalWidgetHelper
import net.osmand.plus.views.layers.base.OsmandMapLayer
import net.osmand.plus.views.mapwidgets.WidgetType
import net.osmand.plus.views.mapwidgets.WidgetsPanel
import net.osmand.plus.settings.backend.OsmandSettings

class NauticalMasterTelemetryWidget(
    mapActivity: MapActivity,
    widgetType: WidgetType,
    customId: String?,
    panel: WidgetsPanel?
) : SimpleWidget(mapActivity, widgetType, customId, panel) {

    private var dataJob: Job? = null
    private var lastWorkflowState: net.osmand.plus.plugins.nautical.engine.SailingWorkflowState? = null

    override fun setupView(view: View) {
        super.setupView(view)
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
    }

    override fun updateWidgetView() {
        super.updateWidgetView()
        widgetName?.visibility = View.GONE
    }

    override fun updateSimpleWidgetInfo(drawSettings: OsmandMapLayer.DrawSettings?) {
        // TASK-053: Automatic Preset Switching
        val autoSwitch = mapActivity.app.settings.NAUTICAL_MASTER_TELEMETRY_AUTO_SWITCH.get()
        val workflowEngine = NauticalPlugin.getInstance()?.workflowEngine
        val currentState = if (autoSwitch) workflowEngine?.currentWorkflow?.value else null
        if (autoSwitch && currentState != null && currentState != lastWorkflowState) {
            applyPresetForState(currentState)
            lastWorkflowState = currentState
        }

        val engine = NauticalPlugin.engine
        val state = engine?.getCurrentState()
        val mode = workflowEngine?.currentWorkflow?.value ?: net.osmand.plus.plugins.nautical.engine.SailingWorkflowState.TACTICAL_PASSAGE
        
        val primaryItemId = getPrimaryItemIdForMode(mode)
        val primaryWidget = WidgetType.getById(primaryItemId) ?: getDefaultPrimaryWidgetForMode(mode)

        updateIcon()

        if (state == null || state.connectionStatus == net.osmand.plus.plugins.nautical.engine.ConnectionStatus.DISCONNECTED) {
            val unit = NauticalWidgetHelper.getDefaultUnit(mapActivity, mapActivity.app.settings, primaryWidget)
            setText("--", unit)
            contentView?.alpha = 0.5f
            return
        }

        val (main, sub) = when {
            primaryWidget == WidgetType.NAUTICAL_POLAR_RATIO || primaryWidget == WidgetType.NAUTICAL_VMG -> {
                when {
                    state.polarSpeedRatio != null -> {
                        val pct = String.format(Locale.US, "%.0f%%", state.polarSpeedRatio * 100.0)
                        val subText = if (state.trueWindAngle != null) {
                            String.format(Locale.US, "TWA %.0f°", Math.toDegrees(state.trueWindAngle))
                        } else {
                            "%"
                        }
                        pct to subText
                    }
                    state.velocityMadeGood != null -> {
                        val (vmg, u) = SignalKUnitConverter.formatValue(mapActivity, mapActivity.app.settings, state.velocityMadeGood, "speed")
                        val subText = if (state.trueWindAngle != null) {
                            String.format(Locale.US, "TWA %.0f°", Math.toDegrees(state.trueWindAngle))
                        } else {
                            u
                        }
                        vmg to subText
                    }
                    state.trueWindAngle != null && state.windSpeedTrue != null -> {
                        val (ws, wu) = SignalKUnitConverter.formatValue(mapActivity, mapActivity.app.settings, state.windSpeedTrue, "speed")
                        val twaDeg = Math.toDegrees(state.trueWindAngle)
                        String.format(Locale.US, "%.0f°", twaDeg) to "$ws $wu"
                    }
                    state.speedOverGround != null && state.courseOverGroundTrue != null -> {
                        val (sog, _) = SignalKUnitConverter.formatValue(mapActivity, mapActivity.app.settings, state.speedOverGround, "speed")
                        val cogDeg = Math.toDegrees(state.courseOverGroundTrue)
                        sog to String.format(Locale.US, "%.0f°", cogDeg)
                    }
                    state.speedOverGround != null -> {
                        NauticalWidgetHelper.formatTelemetry(mapActivity, mapActivity.app.settings, WidgetType.NAUTICAL_SOG, state)
                    }
                    else -> "--" to "%"
                }
            }
            else -> {
                NauticalWidgetHelper.formatTelemetry(mapActivity, mapActivity.app.settings, primaryWidget, state)
            }
        }
        setText(main, sub)
        contentView?.alpha = if (state.connectionStatus == net.osmand.plus.plugins.nautical.engine.ConnectionStatus.STALE) 0.5f else 1.0f
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
