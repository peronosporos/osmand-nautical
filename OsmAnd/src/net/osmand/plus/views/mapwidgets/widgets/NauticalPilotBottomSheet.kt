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
import net.osmand.plus.plugins.nautical.ui.HeadingErrorDialView
import net.osmand.plus.plugins.nautical.ui.RudderView
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.views.OsmandMapTileView
import net.osmand.plus.settings.enums.VesselType
import net.osmand.plus.track.GpxDialogs
import net.osmand.CallbackWithObject
import net.osmand.shared.gpx.GpxFile
import net.osmand.shared.gpx.primitives.WptPt
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

    private lateinit var errorDial: HeadingErrorDialView
    private lateinit var arcView: HeadingArcView

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
        autoDismissHandler.postDelayed(autoDismissRunnable, 20000) // 20 seconds for overhaul
    }

    private fun updateTackButtons() {
        val view = view ?: return
        val minus1Btn = view.findViewById<MaterialButton>(R.id.btn_minus_1)
        val plus1Btn = view.findViewById<MaterialButton>(R.id.btn_plus_1)

        if (isArmedPort) {
            minus1Btn.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_color_negative))
        } else {
            minus1Btn.setTextColor(ContextCompat.getColor(requireContext(), if (nightMode) R.color.text_color_primary_dark else R.color.text_color_primary_light))
        }

        if (isArmedStbd) {
            plus1Btn.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_color_negative))
        } else {
            plus1Btn.setTextColor(ContextCompat.getColor(requireContext(), if (nightMode) R.color.text_color_primary_dark else R.color.text_color_primary_light))
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

        errorDial = view.findViewById(R.id.heading_error_dial)
        arcView = view.findViewById(R.id.heading_arc_view)
        val modeToggleGroup = view.findViewById<MaterialButtonToggleGroup>(R.id.mode_toggle_group)
        val advancedBtn = view.findViewById<View>(R.id.btn_advanced)

        val minus1Btn = view.findViewById<MaterialButton>(R.id.btn_minus_1)
        val plus1Btn = view.findViewById<MaterialButton>(R.id.btn_plus_1)
        val minus10Btn = view.findViewById<MaterialButton>(R.id.btn_minus_10)
        val plus10Btn = view.findViewById<MaterialButton>(R.id.btn_plus_10)

        // Telemetry Grid
        val label11 = view.findViewById<TextView>(R.id.txt_label_1_1)
        val val11 = view.findViewById<TextView>(R.id.txt_value_1_1)
        val label12 = view.findViewById<TextView>(R.id.txt_label_1_2)
        val val12 = view.findViewById<TextView>(R.id.txt_value_1_2)
        val label13 = view.findViewById<TextView>(R.id.txt_label_1_3)
        val val13 = view.findViewById<TextView>(R.id.txt_value_1_3)
        
        val label21 = view.findViewById<TextView>(R.id.txt_label_2_1)
        val val21 = view.findViewById<TextView>(R.id.txt_value_2_1)
        val label22 = view.findViewById<TextView>(R.id.txt_label_2_2)
        val val22 = view.findViewById<TextView>(R.id.txt_value_2_2)
        val label23 = view.findViewById<TextView>(R.id.txt_label_2_3)
        val val23 = view.findViewById<TextView>(R.id.txt_value_2_3)

        val rudderView = view.findViewById<RudderView>(R.id.rudder_view)

        arcView.setNightMode(nightMode)
        errorDial.setNightMode(nightMode)
        rudderView.setNightMode(nightMode)

        listener = { state ->
            view.post {
                if (!isAdded) return@post
                resetAutoDismissTimer()

                val rawMode = state.autopilotState.uppercase(Locale.US)
                arcView.currentMode = rawMode

                when (rawMode) {
                    "STANDBY" -> modeToggleGroup.uncheck(R.id.btn_mode_compass)
                    "AUTO" -> modeToggleGroup.check(R.id.btn_mode_compass)
                    "WIND" -> modeToggleGroup.check(R.id.btn_mode_wind)
                    "TRACK", "ROUTE" -> modeToggleGroup.check(R.id.btn_mode_route)
                }

                // Centralized Error Calculation
                val actualH = state.headingTrue?.let { Math.toDegrees(it) } ?: 0.0
                val targetH = (state.targetHeading ?: state.headingTrue)?.let { Math.toDegrees(it) } ?: 0.0
                var hdgErr = (actualH - targetH).toFloat()
                while (hdgErr > 180) hdgErr -= 360
                while (hdgErr < -180) hdgErr += 360

                errorDial.headingError = hdgErr
                arcView.targetHeading = targetH.toInt()
                arcView.actualHeading = actualH.toInt()

                state.windDirectionApparent?.let { arcView.windAngleApparent = Math.toDegrees(it).toInt() }
                state.targetWindAngleApparent?.let { arcView.targetWindAngleApparent = Math.toDegrees(it).toInt() }
                state.rudderAngle?.let { rudderView?.setRudderAngle(it) }

                // Telemetry Matrix Switching
                updateTelemetryGrid(state, rawMode, 
                    label11, val11, label12, val12, label13, val13,
                    label21, val21, label22, val22, label23, val23)

                if (rawMode == "WIND") {
                    updateTackButtons()
                } else {
                    minus1Btn.setTextColor(ContextCompat.getColor(requireContext(), if (nightMode) R.color.text_color_primary_dark else R.color.text_color_primary_light))
                    plus1Btn.setTextColor(ContextCompat.getColor(requireContext(), if (nightMode) R.color.text_color_primary_dark else R.color.text_color_primary_light))
                }
            }
        }

        engine.registerListener(listener!!)
        listener?.invoke(engine.getCurrentState() ?: MarineState())

        modeToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                resetAutoDismissTimer()
                when (checkedId) {
                    R.id.btn_mode_compass -> autopilot.setAutopilotMode("auto")
                    R.id.btn_mode_wind -> autopilot.setAutopilotMode("wind")
                    R.id.btn_mode_route -> {
                        GpxDialogs.selectGPXFile(requireActivity(), false, false, object : CallbackWithObject<Array<GpxFile>> {
                            override fun processResult(result: Array<GpxFile>?): Boolean {
                                if (result != null && result.isNotEmpty()) {
                                    val gpx = result[0]
                                    val points = mutableListOf<Pair<Double, Double>>()
                                    gpx.tracks.forEach { track ->
                                        track.segments.forEach { segment ->
                                            segment.points.forEach { pt ->
                                                points.add(Pair(pt.lat, pt.lon))
                                            }
                                        }
                                    }
                                    if (points.isNotEmpty()) {
                                        engine.loadRoute(points)
                                        autopilot.setAutopilotMode("route")
                                    }
                                }
                                return true
                            }
                        }, false)
                    }
                    R.id.btn_mode_stop -> autopilot.stopNavigation()
                }
            }
        }

        arcView.onHeadingChanged = { newHeading: Int ->
            autopilot.setTargetHeading(newHeading.toDouble())
            view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
        }

        minus1Btn.setOnClickListener {
            resetAutoDismissTimer()
            val state = NauticalPlugin.engine?.getCurrentState()
            if (state?.autopilotState?.uppercase(Locale.US) == "WIND") {
                if (isArmedPort) {
                    val isProa = settings.NAUTICAL_VESSEL_TYPE.get() == VesselType.PROA
                    if (isProa) autopilot.shunt() else autopilot.tack(port = true)
                    isArmedPort = false
                    armHandler.removeCallbacks(resetArmRunnable)
                } else {
                    isArmedPort = true
                    isArmedStbd = false
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
                if (isArmedStbd) {
                    val isProa = settings.NAUTICAL_VESSEL_TYPE.get() == VesselType.PROA
                    if (isProa) autopilot.shunt() else autopilot.tack(port = false)
                    isArmedStbd = false
                    armHandler.removeCallbacks(resetArmRunnable)
                } else {
                    isArmedStbd = true
                    isArmedPort = false
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

    private fun updateTelemetryGrid(state: MarineState, mode: String,
                                    l11: TextView, v11: TextView, l12: TextView, v12: TextView, l13: TextView, v13: TextView,
                                    l21: TextView, v21: TextView, l22: TextView, v22: TextView, l23: TextView, v23: TextView) {
        val knots = getString(R.string.nautical_unit_knots)
        
        when (mode) {
            "WIND" -> {
                l11.text = "TWS"; v11.text = String.format(Locale.US, "%.1f %s", (state.windSpeedTrue ?: 0.0) * 1.94384, knots)
                l12.text = "AWA CURR"; v12.text = String.format(Locale.US, "%d°", state.windDirectionApparent?.let { Math.toDegrees(it).toInt() } ?: 0)
                l13.text = "AWA TGT"; v13.text = String.format(Locale.US, "%d°", state.targetWindAngleApparent?.let { Math.toDegrees(it).toInt() } ?: 0)
                
                l21.text = "STW"; v21.text = String.format(Locale.US, "%.1f %s", (state.speedThroughWater ?: 0.0) * 1.94384, knots)
                l22.text = "TWA"; v22.text = String.format(Locale.US, "%.0f°", Math.toDegrees(state.trueWindAngle ?: 0.0))
                l23.text = "POLAR TGT"; v23.text = String.format(Locale.US, "%.1f %s", (state.polarTargetSpeed ?: 0.0) * 1.94384, knots)
            }
            "TRACK", "ROUTE" -> {
                l11.text = "SOG"; v11.text = String.format(Locale.US, "%.1f %s", (state.speedOverGround ?: 0.0) * 1.94384, knots)
                l12.text = "XTE"; v12.text = String.format(Locale.US, "%.2f NM", (state.crossTrackError ?: 0.0) * 0.000539957)
                l13.text = "BTW"; v13.text = String.format(Locale.US, "%.0f°", Math.toDegrees(state.targetHeading ?: 0.0))
                
                l21.text = "DTW"; v21.text = "---" // Dist to Waypoint not in SignalK message yet
                l22.text = "COG"; v22.text = String.format(Locale.US, "%.0f°", Math.toDegrees(state.courseOverGroundTrue ?: 0.0))
                l23.text = "TTW"; v23.text = String.format(Locale.US, "%.0fm", (state.timeToWaypoint ?: 0.0) / 60.0)
            }
            else -> { // Heading Mode
                l11.text = "SOG"; v11.text = String.format(Locale.US, "%.1f %s", (state.speedOverGround ?: 0.0) * 1.94384, knots)
                l12.text = "HDG ERR"; v12.text = String.format(Locale.US, "%.1f°", arcView.calculateError(arcView.actualHeading ?: 0, arcView.targetHeading))
                l13.text = "TGT HDG"; v13.text = String.format(Locale.US, "%d°", state.targetHeading?.let { Math.toDegrees(it).toInt() } ?: 0)
                
                l21.text = "STW"; v21.text = String.format(Locale.US, "%.1f %s", (state.speedThroughWater ?: 0.0) * 1.94384, knots)
                l22.text = "SET/DRFT"; v22.text = String.format(Locale.US, "%.0f/%.1f", Math.toDegrees(state.setTrue ?: 0.0), (state.drift ?: 0.0) * 1.94384)
                l23.text = "ROT"; v23.text = String.format(Locale.US, "%.1f°/s", Math.toDegrees(state.rateOfTurn ?: 0.0))
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        val engine = NauticalPlugin.engine
        listener?.let { engine?.unregisterListener(it) }
        listener = null
        autoDismissHandler.removeCallbacks(autoDismissRunnable)
        armHandler.removeCallbacks(resetArmRunnable)
    }
}
