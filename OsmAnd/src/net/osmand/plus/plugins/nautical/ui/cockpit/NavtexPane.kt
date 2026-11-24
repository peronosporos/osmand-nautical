package net.osmand.plus.plugins.nautical.ui.cockpit

import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.hazard.ui.NavtexHudView
import net.osmand.plus.plugins.nautical.hazard.viewmodel.NavtexViewModel
import net.osmand.plus.plugins.nautical.map.controller.SailingMapLayerController

/**
 * Pane wrapper for the Navtex weather-warning HUD view, replicating the
 * previous initNavtexSystem wiring: uiState collection while STARTED feeding
 * both the HUD view and the navtex map layer.
 */
class NavtexPane(
    private val activity: MapActivity,
    private val controller: SailingMapLayerController,
    private val viewModel: NavtexViewModel
) : CockpitPane {
    override val paneId = "navtex"
    override val priority = 200
    override val requiredWhen: (net.osmand.plus.plugins.nautical.engine.MarineState) -> Boolean = { true }
    override val dataDependencies: List<kotlinx.coroutines.flow.StateFlow<*>> = listOf(viewModel.uiState)

    val hudView = NavtexHudView(activity)

    override fun buildView(activity: MapActivity): View = hudView

    override fun bind(view: View, scope: CoroutineScope) {
        scope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    hudView.updateState(state)
                    controller.navtexLayer.updateState(state)
                }
            }
        }
    }

    override fun unbind(view: View) {}
}
