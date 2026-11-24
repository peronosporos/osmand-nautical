package net.osmand.plus.plugins.nautical.ui.cockpit

import android.view.View
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.plugins.nautical.engine.NauticalWorkflowManager
import net.osmand.plus.plugins.nautical.engine.SailingWorkflowEngine
import net.osmand.plus.plugins.nautical.engine.SailingWorkflowState
import net.osmand.plus.plugins.nautical.ui.ScreenTouchLockHudView
import net.osmand.plus.plugins.nautical.ui.TacticalHudView
import net.osmand.plus.plugins.nautical.ui.WorkflowHeaderView

/**
 * Panes replacing the previous initWorkflowSystem wiring (step 4a of
 * docs/nautical/COCKPIT.md): phone branch becomes the workflow proposal,
 * tactical strip and touch-lock panes; the Wear OS branch uses HeartbeatPane.
 * Behavior is copied verbatim from the old wiring; only the collect scopes
 * move from activity lifecycleScope into pane mount scopes.
 */

class WorkflowProposalPane(
    private val activity: MapActivity,
    private val workflowEngine: SailingWorkflowEngine,
    private val onStateChange: () -> Unit
) : CockpitPane {
    override val paneId = "workflow_proposal"
    override val priority = 300
    override val requiredWhen: (MarineState) -> Boolean = { true }
    private val pendingFlow get() = workflowEngine.pendingWorkflowFlow
    override val dataDependencies: List<StateFlow<*>> = emptyList()

    val headerView = WorkflowHeaderView(activity).also { it.setEngine(workflowEngine) }

    override fun buildView(activity: MapActivity): View = headerView

    override fun bind(view: View, scope: CoroutineScope) {
        scope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    pendingFlow.collect { state ->
                        if (state != null) headerView.showProposal(state) else headerView.isVisible = false
                        onStateChange()
                    }
                }
            }
        }
    }

    override fun unbind(view: View) {}
}

class TacticalStripPane(
    private val activity: MapActivity,
    private val workflowEngine: SailingWorkflowEngine,
    private val onStateChange: () -> Unit
) : CockpitPane {
    override val paneId = "tactical_strip"
    override val priority = 150
    override val requiredWhen: (MarineState) -> Boolean = { true }
    override val dataDependencies: List<StateFlow<*>> = listOf(workflowEngine.currentWorkflow)

    val stripView = TacticalHudView(activity)

    override fun buildView(activity: MapActivity): View = stripView

    override fun bind(view: View, scope: CoroutineScope) {
        scope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                workflowEngine.currentWorkflow.collect { state ->
                    stripView.isVisible = (state == SailingWorkflowState.CLOSE_QUARTERS)
                    onStateChange()
                }
            }
        }
    }

    override fun unbind(view: View) {}
}

class ScreenTouchLockPane(
    private val activity: MapActivity,
    private val workflowManager: NauticalWorkflowManager?,
    private val onStateChange: () -> Unit
) : CockpitPane {
    override val paneId = "screen_touch_lock"
    override val priority = 5
    override val requiredWhen: (MarineState) -> Boolean = { true }
    override val dataDependencies: List<StateFlow<*>> =
        listOfNotNull(workflowManager?.getScreenTouchLockManager()?.isTouchLockActive)

    val lockView = ScreenTouchLockHudView(activity)

    override fun buildView(activity: MapActivity): View = lockView

    override fun bind(view: View, scope: CoroutineScope) {
        val lockManager = workflowManager?.getScreenTouchLockManager() ?: return
        scope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    lockManager.isTouchLockActive.collect { locked ->
                        lockView.setLocked(locked)
                        onStateChange()
                    }
                }
                launch {
                    lockManager.unlockProgress.collect { progress ->
                        lockView.setUnlockProgress(progress)
                    }
                }
            }
        }
    }

    override fun unbind(view: View) {}
}

class HeartbeatPane(
    private val activity: MapActivity,
    private val wearOsManager: net.osmand.plus.plugins.nautical.WearOsNauticalManager
) : CockpitPane {
    override val paneId = "wear_heartbeat"
    override val priority = 50
    override val requiredWhen: (MarineState) -> Boolean = { true }
    override val dataDependencies: List<StateFlow<*>> = emptyList()

    val heartbeatView = net.osmand.plus.plugins.nautical.ui.HeartbeatHudView(activity)

    override fun buildView(activity: MapActivity): View = heartbeatView

    override fun bind(view: View, scope: CoroutineScope) {
        wearOsManager.isAmbientMode.onEach { ambient ->
            activity.app.runInUIThread { heartbeatView.setAmbientMode(ambient) }
        }.launchIn(scope)
    }

    override fun unbind(view: View) {}
}
