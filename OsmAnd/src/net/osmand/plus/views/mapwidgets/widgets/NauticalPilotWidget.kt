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
import androidx.core.content.ContextCompat
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.views.layers.base.OsmandMapLayer
import net.osmand.plus.views.mapwidgets.WidgetType
import net.osmand.plus.views.mapwidgets.WidgetsPanel
import net.osmand.plus.settings.backend.preferences.CommonPreference
import java.util.*
import kotlin.math.abs

class NauticalPilotWidget(
    mapActivity: MapActivity,
    widgetType: WidgetType,
    customId: String?,
    panel: WidgetsPanel?,
) : SimpleWidget(mapActivity, widgetType, customId, panel) {

    val useNauticalStandardPref: CommonPreference<Boolean> = mapActivity.app.settings.registerBooleanPreference(
        "nautical_widget_use_standard_${widgetType.id}${customId ?: ""}",
        true,
    ).makeProfile()

    init {
        setIcons(widgetType)
    }

    override fun updateIcon() {
        val iconId = iconId
        if (iconId != 0) {
            val color = settings.applicationMode.getProfileColor(isNightMode)
            setImageDrawable(iconsCache.getPaintedIcon(iconId, color))
        }
    }

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
            }
        )

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
                })
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
            true,
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
        
        if (engine == null) {
            setText(mapActivity.getString(R.string.nautical_status_off), "")
            setImageDrawable(iconsCache.getPaintedIcon(R.drawable.ic_action_sail_boat_dark, ContextCompat.getColor(app, if (isNightMode) R.color.icon_color_default_dark else R.color.icon_color_default_light)))
            setStatusIcon(0)
            return
        }

        val state = engine.getCurrentState()
        if (state == null) {
            setText(mapActivity.getString(R.string.nautical_status_off), "")
            setImageDrawable(iconsCache.getPaintedIcon(R.drawable.ic_action_sail_boat_dark, ContextCompat.getColor(app, if (isNightMode) R.color.icon_color_default_dark else R.color.icon_color_default_light)))
            setStatusIcon(0)
            return
        }

        val xte = state.crossTrackError ?: 0.0
        val threshold = mapActivity.app.settings.NAUTICAL_XTE_THRESHOLD.get() ?: 0.1f
        val isOffCourse = abs(xte) > threshold.toDouble()

        if (isOffCourse) {
            val distStr = if (useNauticalStandardPref.get()) {
                String.format(Locale.US, "%.2f NM", abs(xte))
            } else {
                net.osmand.plus.utils.OsmAndFormatter.getFormattedDistance(abs(xte).toFloat() * 1852f, app)
            }
            setText(mapActivity.getString(R.string.nautical_off_course), distStr)
            setImageDrawable(iconsCache.getPaintedIcon(R.drawable.ic_action_alert, ContextCompat.getColor(app, R.color.text_color_negative)))
            setStatusIcon(0)
        } else {
            val mode = state.autopilotState.lowercase(Locale.US)
            val heading = state.targetHeading ?: state.headingTrue ?: 0.0
            val headingStr = if (mode == "standby") "STBY" else String.format(Locale.US, "%d°", Math.toDegrees(heading).toInt())

            val iconRes = when (mode) {
                "auto" -> R.drawable.ic_action_direction_compass
                "wind" -> R.drawable.ic_action_sail_boat_dark
                "route", "track" -> R.drawable.ic_action_track_16
                "emergency", "stop" -> R.drawable.ic_action_stop
                else -> R.drawable.ic_action_direction_compass
            }
            
            val iconColor = ContextCompat.getColor(app, if (isNightMode) R.color.icon_color_default_dark else R.color.icon_color_default_light)
            setImageDrawable(iconsCache.getPaintedIcon(iconRes, iconColor))
            setText(headingStr, "")
            
            val statusIcon = when (mode) {
                "auto", "wind", "route", "track" -> R.drawable.ic_action_play_dark
                else -> 0
            }
            setStatusIcon(statusIcon)
        }
    }
}
