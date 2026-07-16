package net.osmand.plus.views.mapwidgets.widgets

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.Chip
import com.google.android.material.progressindicator.CircularProgressIndicator
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.plugins.nautical.ui.HeadingArcView
import net.osmand.plus.plugins.nautical.ui.RudderView
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.views.OsmandMapTileView
import java.util.*

class NauticalPilotBottomSheet : BottomSheetDialogFragment() {

    private var listener: ((MarineState) -> Unit)? = null
    private var isArmedPort = false
    private var isArmedStbd = false
    private val armHandler = Handler(Looper.getMainLooper())
    private val resetArmRunnable = Runnable {
        isArmedPort = false
        isArmedStbd = false
        updateTackButtons()
    }
    private var autoDismissHandler = Handler(Looper.getMainLooper())
    private val autoDismissRunnable = Runnable { dismiss() }
    private var mapTouchListener: OsmandMapTileView.TouchListener? = null

    companion object {
        @JvmStatic
        fun newInstance(): NauticalPilotBottomSheet {
            return NauticalPilotBottomSheet()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.BottomSheet_Dialog)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            window.setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
        resetAutoDismissTimer()
    }

    private fun resetAutoDismissTimer() {
        autoDismissHandler.removeCallbacks(autoDismissRunnable)
        autoDismissHandler.postDelayed(autoDismissRunnable, 15000) // 15 seconds default
    }

    private fun updateTackButtons() {
        val view = view ?: return
        val minus1Btn = view.findViewById<Button>(R.id.btn_minus_1)
        val plus1Btn = view.findViewById<Button>(R.id.btn_plus_1)

        if (isArmedPort) {
            minus1Btn.text = getString(R.string.nautical_confirm_tack)
            minus1Btn.setTextColor(ContextCompat.getColor(requireContext(), R.color.active_color_primary_light))
        } else {
            minus1Btn.text = getString(R.string.nautical_tack_port)
            minus1Btn.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_color_primary_light))
        }

        if (isArmedStbd) {
            plus1Btn.text = getString(R.string.nautical_confirm_tack)
            plus1Btn.setTextColor(ContextCompat.getColor(requireContext(), R.color.active_color_primary_light))
        } else {
            plus1Btn.text = getString(R.string.nautical_tack_stbd)
            plus1Btn.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_color_primary_light))
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.bottom_sheet_nautical_pilot, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val engine = NauticalPlugin.engine
        val autopilot = NauticalPlugin.autopilot
        val plugin = NauticalPlugin.getInstance()

        if ((engine == null) || (autopilot == null)) {
            dismiss()
            return
        }

        plugin?.applyNightVisionFilter(view)

        val modeBadge = view.findViewById<Chip>(R.id.mode_badge)
        val standbyBtn = view.findViewById<Button>(R.id.btn_standby)
        val arcView = view.findViewById<HeadingArcView>(R.id.heading_arc_view)
        val seaStateGroup = view.findViewById<MaterialButtonToggleGroup>(R.id.sea_state_toggle_group)
        val advancedBtn = view.findViewById<View>(R.id.btn_advanced)
        val minus1Btn = view.findViewById<Button>(R.id.btn_minus_1)
        val plus1Btn = view.findViewById<Button>(R.id.btn_plus_1)
        val rudderView = view.findViewById<RudderView>(R.id.rudder_view)
        val standbyProgress = view.findViewById<CircularProgressIndicator>(R.id.standby_progress)

        val holdHandler = Handler(Looper.getMainLooper())
        var holdStartTime = 0L
        val holdDuration = 300L

        val holdRunnable = object : Runnable {
            override fun run() {
                val elapsed = System.currentTimeMillis() - holdStartTime
                val progress = ((elapsed.toFloat() / holdDuration) * 100f).toInt()
                if (progress >= 100) {
                    standbyProgress.progress = 100
                    standbyProgress.visibility = View.GONE
                    autopilot.stopNavigation()
                    view.findViewById<View>(R.id.btn_standby).performClick()
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                } else {
                    standbyProgress.progress = progress
                    holdHandler.postDelayed(this, 16)
                }
            }
        }

        listener = { state ->
            view.post {
                if (!isAdded) return@post
                resetAutoDismissTimer()

                val rawMode = state.autopilotState.uppercase(Locale.US)
                val modeLabel = when (rawMode) {
                    "TACKING" -> getString(R.string.nautical_tacking)
                    "GYBING" -> getString(R.string.nautical_gybing)
                    else -> rawMode
                }
                modeBadge.text = getString(R.string.nautical_mode_label, modeLabel)

                if (rawMode != "STANDBY") {
                    standbyBtn.text = getString(R.string.nautical_disengage_standby)
                    standbyBtn.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.text_color_negative))
                } else {
                    standbyBtn.text = getString(R.string.nautical_engage_auto)
                    standbyBtn.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.active_color_primary_light))
                }

                val heading = state.targetHeading ?: state.headingTrue ?: 0.0
                arcView.targetHeading = Math.toDegrees(heading).toInt()
                arcView.isTacking = (rawMode == "TACKING") || (rawMode == "GYBING")

                state.rudderAngle?.let { rudderView?.setRudderAngle(it) }

                val seaState = state.seaState ?: 2
                when (seaState) {
                    1 -> seaStateGroup.check(R.id.btn_sea_calm)
                    2, 3 -> seaStateGroup.check(R.id.btn_sea_auto)
                    4, 5 -> seaStateGroup.check(R.id.btn_sea_heavy)
                }

                // Dynamic buttons for Wind Mode
                if (rawMode == "WIND") {
                    updateTackButtons()
                } else {
                    minus1Btn.text = getString(R.string.nautical_adjust_minus_1)
                    plus1Btn.text = getString(R.string.nautical_adjust_plus_1)
                    minus1Btn.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_color_primary_light))
                    plus1Btn.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_color_primary_light))
                }
            }
        }

        engine.registerListener(listener!!)
        listener?.invoke(engine.getCurrentState() ?: MarineState())

        val mapView = (activity as? MapActivity)?.mapView
        mapTouchListener = OsmandMapTileView.TouchListener { resetAutoDismissTimer() }
        mapTouchListener?.let { mapView?.addTouchListener(it) }

        @android.annotation.SuppressLint("ClickableViewAccessibility")
        standbyBtn.setOnTouchListener { v: View, event: MotionEvent ->
            val currentState = engine.getCurrentState()
            val isStandby = currentState?.autopilotState?.uppercase(Locale.US) == "STANDBY"

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (isStandby) {
                        autopilot.setAutopilotMode("auto")
                        v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                        v.performClick()
                    } else {
                        holdStartTime = System.currentTimeMillis()
                        standbyProgress.progress = 0
                        standbyProgress.visibility = View.VISIBLE
                        holdHandler.post(holdRunnable)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    holdHandler.removeCallbacks(holdRunnable)
                    standbyProgress.visibility = View.GONE
                    true
                }
                else -> false
            }
        }

        arcView.onHeadingChanged = { newHeading: Int ->
            autopilot.setTargetHeading(newHeading.toDouble())
        }
        arcView.onHeadingPreview = { previewHeading: Int? ->
            NauticalPlugin.getInstance()?.nauticalMapLayer?.projectionHeading = previewHeading?.toDouble()
        }

        minus1Btn.setOnClickListener {
            resetAutoDismissTimer()
            if (NauticalPlugin.engine?.getCurrentState()?.autopilotState?.uppercase(Locale.US) == "WIND") {
                if (isArmedPort) {
                    autopilot.tack(port = true)
                    isArmedPort = false
                    armHandler.removeCallbacks(resetArmRunnable)
                } else {
                    isArmedPort = true
                    isArmedStbd = false
                    armHandler.removeCallbacks(resetArmRunnable)
                    armHandler.postDelayed(resetArmRunnable, 3000)
                }
                updateTackButtons()
            } else {
                autopilot.adjustHeading(-1.0)
            }
        }
        plus1Btn.setOnClickListener {
            resetAutoDismissTimer()
            if (NauticalPlugin.engine?.getCurrentState()?.autopilotState?.uppercase(Locale.US) == "WIND") {
                if (isArmedStbd) {
                    autopilot.tack(port = false)
                    isArmedStbd = false
                    armHandler.removeCallbacks(resetArmRunnable)
                } else {
                    isArmedStbd = true
                    isArmedPort = false
                    armHandler.removeCallbacks(resetArmRunnable)
                    armHandler.postDelayed(resetArmRunnable, 3000)
                }
                updateTackButtons()
            } else {
                autopilot.adjustHeading(1.0)
            }
        }

        seaStateGroup.addOnButtonCheckedListener { _, checkedId: Int, isChecked: Boolean ->
            if (isChecked) {
                val level = when (checkedId) {
                    R.id.btn_sea_calm -> 1
                    R.id.btn_sea_auto -> 2
                    R.id.btn_sea_heavy -> 4
                    else -> 2
                }
                autopilot.setSeaState(level)
            }
        }

        advancedBtn.setOnClickListener {
            NauticalAdvancedSettingsBottomSheet.newInstance().show(parentFragmentManager, "advanced_settings")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        val engine = NauticalPlugin.engine
        listener?.let { engine?.unregisterListener(it) }
        listener = null
        val mapView = (activity as? MapActivity)?.mapView
        mapTouchListener?.let { mapView?.removeTouchListener(it) }
        mapTouchListener = null
        autoDismissHandler.removeCallbacks(autoDismissRunnable)
        armHandler.removeCallbacks(resetArmRunnable)
    }
}
