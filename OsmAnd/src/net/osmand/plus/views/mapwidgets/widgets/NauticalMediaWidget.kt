package net.osmand.plus.views.mapwidgets.widgets

import android.view.View
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.ui.NauticalMediaPlayerWidget
import net.osmand.plus.views.layers.base.OsmandMapLayer
import net.osmand.plus.views.mapwidgets.WidgetType
import net.osmand.plus.views.mapwidgets.WidgetsPanel

class NauticalMediaWidget(
    mapActivity: MapActivity,
    widgetType: WidgetType,
    customId: String?,
    panel: WidgetsPanel?,
) : MapWidget(mapActivity, widgetType, customId, panel) {

    private var playerView: NauticalMediaPlayerWidget? = null

    override fun getLayoutId(): Int = net.osmand.plus.R.layout.nautical_media_hud

    override fun getView(): View {
        return playerView ?: NauticalMediaPlayerWidget(mapActivity).also {
            playerView = it
            setupView(it)
        }
    }

    override fun setupView(view: View) {
        playerView = view as? NauticalMediaPlayerWidget
    }

    override fun updateInfo(view: View, drawSettings: OsmandMapLayer.DrawSettings?) {
        val state = NauticalPlugin.engine?.getCurrentState()
        if (state != null) {
            playerView?.updateState(state)
        }
    }
}
