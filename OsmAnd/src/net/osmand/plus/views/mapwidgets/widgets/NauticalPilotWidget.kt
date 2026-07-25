package net.osmand.plus.views.mapwidgets.widgets

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
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.views.layers.base.OsmandMapLayer
import net.osmand.plus.views.mapwidgets.WidgetType
import net.osmand.plus.views.mapwidgets.WidgetsPanel
import java.util.*

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

    init {
        setIcons(widgetType)
    }

    override fun getWidgetName(): String? = null

    override fun getIconId(): Int {
        val engine = NauticalPlugin.engine ?: return R.drawable.ic_plugin_nautical_map
        val state = engine.getCurrentState() ?: return R.drawable.ic_plugin_nautical_map

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
            val isStale = (state?.connectionStatus != net.osmand.plus.plugins.nautical.engine.ConnectionStatus.CONNECTED)

            val color = when {
                state?.isOffCourse == true -> ContextCompat.getColor(app, R.color.text_color_negative) // Red
                mode != "standby" && !isStale -> ContextCompat.getColor(app, R.color.color_ok) // Green
                else -> ContextCompat.getColor(app, R.color.color_unknown) // Dim/Grey
            }
            setImageDrawable(iconsCache.getPaintedIcon(iconId, color))
        }
    }

    private val marineStateListener: (MarineState) -> Unit = { state ->
        val now = System.currentTimeMillis()
        if ((now - lastUpdateTime) > 200) {
            lastUpdateTime = now
            mapActivity.runOnUiThread { updateInfo(null) }
        }

        if ((now - lastRudderUpdateTime) > 50) { // Throttle rudder to 20Hz
            lastRudderUpdateTime = now
            mapActivity.runOnUiThread {
                state.rudderAngle?.let { angle ->
                    val maxAngle = Math.toRadians(35.0)
                    val ratio = (angle.coerceIn(-maxAngle, maxAngle) / maxAngle).toFloat()
                    rudderMarker?.let { marker ->
                        val parent = marker.parent as? View
                        if (parent != null) {
                            val translationX = ratio * (parent.width / 2f - marker.width / 2f)
                            marker.translationX = translationX
                        }
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
        rudderMarker = view.findViewById(R.id.hud_rudder_marker)

        view.addOnAttachStateChangeListener(
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    NauticalPlugin.engine?.registerListener(marineStateListener)
                }

                override fun onViewDetachedFromWindow(v: View) {
                    NauticalPlugin.engine?.unregisterListener(marineStateListener)
                    holdHandler.removeCallbacksAndMessages(null)
                }
            },
        )

        gestureDetector = GestureDetector(
            mapActivity,
            object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                val engine = NauticalPlugin.engine
                val state = engine?.getCurrentState()
                val mode = state?.autopilotState?.lowercase(Locale.US) ?: "standby"
                
                if (mode == "standby") {
                    // Smart Engage
                    if (engine?.isFollowingRoute == true) {
                        NauticalPlugin.autopilot?.setAutopilotMode("track")
                    } else {
                        NauticalPlugin.autopilot?.setAutopilotMode("auto")
                    }
                } else {
                    // Disengage to Standby
                    NauticalPlugin.autopilot?.setAutopilotMode("standby")
                }
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                val sheet = NauticalPilotBottomSheet.newInstance()
                sheet.show(mapActivity.supportFragmentManager, "pilot_control")
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                progressBar?.visibility = View.VISIBLE
                holdProgress = 0
                val holdRunnable = object : Runnable {
                    override fun run() {
                        holdProgress += 4
                        progressBar?.progress = holdProgress

                        if (holdProgress >= 100) {
                            progressBar?.visibility = View.GONE
                            triggerCommand("STOP")
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

        val popupView = View.inflate(mapActivity, R.layout.nautical_confirm_popup, null)
        
        val msgView = popupView.findViewById<TextView>(R.id.txt_confirm_msg)
        if (isProa) {
            msgView?.text = mapActivity.getString(R.string.nautical_confirm_shunt)
        } else {
            msgView?.text = if (upwind) mapActivity.getString(R.string.nautical_confirm_tack) else mapActivity.getString(R.string.nautical_confirm_gybe)
            if (!isSafe) {
                msgView?.append("\n\n" + mapActivity.getString(R.string.nautical_warn_unsafe_maneuver))
                msgView?.setTextColor(ContextCompat.getColor(mapActivity, R.color.text_color_negative))
            }
        }

        val popup = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true,
        )

        val dismissHandler = Handler(Looper.getMainLooper())
        val dismissRunnable = Runnable { if (popup.isShowing) popup.dismiss() }
        dismissHandler.postDelayed(dismissRunnable, 5000)

        popupView.findViewById<View>(R.id.btn_confirm).setOnClickListener {
            dismissHandler.removeCallbacks(dismissRunnable)
            triggerCommand("TACK_EXECUTED")
            popup.dismiss()
        }

        popup.showAsDropDown(view, 0, -(view.height))
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
        if (state == null) {
            setText(mapActivity.getString(R.string.nautical_status_off), "")
            updateIcon()
            setStatusIcon(0)
            return
        }

        if (state.isOffCourse) {
            val xteMeters = state.crossTrackError ?: 0.0
            val xteNm = kotlin.math.abs(xteMeters) / 1852.0
            setText(String.format(Locale.US, "%s: %.2f %s", mapActivity.getString(R.string.nautical_off_course), xteNm, mapActivity.getString(R.string.nautical_unit_nm)), "")
            updateIcon()
            setStatusIcon(0)
        } else {
            val mode = state.autopilotState.lowercase(Locale.US)
            val isStale = (state.connectionStatus != net.osmand.plus.plugins.nautical.engine.ConnectionStatus.CONNECTED)
            val heading = state.targetHeading ?: state.headingTrue
            
            val headingStr = if (mode == "standby") {
                mapActivity.getString(R.string.nautical_mode_stby)
            } else if (heading == null || isStale) {
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
        }
    }

    private fun getCardinalDirection(course: Double): String {
        val directions = arrayOf(
            R.string.nautical_cardinal_n, R.string.nautical_cardinal_ne,
            R.string.nautical_cardinal_e, R.string.nautical_cardinal_se,
            R.string.nautical_cardinal_s, R.string.nautical_cardinal_sw,
            R.string.nautical_cardinal_w, R.string.nautical_cardinal_nw
        )
        val index = (((course % 360.0 + 360.0) % 360.0 + 22.5) / 45.0).toInt() % 8
        return mapActivity.getString(directions[index])
    }
}
