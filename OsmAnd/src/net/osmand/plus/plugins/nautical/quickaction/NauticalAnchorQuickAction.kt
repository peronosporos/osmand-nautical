package net.osmand.plus.plugins.nautical.quickaction

import androidx.annotation.Keep
import android.os.Bundle
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.PluginsHelper
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.AnchorCalculator
import net.osmand.plus.plugins.nautical.ui.anchor.AnchorWatchDialogFragment
import net.osmand.plus.quickaction.QuickAction
import net.osmand.plus.quickaction.QuickActionIds.NAUTICAL_ANCHOR_ACTION_ID
import net.osmand.plus.quickaction.QuickActionType

@Keep
class NauticalAnchorQuickAction : QuickAction {

    companion object {
        @JvmField
        val TYPE: QuickActionType = QuickActionType(NAUTICAL_ANCHOR_ACTION_ID, "nautical.anchor.toggle", NauticalAnchorQuickAction::class.java)
            .nameRes(R.string.nautical_anchor_label)
            .iconRes(R.drawable.ic_action_anchor)
            .category(QuickActionType.MAP_INTERACTIONS)
    }

    @Keep
    constructor() : super(TYPE)

    @Keep
    constructor(quickAction: QuickAction) : super(quickAction)

    @Keep
    constructor(type: Int) : super(TYPE)

    override fun execute(mapActivity: MapActivity, params: Bundle?) {
        val app = mapActivity.app
        val lat = app.settings.NAUTICAL_ANCHOR_LAT.get()
        if (lat == 0.0) {
            val state = NauticalPlugin.engine?.getCurrentState()
            if (state != null) {
                val liveDepth = state.depthBelowTransducer ?: state.depthBelowKeel
                val hasGps = (state.latitude != null) && (state.longitude != null) && (!state.stalePaths.contains("navigation.position"))
                
                if (liveDepth != null && liveDepth > 0 && hasGps) {
                    // TASK-046: One-Tap Auto-Scope Implementation
                    val depth = liveDepth.toFloat()
                    val bowOffset = app.settings.NAUTICAL_ANCHOR_BOW_OFFSET.get()
                    val scopeRatio = app.settings.NAUTICAL_ANCHOR_SCOPE_RATIO.get()
                    val safetyMargin = app.settings.NAUTICAL_ANCHOR_SAFETY_MARGIN.get()
                    val tideRise = app.settings.NAUTICAL_ANCHOR_TIDE_RISE.get()
                    val freeboard = app.settings.NAUTICAL_ANCHOR_FREEBOARD.get()
                    
                    // Rode = (Live_Depth + TIDE + FREEBOARD) * NAUTICAL_SCOPE_RATIO
                    val rode = AnchorCalculator.calculateRodeLength(depth.toDouble(), tideRise.toDouble(), freeboard.toDouble(), scopeRatio.toDouble())
                    
                    val totalRadius = AnchorCalculator.calculateTotalRadius(rode, bowOffset.toDouble(), safetyMargin.toDouble())
                    
                    val curLat = state.latitude
                    val curLon = state.longitude
                    val hdg = state.headingTrue?.let { Math.toDegrees(it) } ?: state.courseOverGroundTrue?.let { Math.toDegrees(it) } ?: 0.0
                    
                    val anchorPos = AnchorCalculator.calculateAnchorDrop(curLat, curLon, hdg, bowOffset.toDouble())
                    
                    val plugin = PluginsHelper.getPlugin(NauticalPlugin::class.java)
                    plugin?.anchorWatchdog?.setAnchor(anchorPos.latitude, anchorPos.longitude, totalRadius.toFloat())
                    app.settings.NAUTICAL_ANCHOR_DEPTH.set(depth)
                    
                    app.showToastMessage(app.getString(R.string.nautical_anchor_set_auto, depth, scopeRatio, totalRadius.toInt()))
                } else {
                    // Fallback to manual dialog if telemetry is missing
                    AnchorWatchDialogFragment.show(mapActivity.supportFragmentManager)
                }
            } else {
                // Fallback to manual dialog if telemetry is missing
                AnchorWatchDialogFragment.show(mapActivity.supportFragmentManager)
            }
        } else {
            val plugin = PluginsHelper.getPlugin(NauticalPlugin::class.java)
            plugin?.anchorWatchdog?.stop()
            app.showToastMessage(R.string.nautical_anchor_cleared)
        }
        app.osmandMap?.refreshMap()
        PluginsHelper.getPlugin(NauticalPlugin::class.java)?.updateNauticalBackgroundService()
    }
}
