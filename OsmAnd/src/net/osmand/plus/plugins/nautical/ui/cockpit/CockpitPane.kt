package net.osmand.plus.plugins.nautical.ui.cockpit

import android.view.View
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.engine.MarineState

/**
 * Cockpit pane contract (see docs/nautical/COCKPIT.md).
 *
 * A pane is a self-contained cockpit surface (MOB takeover, DR warning, tactical
 * strip, autopilot header, ...). The shell ([CockpitPaneHost], driven by
 * [net.osmand.plus.plugins.nautical.NauticalHudManager]) owns mounting, lifecycle,
 * theming and arbitration; panes only describe what they need and how to build
 * and bind their view.
 *
 * Lifecycle: buildView is called once per mount; the pane-scoped [CoroutineScope]
 * passed to bind is cancelled on unmount. requiredWhen is re-evaluated whenever
 * any flow in [stateFlow] or [dataDependencies] emits.
 *
 * IMPORTANT contract rules:
 * - bind must start all flow collection on the provided scope; never on a
 *   global scope, otherwise unmount leaks collectors.
 * - unbind must leave the view in a neutral state; the shell may reuse a view
 *   built for a previous mount of the same pane.
 * - Panes must not call NauticalHudManager.addHeader/removeHeader themselves:
 *   the shell registers the pane view with the header arbitrator using
 *   [priority], so priority arbitration and compact-mode logic stay central.
 */
interface CockpitPane {
    /** Stable identifier, used for logs, diagnostics view and view reuse. */
    val paneId: String

    /**
     * Header priority, same scale as NauticalHudManager.addHeader
     * (lower = higher precedence, 0 = emergency).
     */
    val priority: Int

    /**
     * The single source of required-ness. The pane should be mounted while this
     * returns true. MOB takeover panes may return a constant true; visibility
     * itself can be controlled inside the pane view.
     */
    val requiredWhen: (MarineState) -> Boolean

    /**
     * Flows whose emission should trigger requiredWhen re-evaluation. The first
     * entry should be the MarineState flow when available.
     */
    val dataDependencies: List<StateFlow<*>>

    /** Build (or return a cached) view for this mount. Called on the UI thread. */
    fun buildView(activity: MapActivity): View

    /** Observe data and update the view. collectors must use the given scope. */
    fun bind(view: View, scope: CoroutineScope)

    /** Stop pane work. The shell cancels the scope afterwards as well. */
    fun unbind(view: View)
}
