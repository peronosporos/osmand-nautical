package net.osmand.plus.plugins.nautical.ui.cockpit

import android.content.Context
import android.view.View
import android.view.ViewGroup

/**
 * Full-screen Cockpit shell page contract (companion to [CockpitPane]).
 *
 * [CockpitPane] is the HUD-header contract mounted by [CockpitPaneHost] inside the
 * map activity; [CockpitScreen] is the full-screen contract mounted by
 * [CockpitShellActivity]. They are deliberately separate: headers need
 * MapActivity integration and priority arbitration, full-screen pages need
 * plain context views and their own lifecycle bracket.
 *
 * Lifecycle: onCreateView is called once per mount into a fresh container;
 * onStart starts data collection (pane-scoped coroutines), onStop cancels all
 * pane work. The shell never reuses a stopped screen instance without calling
 * onStart again.
 */
interface CockpitScreen {
    /** Stable identifier; also defines the selector slot in the shell. */
    val paneId: CockpitScreenId

    /** Build the page view for this mount. Called on the UI thread. */
    fun onCreateView(context: Context, parent: ViewGroup): View

    /** Start observing data and updating the mounted view. */
    fun onStart()

    /** Stop all pane work; the shell drops the view afterwards. */
    fun onStop()
}

/** Full-screen cockpit pages. Order defines the selector order in the shell. */
enum class CockpitScreenId {
    INSTRUMENTS,
    HELM,
    NAV,
    VHF,
    WATCHKEEPER,
    EMERGENCY
}
