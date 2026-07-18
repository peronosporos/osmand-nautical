package net.osmand.plus.views.mapwidgets.widgets

import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
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
        val minus1Btn = view.findViewById<MaterialButton>(R.id.btn_minus_1)
        val plus1Btn = view.findViewById<MaterialButton>(R.id.btn_plus_1)

        if (isArmedPort) {
            // Confirm tack/gybe/shunt
            minus1Btn.setIconResource(R.drawable.ic_action_done)
            minus1Btn.setIconTintResource(R.color.text_color_negative)
        } else {
            minus1Btn.setIconResource(R.drawable.ic_action_trim_left)
            minus1Btn.setIconTintResource(R.color.icon_color_default_light)
        }

        if (isArmedStbd) {
            plus1Btn.setIconResource(R.drawable.ic_action_done)
            plus1Btn.setIconTintResource(R.color.text_color_negative)
        } else {
            plus1Btn.setIconResource(R.drawable.ic_action_trim_right)
            plus1Btn.setIconTintResource(R.color.icon_color_default_light)
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
        val standbyBtn = view.findViewById<MaterialButton>(R.id.btn_standby)
        val arcView = view.findViewById<HeadingArcView>(R.id.heading_arc_view)
        val modeToggleGroup = view.findViewById<MaterialButtonToggleGroup>(R.id.mode_toggle_group)
        val advancedBtn = view.findViewById<View>(R.id.btn_advanced)
        
        val minus1Btn = view.findViewById<MaterialButton>(R.id.btn_minus_1)
        val plus1Btn = view.findViewById<MaterialButton>(R.id.btn_plus_1)
        val minus10Btn = view.findViewById<MaterialButton>(R.id.btn_minus_10)
        val plus10Btn = view.findViewById<MaterialButton>(R.id.btn_plus_10)
        
        val txtSog = view.findViewById<TextView>(R.id.txt_sog_value)
        val txtDepth = view.findViewById<TextView>(R.id.txt_depth_value)
        val txtXte = view.findViewById<TextView>(R.id.txt_xte_value)
        
        val rudderView = view.findViewById<RudderView>(R.id.rudder_view)
        val standbyProgress = view.findViewById<CircularProgressIndicator>(R.id.standby_progress)
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
                modeBadge.text = rawMode

                val osmandOrange = ContextCompat.getColor(requireContext(), R.color.icon_color_osmand_light)

                when (rawMode) {
                    "STANDBY" -> {
                        standbyBtn.text = getString(R.string.nautical_engage_autopilot_btn)
                        standbyBtn.backgroundTintList = ColorStateList.valueOf(osmandOrange)
                        modeBadge.setChipBackgroundColorResource(R.color.icon_color_default_light)
                        modeToggleGroup.uncheck(R.id.btn_mode_compass)
                        modeToggleGroup.uncheck(R.id.btn_mode_wind)
                    }
                    "AUTO" -> {
                        standbyBtn.text = getString(R.string.nautical_stop_label)
                        standbyBtn.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.text_color_negative))
                        modeBadge.setChipBackgroundColorResource(R.color.icon_color_osmand_light)
                        modeToggleGroup.check(R.id.btn_mode_compass)
                    }
                    "WIND" -> {
                        standbyBtn.text = getString(R.string.nautical_stop_label)
                        standbyBtn.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.text_color_negative))
                        modeBadge.setChipBackgroundColorResource(R.color.active_color_primary_light)
                        modeToggleGroup.check(R.id.btn_mode_wind)
                    }
                    else -> {
                        standbyBtn.text = getString(R.string.nautical_stop_label)
                        standbyBtn.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.text_color_negative))
                    }
                }

                // Telemetry
                txtSog.text = String.format(Locale.US, "%.1f kn", (state.speedOverGround ?: 0.0) * 1.94384)
                txtDepth.text = String.format(Locale.US, "%.1f m", state.depthBelowTransducer ?: 0.0)
                
                val xte = state.crossTrackError
                if (xte != null) {
                    val xteNm = abs(xte) * 0.000539957 // meters to NM
                    val side = if (xte < 0) "L" else "R"
                    txtXte.text = String.format(Locale.US, "%.2f %s", xteNm, side)
                } else {
                    txtXte.text = "---"
                }

                val targetHeading = state.targetHeading ?: state.headingTrue ?: 0.0
                arcView.targetHeading = Math.toDegrees(targetHeading).toInt()
                state.headingTrue?.let { arcView.actualHeading = Math.toDegrees(it).toInt() }
                
                state.windDirectionApparent?.let { arcView.windAngleApparent = Math.toDegrees(it).toInt() }
                state.targetWindAngleApparent?.let { arcView.targetWindAngleApparent = Math.toDegrees(it).toInt() }
                
                arcView.isTacking = (rawMode == "TACKING") || (rawMode == "GYBING") || (rawMode == "SHUNTING")

                state.rudderAngle?.let { rudderView?.setRudderAngle(it) }

                // Dynamic buttons for Wind Mode
                if (rawMode == "WIND") {
                    updateTackButtons()
                    sailingInfo.visibility = View.VISIBLE
                    state.windDirectionApparent?.let {
                        awaCurrentTxt.text = getString(R.string.nautical_awa_current, Math.toDegrees(it).toInt())
                    }
                    state.targetWindAngleApparent?.let {
                        awaTargetTxt.text = getString(R.string.nautical_awa_target, Math.toDegrees(it).toInt())
                    }
                } else {
                    minus1Btn.setIconResource(R.drawable.ic_action_trim_left)
                    plus1Btn.setIconResource(R.drawable.ic_action_trim_right)
                    sailingInfo.visibility = View.GONE
                }
            }
        }

        engine.registerListener(listener!!)
        listener?.invoke(engine.getCurrentState() ?: MarineState())

        modeToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btn_mode_compass -> autopilot.setAutopilotMode("auto")
                    R.id.btn_mode_wind -> autopilot.setAutopilotMode("wind")
                }
            }
        }

        val mapView = (activity as? MapActivity)?.mapView
        mapTouchListener = OsmandMapTileView.TouchListener { resetAutoDismissTimer() }
        mapTouchListener?.let { mapView?.addTouchListener(it) }

        @android.annotation.SuppressLint("ClickableViewAccessibility")
        standbyBtn.setOnTouchListener { v: View, event: MotionEvent ->
            val currentState = engine.getCurrentState()
            val isStandby = currentState?.autopilotState?.lowercase(Locale.US) == "standby"

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (isStandby) {
                        autopilot.setAutopilotMode("auto")
                        v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
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
                val isDownwind = abs(Math.toDegrees(state.windDirectionApparent ?: 0.0)) > 90
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
