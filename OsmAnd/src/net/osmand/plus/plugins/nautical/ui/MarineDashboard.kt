package net.osmand.plus.plugins.nautical.ui

import android.util.Log
import android.view.View
import net.osmand.plus.OsmandApplication
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.plugins.nautical.engine.SignalKEngine
import net.osmand.plus.views.mapwidgets.WidgetType
import net.osmand.plus.views.mapwidgets.WidgetsPanel
import net.osmand.plus.views.mapwidgets.widgets.TextInfoWidget
import net.osmand.plus.views.layers.base.OsmandMapLayer
import net.osmand.plus.R
import java.lang.reflect.Proxy

class MarineDashboard(
    private val app: OsmandApplication,
    private val engine: SignalKEngine
) {
    class MarineTextWidget(activity: MapActivity, type: WidgetType, id: String, panel: WidgetsPanel?) :
        TextInfoWidget(activity, type, id, panel) {
        override fun updateInfo(view: View, drawSettings: OsmandMapLayer.DrawSettings?) { }
        override fun updateInfo(drawSettings: OsmandMapLayer.DrawSettings?) { }
    }

    private var depthWidget: MarineTextWidget? = null
    private var windWidget: MarineTextWidget? = null

    private val marineStateListener: (MarineState) -> Unit = { state ->
        app.runInUIThread {
            state.depthBelowTransducer?.let { depthWidget?.setText(String.format("%.1f", it), "m") }
            state.windSpeedTrue?.let { windWidget?.setText(String.format("%.1f", it * 1.94384), "kn") }
        }
    }

    fun init(activity: MapActivity) {
        val registry = activity.mapLayers.mapWidgetRegistry
        val type = try { WidgetType.valueOf("INFO") } catch (e: Exception) { WidgetType.values().firstOrNull() ?: return }

        try {
            val registryClass = registry.javaClass
            // 1. Find registerWidget(WidgetType, WidgetCreator)
            val regMethod = registryClass.methods.find {
                it.name == "registerWidget" && it.parameterTypes.size == 2
            }

            if (regMethod != null) {
                // 2. Load WidgetCreator class dynamically, avoiding compile-time dependency
                val creatorClass = Class.forName("net.osmand.plus.views.mapwidgets.WidgetCreator")

                // 3. Create a Proxy to implement WidgetCreator at runtime
                val creator = Proxy.newProxyInstance(
                    creatorClass.classLoader,
                    arrayOf(creatorClass)
                ) { _, method, args ->
                    if (method.name == "createWidget") {
                        val wType = args[0] as WidgetType
                        val wId = args[1] as String
                        val wPanel = args[2] as WidgetsPanel?

                        if (wId == "nautical_depth") {
                            depthWidget = MarineTextWidget(activity, wType, wId, wPanel).apply {
                                setText("---", "m")
                                setIcons(R.drawable.ic_action_info_dark, R.drawable.ic_action_info_dark)
                            }
                            depthWidget
                        } else {
                            windWidget = MarineTextWidget(activity, wType, wId, wPanel).apply {
                                setText("---", "kn")
                                setIcons(R.drawable.ic_action_info_dark, R.drawable.ic_action_info_dark)
                            }
                            windWidget
                        }
                    } else null
                }

                regMethod.invoke(registry, type, creator)
                Log.d("NauticalPlugin", "Widgets registered via Dynamic Proxy")
            }
        } catch (e: Exception) {
            Log.e("NauticalPlugin", "Registration failed", e)
        }

        engine.registerListener(marineStateListener)
    }

    fun destroy() {
        engine.unregisterListener(marineStateListener)
        depthWidget = null
        windWidget = null
    }
}