package net.osmand.plus.plugins.nautical.maneuvers

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.SignalKDataBroker
import net.osmand.plus.views.mapwidgets.WidgetType
import net.osmand.plus.views.mapwidgets.WidgetsPanel
import net.osmand.plus.views.mapwidgets.widgets.MapWidget
import java.util.Locale

class ManeuverOverlayWidget(
    mapActivity: MapActivity,
    val manager: ManeuverManager,
    widgetType: WidgetType,
    customId: String?,
    panel: WidgetsPanel?
) : MapWidget(mapActivity, widgetType, customId, panel), ManeuverManager.ManeuverStateListener {

    private var statusText: TextView? = null
    private var headingText: TextView? = null
    private var windText: TextView? = null
    private var executeBtn: Button? = null
    private var cancelBtn: Button? = null
    private var collisionAlertText: TextView? = null
    private var touchLockText: TextView? = null

    init {
        manager.registerListener(this)
        startObservingData()
    }

    override fun getLayoutId(): Int = R.layout.widget_maneuver_overlay

    override fun setupView(view: View) {
        statusText = view.findViewById(R.id.status_text)
        headingText = view.findViewById(R.id.heading_text)
        windText = view.findViewById(R.id.wind_text)
        executeBtn = view.findViewById(R.id.btn_execute)
        cancelBtn = view.findViewById(R.id.btn_cancel)
        collisionAlertText = view.findViewById(R.id.collision_alert_text)
        touchLockText = view.findViewById(R.id.touch_lock_text)

        executeBtn?.setOnClickListener {
            manager.execute()
        }
        cancelBtn?.setOnClickListener {
            manager.abort()
        }

        updateSize(view)
        updateUI()
    }

    private fun startObservingData() {
        val broker = NauticalPlugin.engine?.dataBroker ?: return
        mapActivity.lifecycleScope.launch {
            broker.headingTrue.collectLatest { hdg ->
                mapActivity.runOnUiThread {
                    headingText?.text = hdg?.let { mapActivity.getString(R.string.nautical_hdg_label, it.toInt()) } ?: "${mapActivity.getString(R.string.nautical_hdg)}: --°"
                }
            }
        }
        mapActivity.lifecycleScope.launch {
            broker.windAngleApparent.collectLatest { awa ->
                mapActivity.runOnUiThread {
                    windText?.text = awa?.let { mapActivity.getString(R.string.nautical_awa_label, it.toInt()) } ?: "${mapActivity.getString(R.string.nautical_awa)}: --°"
                }
            }
        }
        // Observe cpa/tcpa directly from broker
        mapActivity.lifecycleScope.launch {
            broker.cpa.collectLatest { cpa ->
                broker.tcpa.collectLatest { tcpa ->
                    mapActivity.runOnUiThread {
                        if (cpa != null && tcpa != null && cpa < 0.5 && tcpa <= 180.0) {
                            collisionAlertText?.visibility = View.VISIBLE
                            val nm = mapActivity.getString(R.string.nautical_unit_nm)
                            val alert = mapActivity.getString(R.string.nautical_collision_alert)
                            val details = mapActivity.getString(R.string.nautical_cpa_tcpa_format, cpa, nm, tcpa.toInt())
                            collisionAlertText?.text = "$alert\n$details"
                            // Force widget visible even if manager state is IDLE when collision warning triggers!
                            updateVisibility(true)
                        } else {
                            collisionAlertText?.visibility = View.GONE
                            updateUI()
                        }
                    }
                }
            }
        }
    }

    private fun updateSize(view: View) {
        val screenHeight = mapActivity.resources.displayMetrics.heightPixels
        val targetHeight = (screenHeight * 0.20).toInt()
        val lp = view.layoutParams ?: ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, targetHeight)
        lp.height = targetHeight
        view.layoutParams = lp
    }

    private fun updateUI() {
        val visible = manager.state != ManeuverState.IDLE
        updateVisibility(visible)

        val isNight = NauticalPlugin.isNightVision(mapActivity.applicationContext as? net.osmand.plus.OsmandApplication)
        val backgroundColor = 0xFF000000.toInt()
        val textColor = if (isNight) 0xFFFF0000.toInt() else 0xFFFFFF00.toInt()
        val accentColor = if (isNight) 0xFFFF0000.toInt() else 0xFF00FF00.toInt()

        view.setBackgroundColor(backgroundColor)
        statusText?.text = manager.state.name
        statusText?.setTextColor(textColor)
        headingText?.setTextColor(accentColor)
        windText?.setTextColor(accentColor)
        
        executeBtn?.visibility = if (manager.state == ManeuverState.ARMED) View.VISIBLE else View.GONE
    }

    override fun onStateChanged(newState: ManeuverState) {
        mapActivity.runOnUiThread {
            updateUI()
        }
    }
}
