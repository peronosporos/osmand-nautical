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
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.Chip
import com.google.android.material.progressindicator.CircularProgressIndicator
import net.osmand.plus.base.BottomSheetDialogFragment
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.plugins.nautical.ui.HeadingArcView
import net.osmand.plus.plugins.nautical.ui.RudderView
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.views.OsmandMapTileView
import net.osmand.plus.settings.enums.VesselType
import java.util.*
import kotlin.math.abs

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
    private val autoDismissRunnable = Runnable { dismissAllowingStateLoss() }
    private var mapTouchListener: OsmandMapTileView.TouchListener? = null

    companion object {
        @JvmStatic
        fun newInstance(): NauticalPilotBottomSheet {
            return NauticalPilotBottomSheet()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            window.setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setBackgroundDrawableResource(android.R.color.transparent)
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
        val state = NauticalPlugin.engine?.getCurrentState()
        val wind = state?.windDirectionApparent ?: 0.0
        val isDownwind = abs(Math.toDegrees(wind)) > 90
        val isProa = settings.NAUTICAL_VESSEL_TYPE.get() == VesselType.PROA

        if (isArmedPort) {
            minus1Btn.text = getString(if (isProa) R.string.nautical_confirm_shunt else if (isDownwind) R.string.nautical_confirm_gybe else R.string.nautical_confirm_tack)
            minus1Btn.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_color_negative))
        } else {
            minus1Btn.text = getString(if (isProa) R.string.nautical_shunt else if (isDownwind) R.string.nautical_gybe_port else R.string.nautical_tack_port)
            minus1Btn.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_color_primary_light))
        }

        if (isArmedStbd) {
            plus1Btn.text = getString(if (isProa) R.string.nautical_confirm_shunt else if (isDownwind) R.string.nautical_confirm_gybe else R.string.nautical_confirm_tack)
            plus1Btn.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_color_negative))
        } else {
            plus1Btn.text = getString(if (isProa) R.string.nautical_shunt else if (isDownwind) R.string.nautical_gybe_stbd else R.string.nautical_tack_stbd)
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
        val minus10Btn = view.findViewById<Button>(R.id.btn_minus_10)
        val plus10Btn = view.findViewById<Button>(R.id.btn_plus_10)
        val rudderView = view.findViewById<RudderView>(R.id.rudder_view)
        val standbyProgress = view.findViewById<CircularProgressIndicator>(R.id.standby_progress)
        val headerBg = view.findViewById<View>(R.id.header_bg)
        val sailingInfo = view.findViewById<View>(R.id.sailing_info_container)
        val awaCurrentTxt = view.findViewById<TextView>(R.id.txt_awa_current)
        val awaTargetTxt = view.findViewById<TextView>(R.id.txt_awa_target)

        arcView.setNightMode(nightMode)
        rudderView.setNightMode(nightMode)

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
                    standbyBtn.performClick()
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
                    "SHUNTING" -> getString(R.string.nautical_shunting)
                    else -> rawMode
                }
                modeBadge.text = getString(R.string.nautical_mode_label, modeLabel)

                when (rawMode) {
                    "STANDBY" -> {
                        standbyBtn.text = getString(R.string.nautical_engage_auto)
                        standbyBtn.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.active_color_primary_light))
                        headerBg.setBackgroundColor(ContextCompat.getColor(requireContext(), if (nightMode) R.color.activity_background_color_dark else R.color.activity_background_color_light))
                        modeBadge.setChipBackgroundColorResource(R.color.nautical_status_bg_standby)
                    }
                    "AUTO" -> {
                        standbyBtn.text = getString(R.string.nautical_disengage_standby)
                        standbyBtn.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.text_color_negative))
                        headerBg.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.nautical_status_bg_active))
                        modeBadge.setChipBackgroundColorResource(R.color.nautical_status_green)
                    }
                    "WIND" -> {
                        standbyBtn.text = getString(R.string.nautical_disengage_standby)
                        standbyBtn.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.text_color_negative))
                        headerBg.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.active_color_secondary_light))
                        modeBadge.setChipBackgroundColorResource(R.color.active_color_primary_light)
                    }
                    else -> {
                        standbyBtn.text = getString(R.string.nautical_disengage_standby)
                        standbyBtn.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.text_color_negative))
                    }
                }

                val targetHeading = state.targetHeading ?: state.headingTrue ?: 0.0
                arcView.targetHeading = Math.toDegrees(targetHeading).toInt()
                state.headingTrue?.let { arcView.actualHeading = Math.toDegrees(it).toInt() }
                
                state.windDirectionApparent?.let { arcView.windAngleApparent = Math.toDegrees(it).toInt() }
                state.targetWindAngleApparent?.let { arcView.targetWindAngleApparent = Math.toDegrees(it).toInt() }
                
                arcView.isTacking = (rawMode == "TACKING") || (rawMode == "GYBING") || (rawMode == "SHUNTING")

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
                    minus10Btn.visibility = View.INVISIBLE
                    plus10Btn.visibility = View.INVISIBLE
                    sailingInfo.visibility = View.VISIBLE
                    
                    state.windDirectionApparent?.let {
                        awaCurrentTxt.text = getString(R.string.nautical_awa_current, Math.toDegrees(it))
                    }
                    state.targetWindAngleApparent?.let {
                        awaTargetTxt.text = getString(R.string.nautical_awa_target, Math.toDegrees(it))
                    }
                } else {
                    minus1Btn.text = getString(R.string.nautical_adjust_minus_1)
                    plus1Btn.text = getString(R.string.nautical_adjust_plus_1)
                    minus1Btn.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_color_primary_light))
                    plus1Btn.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_color_primary_light))
                    minus10Btn.visibility = View.VISIBLE
                    plus10Btn.visibility = View.VISIBLE
                    sailingInfo.visibility = View.GONE
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
            view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
        }
        arcView.onHeadingPreview = { previewHeading: Int? ->
            NauticalPlugin.getInstance()?.nauticalMapLayer?.projectionHeading = previewHeading?.toDouble()
        }

        minus1Btn.setOnClickListener {
            resetAutoDismissTimer()
            val state = NauticalPlugin.engine?.getCurrentState()
            if (state?.autopilotState?.uppercase(Locale.US) == "WIND") {
                val isDownwind = abs(Math.toDegrees(state.windDirectionApparent ?: 0.0)) > 90
                if (isArmedPort) {
                    val isProa = settings.NAUTICAL_VESSEL_TYPE.get() == VesselType.PROA
                    if (isProa) {
                        autopilot.shunt()
                    } else if (isDownwind) {
                        autopilot.gybe(port = true)
                    } else {
                        autopilot.tack(port = true)
                    }
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
            it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
        }
        plus1Btn.setOnClickListener {
            resetAutoDismissTimer()
            val state = NauticalPlugin.engine?.getCurrentState()
            if (state?.autopilotState?.uppercase(Locale.US) == "WIND") {
                val isDownwind = Math.abs(Math.toDegrees(state.windDirectionApparent ?: 0.0)) > 90
                if (isArmedStbd) {
                    val isProa = settings.NAUTICAL_VESSEL_TYPE.get() == VesselType.PROA
                    if (isProa) {
                        autopilot.shunt()
                    } else if (isDownwind) {
                        autopilot.gybe(port = false)
                    } else {
                        autopilot.tack(port = false)
                    }
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
            it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
        }

        minus10Btn.setOnClickListener {
            resetAutoDismissTimer()
            autopilot.adjustHeading(-10.0)
            it.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        }
        plus10Btn.setOnClickListener {
            resetAutoDismissTimer()
            autopilot.adjustHeading(10.0)
            it.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
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
