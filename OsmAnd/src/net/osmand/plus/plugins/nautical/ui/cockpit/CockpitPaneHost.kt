package net.osmand.plus.plugins.nautical.ui.cockpit

import android.view.View
import androidx.annotation.UiThread
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.NauticalHudManager
import net.osmand.plus.plugins.nautical.engine.MarineState

/**
 * Shell that mounts CockpitPanes into the existing NauticalHudManager header
 * arbitration. This is the step-1 additive layer of the Cockpit plan
 * (docs/nautical/COCKPIT.md): it currently coexists with the legacy
 * initXSystem wirings and changes no behavior until panes register with it.
 *
 * Responsibilities:
 * - mounts/unmounts panes when their requiredWhen flips,
 * - one CoroutineScope per pane mount, cancelled on unmount (no leaked collectors),
 * - re-applies the pane's header priority through NauticalHudManager so
 *   arbitration, compact mode and night-vision theming stay central.
 */
class CockpitPaneHost(
    private val activity: MapActivity,
    private val hudManager: NauticalHudManager
) {
    private class MountedPane(
        val pane: CockpitPane,
        val view: View,
        val scope: CoroutineScope
    )

    private val panes = ConcurrentHashMap<String, CockpitPane>()
    private val mounted = ConcurrentHashMap<String, MountedPane>()
    private val dependencyScopes = ConcurrentHashMap<String, CoroutineScope>()
    private var lastKnownState: MarineState? = null

    /**
     * Register a pane. Data dependencies start being observed immediately;
     * mounting happens as soon as requiredWhen evaluates true for the last
     * known MarineState (or for the next state once one arrives).
     */
    fun register(pane: CockpitPane) {
        if (panes.putIfAbsent(pane.paneId, pane) != null) return
        val depScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        dependencyScopes[pane.paneId] = depScope
        pane.dataDependencies.forEach { flow ->
            flow.onEach { scheduleEval(pane) }.launchIn(depScope)
        }
        scheduleEval(pane)
    }

    fun unregister(pane: CockpitPane) {
        panes.remove(pane.paneId)
        dependencyScopes.remove(pane.paneId)?.cancel()
        unmount(pane.paneId)
    }

    /** Feed the current engine state; triggers requiredWhen evaluation. */
    fun onMarineStateChanged(state: MarineState) {
        lastKnownState = state
        evaluateAll()
    }

    /**
     * The display-default state is used until the first real state arrives, so
     * EMERGENCY panes with constant requiredWhen (MOB, alarm banner) mount
     * immediately even if no SignalK connection has produced data yet.
     */
    private fun currentState(): MarineState = lastKnownState ?: MarineState()

    fun evaluateAll() {
        val state = currentState()
        panes.values.forEach { pane ->
            val shouldMount = try {
                pane.requiredWhen(state)
            } catch (t: Exception) {
                false
            }
            if (shouldMount) mountIfAbsent(pane) else unmount(pane.paneId)
        }
    }

    private fun scheduleEval(pane: CockpitPane) {
        if (activity.isFinishing || activity.isDestroyed) return
        activity.runOnUiThread { evaluateAll() }
    }

    @UiThread
    private fun mountIfAbsent(pane: CockpitPane) {
        if (mounted.containsKey(pane.paneId) ||
            activity.isFinishing || activity.isDestroyed
        ) return
        val view = pane.buildView(activity)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        mounted[pane.paneId] = MountedPane(pane, view, scope)
        hudManager.addHeader(view, priority = pane.priority)
        pane.bind(view, scope)
    }

    @UiThread
    private fun unmount(paneId: String) {
        val record = mounted.remove(paneId) ?: return
        try {
            record.pane.unbind(record.view)
        } catch (_: Exception) {
        }
        record.scope.cancel()
        hudManager.removeHeader(record.view)
    }

    fun isMounted(paneId: String): Boolean = mounted.containsKey(paneId)

    /**
     * Destroy all mounts and stop listening. Called from NauticalHudManager.onDestroy.
     */
    fun destroy() {
        mounted.keys.toList().forEach { unmount(it) }
        dependencyScopes.values.forEach { it.cancel() }
        dependencyScopes.clear()
        panes.clear()
    }
}
