package net.osmand.plus.plugins.nautical.maneuvers

import android.graphics.Color
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
import net.osmand.plus.views.mapwidgets.WidgetType
import net.osmand.plus.views.mapwidgets.WidgetsPanel
import net.osmand.plus.views.mapwidgets.widgets.MapWidget
import kotlin.time.Duration.Companion.seconds

class ManeuverOverlayWidget(
    mapActivity: MapActivity,
    val manager: ManeuverManager,
    widgetType: WidgetType,
    customId: String?,
    panel: WidgetsPanel?,
) : MapWidget(mapActivity, widgetType, customId, panel), ManeuverManager.ManeuverStateListener {

    private var statusText: TextView? = null
    private var headingText: TextView? = null
    private var windText: TextView? = null
    private var sailPlanText: TextView? = null
    private var executeBtn: Button? = null
    private var doneBtn: Button? = null
    private var cancelBtn: Button? = null
    private var collisionAlertText: TextView? = null
    private var touchLockText: TextView? = null
    private var progressBar: android.widget.ProgressBar? = null
    
    private var feedbackJob: kotlinx.coroutines.Job? = null

    init {
        manager.registerListener(this)
        startObservingData()
    }

    override fun getLayoutId(): Int = R.layout.widget_maneuver_overlay

    override fun setupView(view: View) {
        statusText = view.findViewById(R.id.status_text)
        headingText = view.findViewById(R.id.heading_text)
        windText = view.findViewById(R.id.wind_text)
        sailPlanText = view.findViewById(R.id.sail_plan_text)
        executeBtn = view.findViewById(R.id.btn_execute)
        doneBtn = view.findViewById(R.id.btn_done)
        cancelBtn = view.findViewById(R.id.btn_cancel)
        collisionAlertText = view.findViewById(R.id.collision_alert_text)
        touchLockText = view.findViewById(R.id.touch_lock_text)
        progressBar = view.findViewById(R.id.maneuver_progress)

        executeBtn?.setOnClickListener {
            manager.execute()
        }
        doneBtn?.setOnClickListener {
            manager.completeActiveManeuver()
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
                    headingText?.text = hdg?.let { mapActivity.getString(R.string.nautical_hdg_label, it.toInt()) } ?: mapActivity.getString(R.string.nautical_hdg_no_data)
                }
            }
        }
        mapActivity.lifecycleScope.launch {
            broker.windAngleApparent.collectLatest { awa ->
                mapActivity.runOnUiThread {
                    windText?.text = awa?.let { mapActivity.getString(R.string.nautical_awa_label, it.toInt()) } ?: mapActivity.getString(R.string.nautical_awa_no_data)
                }
            }
        }
        
        mapActivity.lifecycleScope.launch {
            NauticalPlugin.engine?.marineStateFlow?.collectLatest { state ->
                mapActivity.runOnUiThread {
                    state.activeSailPlan?.let { plan ->
                        sailPlanText?.visibility = View.VISIBLE
                        sailPlanText?.text = mapActivity.getString(R.string.nautical_sail_plan_label, plan)
                    } ?: run {
                        sailPlanText?.visibility = View.GONE
                    }
                }
            }
        }

        // Observe cpa/tcpa directly from broker
        mapActivity.lifecycleScope.launch {
            broker.cpa.collectLatest { cpa ->
                broker.tcpa.collectLatest { tcpa ->
                    broker.threatName.collectLatest { name ->
                        mapActivity.runOnUiThread {
                            if ((cpa != null) && (tcpa != null) && (cpa < 0.5) && (tcpa <= 180.0)) {
                                collisionAlertText?.visibility = View.VISIBLE
                                val nm = mapActivity.getString(R.string.nautical_unit_nm)
                                val alert = mapActivity.getString(R.string.nautical_collision_alert)
                                val vesselName = name ?: mapActivity.getString(R.string.nautical_target_vessel)
                                val details = mapActivity.getString(R.string.nautical_cpa_tcpa_format, cpa, nm, tcpa.toInt())
                                collisionAlertText?.text = mapActivity.getString(R.string.nautical_collision_alert_full_format, alert, vesselName, details)
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

        mapActivity.lifecycleScope.launch {
            val lockManager = NauticalPlugin.getInstance()?.workflowManager?.getScreenTouchLockManager()
            lockManager?.isTouchLockActive?.collectLatest { active ->
                mapActivity.runOnUiThread {
                    touchLockText?.visibility = if (active) View.VISIBLE else View.GONE
                    touchLockText?.text = if (active) mapActivity.getString(R.string.nautical_touch_lock_active) else ""
                }
            }
        }
    }

    private fun updateSize(view: View) {
        val screenHeight = mapActivity.resources.displayMetrics.heightPixels
        val targetHeight = (screenHeight * 0.22).toInt() // Slightly larger for instructions
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
        statusText?.setTextColor(textColor)
        headingText?.setTextColor(accentColor)
        windText?.setTextColor(accentColor)
        
        executeBtn?.visibility = if (manager.state == ManeuverState.ARMED) View.VISIBLE else View.GONE
        if (manager.state == ManeuverState.ARMED) {
            val maneuverId = manager.activeManeuver?.let { manager.getManeuverId(it) } ?: "maneuver"
            val maneuverName = maneuverId.replace("_", " ").uppercase()
            val executeLabel = mapActivity.getString(R.string.maneuver_execute).uppercase()
            executeBtn?.text = "$executeLabel $maneuverName"
            executeBtn?.setBackgroundColor(0xFF00C853.toInt()) // High-Contrast Green
            executeBtn?.setTextColor(Color.WHITE)
        }
        doneBtn?.visibility = if (manager.state == ManeuverState.EXECUTING) View.VISIBLE else View.GONE
        progressBar?.visibility = if (manager.state == ManeuverState.EXECUTING) View.VISIBLE else View.GONE
        
        if (manager.state == ManeuverState.IDLE) {
            statusText?.text = mapActivity.getString(R.string.nautical_maneuver_state_idle)
            progressBar?.progress = 0
            feedbackJob?.cancel()
        }
    }

    override fun onStateChanged(newState: ManeuverState) {
        mapActivity.runOnUiThread {
            updateUI()
            if (newState == ManeuverState.EXECUTING) {
                startObservingManeuverFeedback()
            } else if (newState == ManeuverState.IDLE) {
                val reason = manager.lastAbortReason
                if (reason != null && reason != "User cancelled") {
                    statusText?.text = mapActivity.getString(R.string.nautical_maneuver_aborted_format, reason)
                    statusText?.setTextColor(0xFFFF0000.toInt()) // Red
                    updateVisibility(true)
                    // Auto-hide after 5 seconds
                    mapActivity.lifecycleScope.launch {
                        kotlinx.coroutines.delay(5.seconds)
                        if (manager.state == ManeuverState.IDLE) {
                            updateUI()
                        }
                    }
                }
            }
        }
    }
    
    private fun startObservingManeuverFeedback() {
        feedbackJob?.cancel()
        val engine = manager.activeManeuver ?: return
        
        feedbackJob = mapActivity.lifecycleScope.launch {
            launch {
                engine.instructionFlow.collectLatest { text ->
                    mapActivity.runOnUiThread {
                        statusText?.text = text ?: manager.state.name
                    }
                }
            }
            launch {
                engine.progressFlow.collectLatest { progress ->
                    mapActivity.runOnUiThread {
                        progressBar?.progress = progress
                    }
                }
            }
        }
    }
}
