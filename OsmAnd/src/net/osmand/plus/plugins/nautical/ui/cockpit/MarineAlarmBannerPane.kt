package net.osmand.plus.plugins.nautical.ui.cockpit

import android.view.View
import kotlinx.coroutines.CoroutineScope
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.engine.AlarmPriorityManager
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.plugins.nautical.ui.widgets.MarineAlarmBannerView

/**
 * Pane wrapper for MarineAlarmBannerView (step 2 of the Cockpit plan).
 * The banner is self-observing: it starts/stops its own collectors in
 * onAttachedToWindow/onDetachedFromWindow, so bind/unbind are no-ops and
 * mount/unmount (host add/removeHeader) is enough to drive its whole lifecycle.
 * Keep the legacy NauticalUiOverlayManager field assignment in sync until the
 * remaining wirings migrate.
 */
class MarineAlarmBannerPane(
    private val activity: MapActivity,
    alarmPriorityManager: AlarmPriorityManager
) : CockpitPane {

    override val paneId = "marine_alarm_banner"
    override val priority = 0
    override val requiredWhen: (MarineState) -> Boolean = ALWAYS_REQUIRED
    override val dataDependencies: List<kotlinx.coroutines.flow.StateFlow<*>> = emptyList()

    val bannerView = MarineAlarmBannerView(activity, alarmPriorityManager)

    override fun buildView(activity: MapActivity): View = bannerView

    override fun bind(view: View, scope: CoroutineScope) {
        // Observers start in MarineAlarmBannerView.onAttachedToWindow
    }

    override fun unbind(view: View) {
        // Observers stop in MarineAlarmBannerView.onDetachedFromWindow
    }

    companion object {
        private val ALWAYS_REQUIRED: (MarineState) -> Boolean = { true }
    }
}
