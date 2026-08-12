package net.osmand.plus.plugins.nautical.maneuvers

import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.ui.SlideToConfirmView
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
    private var slideExecute: SlideToConfirmView? = null
    private var slideDone: SlideToConfirmView? = null
    private var cancelBtn: Button? = null
    private var collisionAlertText: TextView? = null
    private var touchLockText: TextView? = null
    private var progressBar: android.widget.ProgressBar? = null
    private var maneuverIcon: android.widget.ImageView? = null
    
    private var feedbackJob: kotlinx.coroutines.Job? = null
    private var showingAbortReason = false

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
        slideExecute = view.findViewById(R.id.slide_execute)
        slideDone = view.findViewById(R.id.slide_done)
        cancelBtn = view.findViewById(R.id.btn_cancel)
        collisionAlertText = view.findViewById(R.id.collision_alert_text)
        touchLockText = view.findViewById(R.id.touch_lock_text)
        progressBar = view.findViewById(R.id.maneuver_progress)
        maneuverIcon = view.findViewById(R.id.maneuver_icon)

        executeBtn?.setOnClickListener {
            manager.execute()
        }
        slideExecute?.onConfirm = {
            manager.execute()
        }
        slideDone?.onConfirm = {
            manager.completeActiveManeuver()
        }
        
        cancelBtn?.setOnClickListener {
            manager.abort()
        }

        touchLockText?.setOnLongClickListener {
            val lockManager = NauticalPlugin.getInstance()?.workflowManager?.getScreenTouchLockManager()
            lockManager?.setTouchLockActive(active = false)
            mapActivity.app.showToastMessage(R.string.nautical_touch_lock_emergency_unlock)
            true
        }

        updateSize(view)
        updateUI()
    }

    private fun startObservingData() {
        val broker = NauticalPlugin.engine?.dataBroker ?: return
        mapActivity.lifecycleScope.launch {
            broker.headingTrue.collectLatest { hdg ->
                headingText?.text = hdg?.let { mapActivity.getString(R.string.nautical_hdg_label, it.toInt()) } ?: mapActivity.getString(R.string.nautical_hdg_no_data)
            }
        }
        mapActivity.lifecycleScope.launch {
            broker.windAngleApparent.collectLatest { awa ->
                windText?.text = awa?.let { mapActivity.getString(R.string.nautical_awa_label, it.toInt()) } ?: mapActivity.getString(R.string.nautical_awa_no_data)
            }
        }
        
        mapActivity.lifecycleScope.launch {
            NauticalPlugin.engine?.marineStateFlow?.collectLatest { state ->
                state.activeSailPlan?.let { plan ->
                    sailPlanText?.visibility = View.VISIBLE
                    sailPlanText?.text = mapActivity.getString(R.string.nautical_sail_plan_label, plan)
                } ?: run {
                    sailPlanText?.visibility = View.GONE
                }
            }
        }

        // Item 10 Fix: Combined observation to prevent collection churn
        mapActivity.lifecycleScope.launch {
            combine(broker.cpa, broker.tcpa, broker.threatName) { cpa, tcpa, name ->
                Triple(cpa, tcpa, name)
            }.collectLatest { (cpa, tcpa, name) ->
                val isDocking = manager.activeManeuver is DockingManeuver
                val dockingAlert = isDocking && (cpa != null) && (cpa < 0.1)
                val standardAlert = (cpa != null) && (tcpa != null) && (cpa < 0.5) && (tcpa <= 180.0)

                if (standardAlert || dockingAlert) {
                    collisionAlertText?.visibility = View.VISIBLE
                    val nm = mapActivity.getString(R.string.nautical_unit_nm)
                    val alert = mapActivity.getString(R.string.nautical_collision_alert)
                    val vesselName = name ?: mapActivity.getString(R.string.nautical_target_vessel)
                    val details = mapActivity.getString(R.string.nautical_cpa_tcpa_format, cpa, nm, tcpa?.toInt() ?: 0)
                    collisionAlertText?.text = mapActivity.getString(R.string.nautical_collision_alert_full_format, alert, vesselName, details)
                    
                    // Item 13 Fix: Collision alerts are always visible if this widget is active, regardless of maneuver state
                    updateVisibility(true)
                } else {
                    collisionAlertText?.visibility = View.GONE
                    if (manager.state == ManeuverState.IDLE && !showingAbortReason) {
                        updateVisibility(false)
                    } else {
                        updateUI()
                    }
                }
            }
        }

        mapActivity.lifecycleScope.launch {
            val lockManager = NauticalPlugin.getInstance()?.workflowManager?.getScreenTouchLockManager()
            lockManager?.isTouchLockActive?.collectLatest { active ->
                touchLockText?.visibility = if (active) View.VISIBLE else View.GONE
                if (active) {
                    touchLockText?.text = mapActivity.getString(
                        R.string.nautical_touch_lock_active_full, 
                        mapActivity.getString(R.string.nautical_touch_lock_active), 
                        mapActivity.getString(R.string.nautical_touch_lock_emergency_unlock)
                    )
                }
            }
        }
    }

    private fun updateSize(view: View) {
        val lp = view.layoutParams ?: ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
        view.layoutParams = lp
    }

    private fun updateUI() {
        if (showingAbortReason && manager.state == ManeuverState.IDLE) return

        val activeManeuver = manager.activeManeuver
        val visible = manager.state != ManeuverState.IDLE
        updateVisibility(visible)

        val textColor = getThemeColor(R.attr.nautical_status_yellow)
        val accentColor = getThemeColor(R.attr.nautical_status_green)
        val bgColor = getThemeColor(R.attr.nautical_widget_background)

        view.setBackgroundColor(bgColor) 
        statusText?.setTextColor(textColor)
        headingText?.setTextColor(accentColor)
        windText?.setTextColor(accentColor)
        
        if (manager.state == ManeuverState.ARMED && activeManeuver != null) {
            val maneuverName = mapActivity.getString(activeManeuver.displayNameRes)
            updateIcon(activeManeuver.iconRes)
            
            if (activeManeuver.isHighRisk) {
                executeBtn?.visibility = View.GONE
                slideExecute?.apply {
                    visibility = View.VISIBLE
                    label = mapActivity.getString(R.string.maneuver_execute).uppercase() + " " + maneuverName.uppercase()
                    reset()
                }
            } else {
                slideExecute?.visibility = View.GONE
                executeBtn?.apply {
                    visibility = View.VISIBLE
                    text = mapActivity.getString(R.string.nautical_maneuver_execute_name, maneuverName)
                    setBackgroundColor(accentColor)
                    setTextColor(Color.BLACK)
                }
            }
        } else {
            executeBtn?.visibility = View.GONE
            slideExecute?.visibility = View.GONE
        }
        
        slideDone?.visibility = if (manager.state == ManeuverState.EXECUTING) View.VISIBLE else View.GONE
        if (manager.state == ManeuverState.EXECUTING) {
             slideDone?.reset()
             // Item 11: Dynamic Slide label based on maneuver type
             slideDone?.label = when (activeManeuver) {
                 is DockingManeuver -> mapActivity.getString(R.string.nautical_docking_slide_to_complete)
                 is AnchoringManeuver -> mapActivity.getString(R.string.nautical_anchoring_slide_to_set)
                 else -> mapActivity.getString(R.string.nautical_maneuver_slide_to_complete)
             }
        }
        progressBar?.visibility = if (manager.state == ManeuverState.EXECUTING) View.VISIBLE else View.GONE
        
        if (manager.state == ManeuverState.IDLE) {
            statusText?.text = mapActivity.getString(R.string.nautical_maneuver_state_idle)
            maneuverIcon?.visibility = View.GONE
            feedbackJob?.cancel()
        }
    }

    private fun updateIcon(iconRes: Int) {
        maneuverIcon?.setImageResource(iconRes)
        maneuverIcon?.visibility = View.VISIBLE
    }

    private fun getThemeColor(attrId: Int): Int {
        val typedValue = android.util.TypedValue()
        mapActivity.theme.resolveAttribute(attrId, typedValue, true)
        return typedValue.data
    }

    override fun onStateChanged(newState: ManeuverState) {
        mapActivity.runOnUiThread {
            updateUI()
            if (newState == ManeuverState.EXECUTING) {
                startObservingManeuverFeedback()
            } else if (newState == ManeuverState.IDLE) {
                val reason = manager.lastAbortReason
                if (reason != null && reason != "User cancelled") {
                    showingAbortReason = true
                    statusText?.text = mapActivity.getString(R.string.nautical_maneuver_aborted_format, reason)
                    statusText?.setTextColor(getThemeColor(R.attr.nautical_status_red))
                    updateVisibility(true)

                    mapActivity.lifecycleScope.launch {
                        kotlinx.coroutines.delay(5.seconds)
                        showingAbortReason = false
                        if (manager.state == ManeuverState.IDLE) {
                             updateUI()
                        }
                    }
                }
                
                // UX Item 14: Keep progress visible for 2 seconds after completion
                mapActivity.lifecycleScope.launch {
                    kotlinx.coroutines.delay(2.seconds)
                    if (manager.state == ManeuverState.IDLE) {
                        progressBar?.progress = 0
                        progressBar?.visibility = View.GONE
                        if (reason == null || reason == "User cancelled") {
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
