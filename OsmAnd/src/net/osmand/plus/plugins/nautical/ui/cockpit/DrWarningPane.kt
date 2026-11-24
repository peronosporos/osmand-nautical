package net.osmand.plus.plugins.nautical.ui.cockpit

import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.dr.ui.DrWarningHeaderView
import net.osmand.plus.plugins.nautical.dr.viewmodel.DeadReckoningViewModel
import net.osmand.plus.plugins.nautical.map.controller.SailingMapLayerController

/**
 * Pane wrapper for the Dead-Reckoning drift warning header, replicating the
 * previous initDrSystem wiring: uiState collection while STARTED feeding both
 * the header and the DR map layer.
 */
class DrWarningPane(
    private val activity: MapActivity,
    private val controller: SailingMapLayerController,
    private val viewModel: DeadReckoningViewModel
) : CockpitPane {
    override val paneId = "dr_warning"
    override val priority = 100
    override val requiredWhen: (net.osmand.plus.plugins.nautical.engine.MarineState) -> Boolean = { true }
    override val dataDependencies: List<kotlinx.coroutines.flow.StateFlow<*>> = listOf(viewModel.uiState)

    val headerView = DrWarningHeaderView(activity)

    override fun buildView(activity: MapActivity): View = headerView

    override fun bind(view: View, scope: CoroutineScope) {
        scope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    headerView.updateState(state)
                    controller.drLayer.updateState(state)
                }
            }
        }
    }

    override fun unbind(view: View) {}
}
