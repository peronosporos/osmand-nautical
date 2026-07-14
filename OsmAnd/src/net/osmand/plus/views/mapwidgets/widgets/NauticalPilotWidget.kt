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
import android.widget.Toast
import androidx.appcompat.widget.AppCompatImageView
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.settings.backend.preferences.OsmandPreference
import net.osmand.plus.settings.enums.WidgetSize
import net.osmand.plus.views.layers.base.OsmandMapLayer
import net.osmand.plus.views.mapwidgets.WidgetType
import net.osmand.plus.views.mapwidgets.WidgetsPanel
import net.osmand.plus.views.mapwidgets.widgetinterfaces.ISupportWidgetResizing
import net.osmand.plus.views.mapwidgets.widgetstates.ResizableWidgetState
import java.util.*

class NauticalPilotWidget(
    mapActivity: MapActivity,
    widgetType: WidgetType,
    customId: String?,
    panel: WidgetsPanel?
) : TextInfoWidget(mapActivity, widgetType, customId, panel), ISupportWidgetResizing {

    private val widgetSizePref: OsmandPreference<WidgetSize> = ResizableWidgetState.registerWidgetSizePref(
        mapActivity.app,
        customId,
        widgetType,
        WidgetSize.MEDIUM
    )

    private var statusIconView: AppCompatImageView? = null
    private var progressBar: ProgressBar? = null
    private var gestureDetector: GestureDetector? = null
    private val holdHandler = Handler(Looper.getMainLooper())
    private var holdProgress = 0

    private val marineStateListener: (MarineState) -> Unit = {
        mapActivity.runOnUiThread {
            updateInfo(view, null)
        }
    }

    override fun getLayoutId(): Int = R.layout.map_hud_pilot_widget

    override fun getWidgetSizePref(): OsmandPreference<WidgetSize> = widgetSizePref

    override fun allowResize(): Boolean = true

    override fun recreateView() {
        setupView(view)
        updateInfo(view, null)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun setupView(view: View) {
        super.setupView(view)

        statusIconView = view.findViewById(R.id.pilot_status_icon)
        progressBar = view.findViewById(R.id.pilot_progress_bar)

        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
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

            override fun onLongPress(e: MotionEvent) {
                progressBar?.visibility = View.VISIBLE
                holdProgress = 0
                holdHandler.post(object : Runnable {
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
                })
            }
        })

        view.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
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
                view?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                autopilot.stopNavigation()
                Toast.makeText(mapActivity, mapActivity.getString(R.string.nautical_emergency_stop_executed), Toast.LENGTH_LONG).show()
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
            executeRoutineCommand("TACK_EXECUTED")
            popup.dismiss()
        }

        popup.showAsDropDown(view, 0, -(view?.height ?: 0))
    }

    private fun executeRoutineCommand(command: String) {
        Toast.makeText(mapActivity, "${mapActivity.getString(R.string.nautical_command_sent)}: $command", Toast.LENGTH_SHORT).show()
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

    @SuppressLint("DefaultLocale")
    override fun updateInfo(view: View, drawSettings: OsmandMapLayer.DrawSettings?) {
        super.updateInfo(view, drawSettings)

        val engine = NauticalPlugin.engine
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
        val isOffCourse = Math.abs(xte) > 0.1

        if (isOffCourse) {
            setText(String.format(Locale.US, "%.2f", xte), mapActivity.getString(R.string.nautical_unit_nm) + " XTE")
            setStatusIcon(R.drawable.ic_action_alert)
        } else {
            val apState = state.autopilotState.lowercase(Locale.US)
            when (apState) {
                "auto", "wind" -> {
                    setStatusIcon(R.drawable.ic_action_play_dark)
                    setText(mapActivity.getString(R.string.nautical_mode_auto), mapActivity.getString(R.string.nautical_mode_ap))
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
