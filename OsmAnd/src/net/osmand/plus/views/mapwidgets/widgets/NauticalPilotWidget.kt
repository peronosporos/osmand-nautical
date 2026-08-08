package net.osmand.plus.views.mapwidgets.widgets

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.ProgressBar
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.ConnectionStatus
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.plugins.nautical.engine.NauticalHelmArbitrator
import net.osmand.plus.views.layers.base.OsmandMapLayer
import net.osmand.plus.views.mapwidgets.WidgetType
import net.osmand.plus.views.mapwidgets.WidgetsPanel
import java.util.Locale

class NauticalPilotWidget(
    mapActivity: MapActivity,
    widgetType: WidgetType,
    customId: String?,
    panel: WidgetsPanel?,
) : SimpleWidget(mapActivity, widgetType, customId, panel) {

    private var statusIconView: AppCompatImageView? = null
    private var progressBar: ProgressBar? = null
    private var progressText: View? = null
    private var rudderMarker: View? = null
    private var gestureDetector: GestureDetector? = null
    private val holdHandler = Handler(Looper.getMainLooper())
    private var holdProgress = 0
    private var pendingAnimator: ValueAnimator? = null
    private var lastIsPending = false
    private var lastCommandTime = 0L
    private val COMMAND_DEBOUNCE_MS = 500L

    init {
        setIcons(widgetType)
    }

    override fun getWidgetName(): String? = null

    override fun getIconId(): Int {
        val engine = NauticalPlugin.engine ?: return R.drawable.ic_plugin_nautical_map
        val state = engine.getCurrentState()

        if (state.isOffCourse) {
            return R.drawable.ic_action_alert
        }

        return when (state.autopilotState.lowercase(Locale.US)) {
            "auto" -> R.drawable.ic_action_direction_compass
            "wind" -> R.drawable.ic_action_wind
            "route", "track" -> R.drawable.ic_action_track_16
            "emergency", "stop" -> R.drawable.ic_action_stop
            else -> R.drawable.ic_plugin_nautical_map
        }
    }

    override fun updateIcon() {
        val iconId = getIconId()
        if (iconId != 0) {
            val engine = NauticalPlugin.engine
            val state = engine?.getCurrentState()
            val mode = state?.autopilotState?.lowercase(Locale.US) ?: "standby"
            val isStale = (state?.connectionStatus != ConnectionStatus.CONNECTED)
            val isLocked = NauticalHelmArbitrator.getInstance(app).isLockedByEmergency()
            val isPending = (state?.pendingAutopilotState != null) || (state?.pendingTargetHeading != null) || (state?.pendingCommandPath != null)

            updateAnimationState(isPending)

            val color = when {
                isLocked -> ContextCompat.getColor(app, R.color.text_color_negative) // Red/Locked
                state?.isOffCourse == true -> ContextCompat.getColor(app, R.color.text_color_negative) // Red
                ((mode != "standby") && !isStale) -> ContextCompat.getColor(app, R.color.color_ok) // Green
                else -> ContextCompat.getColor(app, R.color.map_widget_icon_color) // Dim/Grey
            }


            setImageDrawable(iconsCache.getPaintedIcon(iconId, color))
        }
    }

    private fun updateAnimationState(isPending: Boolean) {
        if (isPending != lastIsPending) {
            lastIsPending = isPending
            if (isPending) {
                if (pendingAnimator == null) {
                    pendingAnimator = ValueAnimator.ofFloat(0.5f, 1.0f).apply {
                        duration = 500
                        repeatCount = ValueAnimator.INFINITE
                        repeatMode = ValueAnimator.REVERSE
                        addUpdateListener { animator ->
                            val alpha = animator.animatedValue as Float
                            contentView?.alpha = alpha
                            statusIconView?.alpha = alpha
                        }
                        start()
                    }
                }
            } else {
                pendingAnimator?.cancel()
                pendingAnimator?.removeAllUpdateListeners()
                pendingAnimator = null
                contentView?.alpha = 1.0f
                statusIconView?.alpha = 1.0f
            }
        }
    }

    private val marineStateListener: (MarineState) -> Unit = { state ->
        mapActivity.runOnUiThread { 
            updateInfo(null)
            NauticalPlugin.getInstance()?.checkScreenAlwaysOn()
            
            state.rudderAngle?.let { angle ->
                val maxAngle = Math.toRadians(35.0)
                val ratio = (angle.coerceIn(-maxAngle, maxAngle) / maxAngle).toFloat()
                rudderMarker?.let { marker ->
                    val parent = marker.parent as? View
                    if (parent != null) {
                        val translationX = ratio * ((parent.width / 2f) - (marker.width / 2f))
                        marker.translationX = translationX
                    }
                }
            }
        }
    }

    override fun getContentLayoutId(): Int = R.layout.map_hud_pilot_widget

    @SuppressLint("ClickableViewAccessibility")
    override fun setupView(view: View) {
        super.setupView(view)

        statusIconView = view.findViewById(R.id.pilot_status_icon)
        progressBar = view.findViewById(R.id.pilot_progress_bar)
        progressText = view.findViewById(R.id.pilot_progress_text)
        rudderMarker = view.findViewById(R.id.hud_rudder_marker)

        view.addOnAttachStateChangeListener(
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    NauticalPlugin.engine?.registerListener(marineStateListener)
                }

                override fun onViewDetachedFromWindow(v: View) {
                    NauticalPlugin.engine?.unregisterListener(marineStateListener)
                    holdHandler.removeCallbacksAndMessages(null)
                    pendingAnimator?.cancel()
                    pendingAnimator?.removeAllUpdateListeners()
                    pendingAnimator = null
                    lastIsPending = false
                }
            },
        )

        gestureDetector = GestureDetector(
            mapActivity,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    val now = System.currentTimeMillis()
                    if ((now - lastCommandTime) < COMMAND_DEBOUNCE_MS) return true
                    lastCommandTime = now

                    if (NauticalHelmArbitrator.getInstance(app).isLockedByEmergency()) {
                        val maneuver = NauticalHelmArbitrator.getInstance(app).getActiveManeuver()
                        app.showToastMessage("Helm Locked by $maneuver")
                        return true
                    }
                    val sheet = NauticalPilotBottomSheet.newInstance()
                    sheet.show(mapActivity.supportFragmentManager, "pilot_control")
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    return true
                }

                override fun onLongPress(e: MotionEvent) {
                    val now = System.currentTimeMillis()
                    if ((now - lastCommandTime) < COMMAND_DEBOUNCE_MS) return
                    lastCommandTime = now

                    val engine = NauticalPlugin.engine
                    val mode = engine?.getCurrentState()?.autopilotState?.lowercase(Locale.US) ?: "standby"
                    if (mode == "standby") return // Only long press to drop to standby from engaged

                    progressBar?.visibility = View.VISIBLE
                    progressText?.visibility = View.VISIBLE
                    holdProgress = 0
                    val holdRunnable = object : Runnable {
                        override fun run() {
                            holdProgress += 4
                            progressBar?.progress = holdProgress

                            if (holdProgress >= 100) {
                                progressBar?.visibility = View.GONE
                                progressText?.visibility = View.GONE
                                triggerCommand("STOP")
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            } else {
                                holdHandler.postDelayed(this, 50)
                            }
                        }
                    }
                    holdHandler.post(holdRunnable)
                }
            },
        )

        view.setOnTouchListener { _, event ->
            if ((event.action == MotionEvent.ACTION_UP) || (event.action == MotionEvent.ACTION_CANCEL)) {
                holdHandler.removeCallbacksAndMessages(null)
                progressBar?.visibility = View.GONE
                progressText?.visibility = View.GONE
            }
            gestureDetector?.onTouchEvent(event) ?: false
        }

        // Accessibility Support
        view.contentDescription = mapActivity.getString(R.string.nautical_autopilot)
        androidx.core.view.ViewCompat.addAccessibilityAction(
            view,
            mapActivity.getString(R.string.nautical_accessibility_autopilot_disengage),
        ) { _, _ ->
            val engine = NauticalPlugin.engine
            val mode = engine?.getCurrentState()?.autopilotState?.lowercase(Locale.US) ?: "standby"
            if (mode != "standby") {
                triggerCommand("STOP")
                true
            } else {
                false
            }
        }


        // Phase 4: Bind HUD nudge controls
        val nudgeMinus10 = view.findViewById<View>(R.id.btn_hud_minus_10)
        val nudgeMinus1 = view.findViewById<View>(R.id.btn_hud_minus_1)
        val nudgePlus1 = view.findViewById<View>(R.id.btn_hud_plus_1)
        val nudgePlus10 = view.findViewById<View>(R.id.btn_hud_plus_10)

        nudgeMinus10?.setOnClickListener {
            if (!checkHelmLock()) return@setOnClickListener
            NauticalPlugin.autopilot?.adjustHeading(-10.0)
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
        nudgeMinus1?.setOnClickListener {
            if (!checkHelmLock()) return@setOnClickListener
            NauticalPlugin.autopilot?.adjustHeading(-1.0)
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
        nudgePlus1?.setOnClickListener {
            if (!checkHelmLock()) return@setOnClickListener
            NauticalPlugin.autopilot?.adjustHeading(1.0)
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
        nudgePlus10?.setOnClickListener {
            if (!checkHelmLock()) return@setOnClickListener
            NauticalPlugin.autopilot?.adjustHeading(10.0)
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    private fun checkHelmLock(): Boolean {
        if (NauticalHelmArbitrator.getInstance(app).isLockedByEmergency()) {
            val maneuver = NauticalHelmArbitrator.getInstance(app).getActiveManeuver()
            app.showToastMessage("Helm Locked by $maneuver")
            return false
        }
        return true
    }

    private fun triggerCommand(command: String) {
        val ap = NauticalPlugin.autopilot
        when (command) {
            "STOP" -> executeStopCommand()
            "TACK" -> showTacticalGate()
            "TACK_EXECUTED" -> {
                val isProa = settings.NAUTICAL_VESSEL_TYPE.get() == net.osmand.plus.settings.enums.VesselType.PROA
                if (isProa) {
                    ap?.shunt()
                } else {
                    val state = NauticalPlugin.engine?.getCurrentState()
                    val awa = state?.windDirectionApparent ?: 0.0
                    val awaDeg = Math.toDegrees(awa)
                    val upwind = kotlin.math.abs(awaDeg) < 90.0
                    // If wind is from Starboard (awaDeg > 0), we want to turn Starboard to tack upwind, 
                    // or Port to gybe downwind. This is complex and depends on AP. 
                    // Most APs just take "port" or "starboard" as the direction of the turn.
                    // For now, let's just flip from current side.
                    val turnToPort = awaDeg > 0 
                    if (upwind) ap?.tack(port = turnToPort) else ap?.gybe(port = turnToPort)
                }
            }
            else -> executeRoutineCommand(command)
        }
    }

    private fun executeStopCommand() {
        NauticalPlugin.autopilot?.let { autopilot ->
            if (autopilot.isConnected()) {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                autopilot.stopNavigation()
                mapActivity.app.showToastMessage(R.string.nautical_emergency_stop_executed)
            }
        }
    }

    private fun showTacticalGate() {
        val isProa = settings.NAUTICAL_VESSEL_TYPE.get() == net.osmand.plus.settings.enums.VesselType.PROA
        val autopilot = NauticalPlugin.autopilot
        val state = NauticalPlugin.engine?.getCurrentState()
        val upwind = state?.windDirectionApparent?.let { kotlin.math.abs(Math.toDegrees(it)) < 90.0 } ?: true
        val isSafe = autopilot?.isWindSafeForManeuver(tacking = upwind) ?: true

        val msg = if (isProa) {
            mapActivity.getString(R.string.nautical_confirm_shunt)
        } else {
            val baseMsg = if (upwind) mapActivity.getString(R.string.nautical_confirm_tack) else mapActivity.getString(R.string.nautical_confirm_gybe)
            if (!isSafe) {
                baseMsg + "\n\n" + mapActivity.getString(R.string.nautical_warn_unsafe_maneuver)
            } else {
                baseMsg
            }
        }

        val label = if (isProa) mapActivity.getString(R.string.nautical_shunt) else if (upwind) mapActivity.getString(R.string.nautical_tack) else mapActivity.getString(R.string.nautical_gybe)

        NauticalPlugin.hudManager?.get()?.showBanner(msg, 10000, label, !isSafe) {
            triggerCommand("TACK_EXECUTED")
        }
    }

    private fun executeRoutineCommand(command: String) {
        val msg = mapActivity.getString(R.string.nautical_command_sent) + ": " + command
        mapActivity.app.showToastMessage(msg)
    }

    private fun setStatusIcon(iconResId: Int) {
        statusIconView?.let {
            if (iconResId != 0) {
                it.setImageResource(iconResId)
                it.visibility = View.VISIBLE
            } else {
                it.visibility = View.GONE
            }
        }
    }

    override fun updateSimpleWidgetInfo(drawSettings: OsmandMapLayer.DrawSettings?) {
        val engine = NauticalPlugin.engine

        if (engine == null) {
            setText(mapActivity.getString(R.string.nautical_status_off), "")
            updateIcon()
            setStatusIcon(0)
            return
        }

        val state = engine.getCurrentState()
        if (state.isOffCourse) {
            val xteMeters = state.crossTrackError ?: 0.0
            val xteNm = kotlin.math.abs(xteMeters) / 1852.0
            setText(String.format(Locale.US, "%s: %.2f %s", mapActivity.getString(R.string.nautical_off_course), xteNm, mapActivity.getString(R.string.nautical_unit_nm)), "")
            updateIcon()
            setStatusIcon(0)
            view.findViewById<View>(R.id.hud_nudge_controls)?.visibility = View.VISIBLE
        } else {
            val mode = state.autopilotState.lowercase(Locale.US)
            val isStale = (state.connectionStatus != ConnectionStatus.CONNECTED)
            val heading = state.targetHeading ?: state.headingTrue
            
            val headingStr = if (mode == "standby") {
                mapActivity.getString(R.string.nautical_mode_stby)
            } else if ((heading == null) || isStale) {
                "--"
            } else {
                val headingDeg = Math.toDegrees(heading)
                val cardinal = getCardinalDirection(headingDeg)
                String.format(Locale.US, "%d° %s", headingDeg.toInt(), cardinal)
            }

            updateIcon()
            setText(headingStr, "")

            val statusIcon = when (mode) {
                "auto", "wind", "track" -> if (isStale) R.drawable.ic_action_time else R.drawable.ic_action_play_dark
                else -> 0
            }
            setStatusIcon(statusIcon)
            
            if (isStale) {
                statusIconView?.alpha = 0.5f
                contentView?.alpha = 0.5f
            } else {
                statusIconView?.alpha = 1.0f
                contentView?.alpha = 1.0f
            }

            val controls = view.findViewById<View>(R.id.hud_nudge_controls)
            controls?.visibility = if ((mode != "standby") && !isStale) View.VISIBLE else View.GONE
        }
    }

    private fun getCardinalDirection(course: Double): String {
        val directions = arrayOf(
            R.string.nautical_cardinal_n, R.string.nautical_cardinal_ne,
            R.string.nautical_cardinal_e, R.string.nautical_cardinal_se,
            R.string.nautical_cardinal_s, R.string.nautical_cardinal_sw,
            R.string.nautical_cardinal_w, R.string.nautical_cardinal_nw,
        )
        val index = (((((course % 360.0) + 360.0) % 360.0) + 22.5) / 45.0).toInt() % 8
        return mapActivity.getString(directions[index])
    }
}
