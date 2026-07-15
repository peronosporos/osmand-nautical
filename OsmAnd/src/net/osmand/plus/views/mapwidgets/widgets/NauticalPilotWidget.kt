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
import androidx.appcompat.widget.AppCompatImageView
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.views.layers.base.OsmandMapLayer
import net.osmand.plus.views.mapwidgets.WidgetType
import net.osmand.plus.views.mapwidgets.WidgetsPanel
import java.util.*
import kotlin.math.abs

class NauticalPilotWidget(
    mapActivity: MapActivity,
    widgetType: WidgetType,
    customId: String?,
    panel: WidgetsPanel?,
) : SimpleWidget(mapActivity, widgetType, customId, panel) {

    private var statusIconView: AppCompatImageView? = null
    private var progressBar: ProgressBar? = null
    private var gestureDetector: GestureDetector? = null
    private val holdHandler = Handler(Looper.getMainLooper())
    private var holdProgress = 0

    private val marineStateListener: (MarineState) -> Unit = {
        mapActivity.runOnUiThread {
            updateInfo(null)
        }
    }

    override fun getContentLayoutId(): Int = R.layout.map_hud_pilot_widget

    @SuppressLint("ClickableViewAccessibility")
    override fun setupView(view: View) {
        super.setupView(view)

        statusIconView = view.findViewById(R.id.pilot_status_icon)
        progressBar = view.findViewById(R.id.pilot_progress_bar)

        view.addOnAttachStateChangeListener(
            object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                NauticalPlugin.engine?.registerListener(marineStateListener)
            }

            override fun onViewDetachedFromWindow(v: View) {
                NauticalPlugin.engine?.unregisterListener(marineStateListener)
                holdHandler.removeCallbacksAndMessages(null)
            }
        })

        gestureDetector = GestureDetector(mapActivity, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                val sheet = NauticalPilotBottomSheet.newInstance()
                sheet.show(mapActivity.supportFragmentManager, "pilot_control")
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                triggerCommand("STOP")
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                progressBar?.visibility = View.VISIBLE
                holdProgress = 0
                holdHandler.post(
                    object : Runnable {
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
                },
            )
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
        when (command) {
            "STOP" -> executeStopCommand()
            "TACK" -> showTacticalGate()
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
        val popupView = View.inflate(mapActivity, R.layout.nautical_confirm_popup, null)
        val popup = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )

        val dismissHandler = Handler(Looper.getMainLooper())
        val dismissRunnable = Runnable { if (popup.isShowing) popup.dismiss() }
        dismissHandler.postDelayed(dismissRunnable, 3000)

        popupView.findViewById<View>(R.id.btn_confirm).setOnClickListener {
            dismissHandler.removeCallbacks(dismissRunnable)
            triggerCommand("TACK_EXECUTED")
            popup.dismiss()
        }

        popup.showAsDropDown(view, 0, -(view.height))
    }

    private fun executeRoutineCommand(command: String) {
        mapActivity.app.showToastMessage("${mapActivity.getString(R.string.nautical_command_sent)}: $command")
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
        
        updateIcon()

        if (engine == null) {
            setText(mapActivity.getString(R.string.nautical_status_off), mapActivity.getString(R.string.nautical_mode_ap))
            setStatusIcon(0)
            return
        }

        val state = engine.getCurrentState()
        if (state == null) {
            setText(mapActivity.getString(R.string.nautical_status_off), mapActivity.getString(R.string.nautical_mode_ap))
            setStatusIcon(0)
            return
        }

        val xte = state.crossTrackError ?: 0.0
        val threshold = mapActivity.app.settings.NAUTICAL_XTE_THRESHOLD.get() ?: 0.1f
        val isOffCourse = abs(xte) > threshold.toDouble()

        if (isOffCourse) {
            setText(String.format(Locale.US, "%.2f", xte), mapActivity.getString(R.string.nautical_xte_label, mapActivity.getString(R.string.nautical_unit_nm)))
            setStatusIcon(R.drawable.ic_action_alert)
        } else {
            when (val mode = state.autopilotState.lowercase(Locale.US)) {
                "auto", "wind", "route", "track" -> {
                    setStatusIcon(R.drawable.ic_action_play_dark)
                    val modeLabel = when (mode) {
                        "wind" -> "WIND"
                        "route", "track" -> "TRACK"
                        else -> "AUTO"
                    }
                    setText(modeLabel, mapActivity.getString(R.string.nautical_mode_ap))
                }
                "emergency", "stop" -> {
                    setStatusIcon(R.drawable.ic_action_stop)
                    setText(mapActivity.getString(R.string.nautical_emg_stop), mapActivity.getString(R.string.nautical_mode_stop))
                }
                else -> {
                    setStatusIcon(R.drawable.ic_pause)
                    setText(mapActivity.getString(R.string.nautical_mode_stby), mapActivity.getString(R.string.nautical_mode_ap))
                }
            }
        }
    }
}
