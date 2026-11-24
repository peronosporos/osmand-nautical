package net.osmand.plus.plugins.nautical.ui.cockpit

import android.view.View
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.engine.MarineState

/**
 * Generic pane for a self-sufficient HUD view: the view manages its own
 * visibility and data internally, so the pane only supplies identity and
 * priority. Mount = addHeader, unmount = removeHeader.
 */
class SimpleViewPane(
    override val paneId: String,
    override val priority: Int,
    private val viewProvider: (MapActivity) -> View,
    override val dataDependencies: List<StateFlow<*>> = emptyList(),
    private val onBind: ((View, CoroutineScope) -> Unit)? = null,
    private val onUnbind: ((View) -> Unit)? = null
) : CockpitPane {
    override val requiredWhen: (MarineState) -> Boolean = { true }
    private var cached: View? = null

    override fun buildView(activity: MapActivity): View {
        val v = cached ?: viewProvider(activity)
        cached = v
        return v
    }

    override fun bind(view: View, scope: CoroutineScope) {
        onBind?.invoke(view, scope)
    }

    override fun unbind(view: View) {
        onUnbind?.invoke(view)
    }
}
