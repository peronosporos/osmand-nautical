package net.osmand.plus.plugins.nautical.ui.cockpit

import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.plugins.nautical.map.controller.SailingMapLayerController
import net.osmand.plus.plugins.nautical.mob.ui.MobEmergencyHeaderView
import net.osmand.plus.plugins.nautical.mob.viewmodel.MobViewModel

/**
 * Pane wrapper for the MOB emergency header (step 2 of the Cockpit plan).
 *
 * Replicates the exact behavior of the previous wiring in
 * NauticalUiOverlayManager.initMobSystem: mounts the header at emergency
 * priority, collects MobViewModel.uiState while STARTED, updates the header and
 * the MOB map layer, and manages the keep-screen-on flag.
 */
class MobEmergencyPane(
    private val activity: MapActivity,
    private val controller: SailingMapLayerController,
    private val viewModel: MobViewModel
) : CockpitPane {

    override val paneId = "mob_emergency"
    override val priority = 0
    override val requiredWhen: (MarineState) -> Boolean = { true }
    override val dataDependencies: List<kotlinx.coroutines.flow.StateFlow<*>> = listOf(viewModel.uiState)

    val headerView = MobEmergencyHeaderView(activity).also { it.setViewModel(viewModel) }

    override fun buildView(activity: MapActivity): View = headerView

    override fun bind(view: View, scope: CoroutineScope) {
        scope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    headerView.updateState(state)
                    controller.mobLayer.updateState(state)
                    if (state.isMobActive) {
                        activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else if (activity.app.settings.USE_SYSTEM_SCREEN_TIMEOUT.get()) {
                        activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }
            }
        }
    }

    override fun unbind(view: View) {
        // Pane scope is cancelled right after this; collectors stop. If MOB is not
        // active and the system timeout applies, release the keep-screen-on flag.
        if (!viewModel.uiState.value.isMobActive &&
            activity.app.settings.USE_SYSTEM_SCREEN_TIMEOUT.get()
        ) {
            activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
