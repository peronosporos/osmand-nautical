package net.osmand.plus.plugins.nautical.ui.cockpit

import net.osmand.plus.OsmandApplication

/**
 * Registry of cockpit panes. The shell asks the registry which concerns are available
 * and mounts one pane at a time; the declaration order defines the selector order.
 *
 * Panes are registered once, duplicates are rejected loudly (an ID collision would
 * silently hide one panel — that must never pass unnoticed).
 */
class CockpitScreenRegistry {

    private val panes = LinkedHashMap<CockpitScreenId, CockpitScreen>()

    fun register(pane: CockpitScreen) {
        require(pane.paneId !in panes) {
            "Cockpit pane already registered for concern ${pane.paneId}"
        }
        panes[pane.paneId] = pane
    }

    fun available(): List<CockpitScreenId> = panes.keys.toList()

    fun pane(id: CockpitScreenId): CockpitScreen? = panes[id]

    fun isEmpty(): Boolean = panes.isEmpty()

    companion object {
        /**
         * The production registry. Panes migrate here from the legacy HUD view system
         * one by one; Instruments is the pilot pane.
         */
        fun createDefault(app: OsmandApplication): CockpitScreenRegistry {
            val registry = CockpitScreenRegistry()
            registry.register(InstrumentsPane(app))
            registry.register(HelmScreen(app))
            registry.register(WatchkeeperScreen(app))
            registry.register(NavScreen(app))
            return registry
        }
    }
}
