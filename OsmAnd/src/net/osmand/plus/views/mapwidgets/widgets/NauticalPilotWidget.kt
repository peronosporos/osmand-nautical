package net.osmand.plus.views.mapwidgets.widgets

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.plugins.nautical.engine.ConnectionStatus
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
    private var rudderMarker: View? = null
    private var gestureDetector: GestureDetector? = null
    private val holdHandler = Handler(Looper.getMainLooper())
    private var holdProgress = 0
    private var lastUpdateTime = 0L
    private var lastRudderUpdateTime = 0L
    private var lastCommandTime = 0L
    private val COMMAND_DEBOUNCE_MS = 500L
    private var pendingAnimator: ValueAnimator? = null
    private var lastIsPending = false

    init {
        setIcons(widgetType)
    }

    override fun getWidgetName(): String? = null

    override fun getAdditionalWidgetName(): String? = null

    override fun setContentTitle(messageId: Int) {
        super.setContentTitle("")
        widgetName?.visibility = View.GONE
        widgetName?.text = ""
    }

    override fun setContentTitle(text: String?) {
        super.setContentTitle("")
        widgetName?.visibility = View.GONE
        widgetName?.text = ""
    }

    override fun updateWidgetView() {
        super.updateWidgetView()
        widgetName?.visibility = View.GONE
        widgetName?.text = ""
    }

    override fun getIconId(): Int {
        val engine = NauticalPlugin.engine ?: return R.drawable.ic_action_power_standby
        val state = engine.getCurrentState()
        val mode = state.autopilotState.lowercase(Locale.US)
        return when (mode) {
            "standby", "" -> R.drawable.ic_action_power_standby
            "compass", "heading", "auto" -> R.drawable.ic_action_direction_compass
            "wind" -> R.drawable.ic_action_wind
            "nav", "track", "route" -> R.drawable.ic_action_track_16
            "emergency", "stop" -> R.drawable.ic_action_stop
            else -> R.drawable.ic_action_power_standby
        }
    }

    override fun updateIcon() {
        val iconId = getIconId()
        if (iconId != 0) {
            val engine = NauticalPlugin.engine
            val state = engine?.getCurrentState()
            val mode = state?.autopilotState?.lowercase(Locale.US) ?: "standby"
            val isEngaged = mode != "standby" && mode.isNotEmpty()
            val isStale = (state?.connectionStatus != ConnectionStatus.CONNECTED)
            val isLocked = NauticalHelmArbitrator.getInstance(app).isLockedByEmergency()
            val isPending = (state?.pendingAutopilotState != null) || (state?.pendingTargetHeading != null)

            updateAnimationState(isPending)

            val color = when {
                isLocked -> ContextCompat.getColor(app, R.color.text_color_negative)
                state?.isOffCourse == true -> ContextCompat.getColor(app, R.color.text_color_negative)
                isEngaged && !isStale -> ContextCompat.getColor(app, R.color.color_ok)
                else -> ContextCompat.getColor(app, R.color.map_widget_icon_color)
            }
            setImageDrawable(iconsCache.getPaintedIcon(iconId, color))
        }
    }

    override fun updateColors(textState: net.osmand.plus.views.layers.MapInfoLayer.TextState) {
        super.updateColors(textState)
        updateIcon()
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
                        }
                        start()
                    }
                }
            } else {
                pendingAnimator?.cancel()
                pendingAnimator = null
                contentView?.alpha = 1.0f
            }
        }
    }

    override fun getContentLayoutId(): Int = R.layout.map_hud_pilot_widget

    private var dataJob: kotlinx.coroutines.Job? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun setupView(view: View) {
        super.setupView(view)
        widgetName?.visibility = View.GONE
        widgetName?.text = ""

        statusIconView = view.findViewById(R.id.pilot_status_icon)
        statusIconView?.setOnClickListener {
            showTacticalGate()
        }
        progressBar = view.findViewById(R.id.pilot_progress_bar)
        rudderMarker = view.findViewById(R.id.hud_rudder_marker)

        view.addOnAttachStateChangeListener(
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    v.post {
                        if (v.isAttachedToWindow && !mapActivity.isFinishing && !mapActivity.isDestroyed) {
                            val broker = NauticalPlugin.engine?.dataBroker
                            if (broker != null) {
                                dataJob?.cancel()
                                dataJob = mapActivity.lifecycleScope.launch {
                                    mapActivity.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                                        broker.marineState
                                            .sample(300L)
                                            .collect { state ->
                                            updateInfo(null)
                                            updateWidgetView()
                                            state.rudderAngle?.let { angle ->
                                                val maxAngle = Math.toRadians(app.settings.NAUTICAL_RUDDER_LIMIT.get().toDouble())
                                                val ratio = (angle.coerceIn(-maxAngle, maxAngle) / maxAngle).toFloat()
                                                rudderMarker?.let { marker ->
                                                    val parent = marker.parent as? View
                                                    if (parent != null) {
                                                        val translationX = ratio * (parent.width / 2f - marker.width / 2f)
                                                        marker.translationX = translationX
                                                    }
                                                }
                                            }
                                            v.invalidate()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                override fun onViewDetachedFromWindow(v: View) {
                    dataJob?.cancel()
                    dataJob = null
                    holdHandler.removeCallbacksAndMessages(null)
                    pendingAnimator?.cancel()
                    pendingAnimator = null
                }
            },
        )

        gestureDetector = GestureDetector(
            mapActivity,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    val sheet = net.osmand.plus.plugins.nautical.ui.widgets.NauticalPilotBottomSheet.newInstance()
                    sheet.show(mapActivity.supportFragmentManager, "pilot_control")
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    return true
                }

                override fun onDoubleTap(e: MotionEvent): Boolean {
                    val now = System.currentTimeMillis()
                    if ((now - lastCommandTime) < COMMAND_DEBOUNCE_MS) return true
                    lastCommandTime = now

                    if (!checkHelmLock()) return true

                    val engine = NauticalPlugin.engine
                    val state = engine?.getCurrentState()
                    val mode = state?.autopilotState?.lowercase(Locale.US) ?: "standby"

                    if (mode == "standby") {
                        if (engine?.isFollowingRoute == true) {
                            NauticalPlugin.autopilot?.setAutopilotMode("track")
                        } else {
                            NauticalPlugin.autopilot?.setAutopilotMode("auto")
                        }
                    } else {
                        NauticalPlugin.autopilot?.setAutopilotMode("standby")
                    }
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    updateInfo(null)
                    updateWidgetView()
                    return true
                }

                override fun onLongPress(e: MotionEvent) {
                    val engine = NauticalPlugin.engine
                    val mode = engine?.getCurrentState()?.autopilotState?.lowercase(Locale.US) ?: "standby"
                    if (mode == "standby") return

                    progressBar?.visibility = View.VISIBLE
                    holdProgress = 0
                    val holdRunnable = object : Runnable {
                        override fun run() {
                            holdProgress += 4
                            progressBar?.progress = holdProgress

                            if (holdProgress >= 100) {
                                progressBar?.visibility = View.GONE
                                executeStopCommand()
                            } else {
                                holdHandler.postDelayed(this, 50)
                            }
                        }
                    }
                    holdHandler.post(holdRunnable)
                }
            })

        view.setOnTouchListener { _, event ->
            if ((event.action == MotionEvent.ACTION_UP) || (event.action == MotionEvent.ACTION_CANCEL)) {
                holdHandler.removeCallbacksAndMessages(null)
                progressBar?.visibility = View.GONE
            }
            gestureDetector?.onTouchEvent(event) ?: false
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

    private fun executeStopCommand() {
        NauticalPlugin.autopilot?.let { autopilot ->
            if (autopilot.isConnected()) {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                autopilot.stopNavigation()
                mapActivity.app.showToastMessage(R.string.nautical_emergency_stop_executed)
                updateInfo(null)
                updateWidgetView()
            }
        }
    }

    private fun showTacticalGate() {
        val autopilot = NauticalPlugin.autopilot
        val state = NauticalPlugin.engine?.getCurrentState()
        val awa = state?.windDirectionApparent ?: 0.0
        val awaDeg = Math.toDegrees(awa)
        val upwind = kotlin.math.abs(awaDeg) < 90.0
        val isSafe = autopilot?.isWindSafeForManeuver(tacking = upwind) ?: true

        val popupView = View.inflate(mapActivity, R.layout.nautical_confirm_popup, null)
        
        val titleView = popupView.findViewById<TextView>(R.id.confirm_title)
        val msgView = popupView.findViewById<TextView>(R.id.confirm_message)
        val slider = popupView.findViewById<net.osmand.plus.plugins.nautical.ui.SlideToConfirmView>(R.id.confirm_slider)

        titleView?.text = mapActivity.getString(if (upwind) R.string.nautical_layline_reached_tack else R.string.nautical_layline_reached_gybe)
        msgView?.text = if (!isSafe) mapActivity.getString(R.string.nautical_autopilot_rejected) else ""
        
        if (!isSafe) {
            msgView?.setTextColor(ContextCompat.getColor(mapActivity, R.color.text_color_negative))
        }

        val popup = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true,
        )

        val dismissHandler = Handler(Looper.getMainLooper())
        val dismissRunnable = Runnable { if (popup.isShowing) popup.dismiss() }
        dismissHandler.postDelayed(dismissRunnable, 10000)

        slider?.onConfirm = {
            dismissHandler.removeCallbacks(dismissRunnable)
            val turnToPort = awaDeg > 0 
            val isProa = app.settings.NAUTICAL_VESSEL_TYPE.get() == net.osmand.plus.settings.enums.VesselType.PROA
            if (isProa) {
                autopilot?.shunt()
            } else {
                if (upwind) autopilot?.tack(port = turnToPort) else autopilot?.gybe(port = turnToPort)
            }
            popup.dismiss()
            updateInfo(null)
            updateWidgetView()
        }

        popupView.findViewById<View>(R.id.btn_cancel).setOnClickListener {
            popup.dismiss()
        }

        view.let { v ->
            popup.showAtLocation(v, android.view.Gravity.BOTTOM, 0, 0)
        }
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
        widgetName?.visibility = View.GONE
        widgetName?.text = ""
        val state = NauticalPlugin.engine?.getCurrentState()
        if (state == null) {
            setText("STANDBY", "")
            updateIcon()
            setStatusIcon(0)
            contentView?.alpha = 0.5f
            return
        }

        val mode = state.autopilotState.lowercase(Locale.US)
        val isEngaged = mode != "standby" && mode.isNotEmpty()
        val isStale = (state.connectionStatus != ConnectionStatus.CONNECTED)

        updateIcon()

        if (state.isOffCourse) {
            val xteMeters = state.crossTrackError ?: 0.0
            val xteNm = kotlin.math.abs(xteMeters) / 1852.0
            setText(String.format(Locale.US, "XTE %.2f", xteNm), "")
            setStatusIcon(if (isStale) R.drawable.ic_action_time else R.drawable.ic_action_play_dark)
        } else if (!isEngaged) {
            setText("STANDBY", "")
            setStatusIcon(0)
        } else {
            when (mode) {
                "wind" -> {
                    val targetWind = state.targetWindAngleApparent ?: state.windDirectionApparent
                    if (targetWind != null && !isStale) {
                        val deg = Math.toDegrees(targetWind)
                        setText(String.format(Locale.US, "W %.0f°", deg), "")
                    } else {
                        setText("--", "")
                    }
                }
                "nav", "track", "route" -> {
                    val heading = state.targetHeading ?: state.courseOverGroundTrue
                    if (heading != null && !isStale) {
                        val deg = Math.toDegrees(heading)
                        setText(String.format(Locale.US, "%.0f°", deg), "")
                    } else {
                        setText("--", "")
                    }
                }
                else -> { // auto, compass, heading
                    val heading = state.targetHeading ?: state.headingTrue
                    if (heading != null && !isStale) {
                        val deg = Math.toDegrees(heading)
                        setText(String.format(Locale.US, "%.0f°", deg), "")
                    } else {
                        setText("--", "")
                    }
                }
            }
            setStatusIcon(if (isStale) R.drawable.ic_action_time else R.drawable.ic_action_play_dark)
        }

        if (isStale) {
            statusIconView?.alpha = 0.5f
            contentView?.alpha = 0.5f
        } else {
            statusIconView?.alpha = 1.0f
            contentView?.alpha = 1.0f
        }
    }

    private val CARDINAL_DIRECTIONS = intArrayOf(
        R.string.nautical_cardinal_n, R.string.nautical_cardinal_ne,
        R.string.nautical_cardinal_e, R.string.nautical_cardinal_se,
        R.string.nautical_cardinal_s, R.string.nautical_cardinal_sw,
        R.string.nautical_cardinal_w, R.string.nautical_cardinal_nw
    )

    private fun getCardinalDirection(course: Double): String {
        val index = (((course % 360.0 + 360.0) % 360.0 + 22.5) / 45.0).toInt() % 8
        return mapActivity.getString(CARDINAL_DIRECTIONS[index])
    }
}
