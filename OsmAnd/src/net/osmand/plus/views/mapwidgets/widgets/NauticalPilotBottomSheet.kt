package net.osmand.plus.views.mapwidgets.widgets

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import net.osmand.plus.base.BaseMaterialBottomSheetDialogFragment
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.plugins.nautical.ui.HeadingArcView
import net.osmand.plus.plugins.nautical.ui.HeadingErrorDialView
import net.osmand.plus.plugins.nautical.ui.RudderView
import net.osmand.plus.settings.enums.VesselType
import net.osmand.plus.track.GpxDialogs
import net.osmand.shared.gpx.GpxFile
import java.util.*
import kotlin.math.abs

class NauticalPilotBottomSheet : BaseMaterialBottomSheetDialogFragment() {

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
    private var selectedModeOverride: String? = null
    private var lastSog: Double? = null
    private var lastStw: Double? = null
    private var lastVoiceHeading: Int? = null
    private val voiceHandler = Handler(Looper.getMainLooper())
    private val speakRunnable = Runnable {
        lastVoiceHeading?.let { heading ->
            val app = requireActivity().application as net.osmand.plus.OsmandApplication
            app.player?.let { player ->
                val text = getString(R.string.nautical_new_heading, heading)
                player.playCommands(player.newCommandBuilder().attention(text))
            }
        }
    }

    private fun speakHeading(heading: Int) {
        lastVoiceHeading = heading
        voiceHandler.removeCallbacks(speakRunnable)
        voiceHandler.postDelayed(speakRunnable, 1000) // 1 second debounce
    }

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
        autoDismissHandler.postDelayed(autoDismissRunnable, 20000)
    }

    private fun updateTackButtons() {
        val view = view ?: return
        val minus1Btn = view.findViewById<MaterialButton>(R.id.btn_minus_1)
        val plus1Btn = view.findViewById<MaterialButton>(R.id.btn_plus_1)

        val defaultColor = ContextCompat.getColor(requireContext(), if (nightMode) R.color.text_color_primary_dark_v2 else R.color.text_color_primary_light_v2)

        if (isArmedPort) {
            minus1Btn.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_color_negative))
        } else {
            minus1Btn.setTextColor(defaultColor)
        }

        if (isArmedStbd) {
            plus1Btn.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_color_negative))
        } else {
            plus1Btn.setTextColor(defaultColor)
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

        // Telemetry Grid Bindings
        val label11 = view.findViewById<TextView>(R.id.txt_label_1_1)
        val val11 = view.findViewById<TextView>(R.id.txt_value_1_1)
        val icon11 = view.findViewById<ImageView>(R.id.img_icon_1_1)
        val label12 = view.findViewById<TextView>(R.id.txt_label_1_2)
        val val12 = view.findViewById<TextView>(R.id.txt_value_1_2)
        val icon12 = view.findViewById<ImageView>(R.id.img_icon_1_2)
        val label13 = view.findViewById<TextView>(R.id.txt_label_1_3)
        val val13 = view.findViewById<TextView>(R.id.txt_value_1_3)
        val icon13 = view.findViewById<ImageView>(R.id.img_icon_1_3)
        
        val label21 = view.findViewById<TextView>(R.id.txt_label_2_1)
        val val21 = view.findViewById<TextView>(R.id.txt_value_2_1)
        val icon21 = view.findViewById<ImageView>(R.id.img_icon_2_1)
        val label22 = view.findViewById<TextView>(R.id.txt_label_2_2)
        val val22 = view.findViewById<TextView>(R.id.txt_value_2_2)
        val icon22 = view.findViewById<ImageView>(R.id.img_icon_2_2)
        val label23 = view.findViewById<TextView>(R.id.txt_label_2_3)
        val val23 = view.findViewById<TextView>(R.id.txt_value_2_3)
        val icon23 = view.findViewById<ImageView>(R.id.img_icon_2_3)

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

                val sogTrend = when {
                    state.speedOverGround == null || lastSog == null || Math.abs(state.speedOverGround - lastSog!!) < 0.01 -> ""
                    state.speedOverGround > lastSog!! -> " ↑"
                    else -> " ↓"
                }
                lastSog = state.speedOverGround

                val stwTrend = when {
                    state.speedThroughWater == null || lastStw == null || Math.abs(state.speedThroughWater - lastStw!!) < 0.01 -> ""
                    state.speedThroughWater > lastStw!! -> " ↑"
                    else -> " ↓"
                }
                lastStw = state.speedThroughWater

                updateTelemetryGrid(
                    state, selectedModeOverride ?: rawMode,
                    label11, val11, icon11, label12, val12, icon12, label13, val13, icon13,
                    label21, val21, icon21, label22, val22, icon22, label23, val23, icon23,
                    sogTrend, stwTrend
                )

                if (rawMode == "WIND") {
                    updateTackButtons()
                } else {
                    val defaultColor = ContextCompat.getColor(requireContext(), if (nightMode) R.color.text_color_primary_dark_v2 else R.color.text_color_primary_light_v2)
                    minus1Btn.setTextColor(defaultColor)
                    plus1Btn.setTextColor(defaultColor)
                }
            }
        }

        engine.registerListener(listener!!)
        listener?.invoke(engine.getCurrentState() ?: MarineState())

        modeToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                resetAutoDismissTimer()
                when (checkedId) {
                    R.id.btn_mode_compass -> {
                        selectedModeOverride = "AUTO"
                        autopilot.setAutopilotMode("auto")
                    }
                    R.id.btn_mode_wind -> {
                        selectedModeOverride = "WIND"
                        autopilot.setAutopilotMode("wind")
                    }
                    R.id.btn_mode_route -> {
                        selectedModeOverride = "ROUTE"
                        GpxDialogs.selectGPXFile(requireActivity(), false, false, object : net.osmand.CallbackWithObject<Array<GpxFile>> {
                            override fun processResult(result: Array<GpxFile>?): Boolean {
                                if (!result.isNullOrEmpty()) {
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
                                } else {
                                    selectedModeOverride = null
                                }
                                return true
                            }
                        }, nightMode)
                    }
                    R.id.btn_mode_stop -> {
                        selectedModeOverride = "STANDBY"
                        autopilot.stopNavigation()
                    }
                }
                listener?.invoke(engine.getCurrentState() ?: MarineState())
            }
        }

        arcView.onHeadingChanged = { newHeading: Int ->
            autopilot.setTargetHeading(newHeading.toDouble())
            speakHeading(newHeading)
            view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
        }

        errorDial.onHeadingChanged = { newHeading: Int ->
            autopilot.setTargetHeading(newHeading.toDouble())
            speakHeading(newHeading)
        }

        minus1Btn.setOnClickListener {
            resetAutoDismissTimer()
            val state = NauticalPlugin.engine?.getCurrentState()
            if (state?.autopilotState?.uppercase(Locale.US) == "WIND") {
                if (isArmedPort) {
                    val isProa = osmandSettings.NAUTICAL_VESSEL_TYPE.get() == VesselType.PROA
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
                    val isProa = osmandSettings.NAUTICAL_VESSEL_TYPE.get() == VesselType.PROA
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
                                    l11: TextView, v11: TextView, i11: ImageView,
                                    l12: TextView, v12: TextView, i12: ImageView,
                                    l13: TextView, v13: TextView, i13: ImageView,
                                    l21: TextView, v21: TextView, i21: ImageView,
                                    l22: TextView, v22: TextView, i22: ImageView,
                                    l23: TextView, v23: TextView, i23: ImageView,
                                    sogTrend: String, stwTrend: String) {
        val knots = getString(R.string.nautical_unit_knots)
        val nm = getString(R.string.nautical_unit_nm)
        val knotsCoeff = net.osmand.shared.units.SpeedConstants.KNOTS
        
        when (mode) {
            "WIND" -> {
                l11.text = getString(R.string.nautical_tws); v11.text = String.format(Locale.US, "%.1f %s", (state.windSpeedTrue ?: 0.0) * knotsCoeff, knots)
                i11.setImageResource(R.drawable.widget_weather_wind_day)
                
                val awaDeg = state.windDirectionApparent?.let { Math.toDegrees(it).toInt() } ?: 0
                val targetAwaDeg = state.targetWindAngleApparent?.let { Math.toDegrees(it).toInt() } ?: 0
                var windErr = (awaDeg - targetAwaDeg).toFloat()
                while (windErr > 180) windErr -= 360
                while (windErr < -180) windErr += 360
                
                l12.text = getString(R.string.nautical_wind_err); v12.text = String.format(Locale.US, "%.1f°", windErr)
                i12.setImageResource(R.drawable.ic_action_relative_bearing)
                l13.text = getString(R.string.nautical_target_twa); v13.text = String.format(Locale.US, "%d°", targetAwaDeg)
                i13.setImageResource(R.drawable.ic_action_target_direction_on)
                
                l21.text = getString(R.string.nautical_stw); v21.text = String.format(Locale.US, "%.1f%s %s", (state.speedThroughWater ?: 0.0) * knotsCoeff, stwTrend, knots)
                i21.setImageResource(R.drawable.ic_action_sensor_speed_outlined)
                l22.text = getString(R.string.nautical_twa); v22.text = String.format(Locale.US, "%.0f°", Math.toDegrees(state.trueWindAngle ?: 0.0))
                i22.setImageResource(R.drawable.widget_weather_wind_day)
                l23.text = getString(R.string.nautical_polar_target); v23.text = String.format(Locale.US, "%.1f %s", (state.polarTargetSpeed ?: 0.0) * knotsCoeff, knots)
                i23.setImageResource(R.drawable.ic_action_vmg)
            }
            "TRACK", "ROUTE" -> {
                l11.text = getString(R.string.nautical_sog); v11.text = String.format(Locale.US, "%.1f%s %s", (state.speedOverGround ?: 0.0) * knotsCoeff, sogTrend, knots)
                i11.setImageResource(R.drawable.ic_action_speed)
                l12.text = getString(R.string.nautical_xte); v12.text = String.format(Locale.US, "%.3f %s", (state.crossTrackError ?: 0.0) / 1852.0, nm)
                i12.setImageResource(R.drawable.ic_action_nautical_xte)
                l13.text = getString(R.string.nautical_btw); v13.text = String.format(Locale.US, "%.0f°", Math.toDegrees(state.targetHeading ?: 0.0))
                i13.setImageResource(R.drawable.ic_action_bearing)
                
                val dtw = state.distanceToWaypoint
                l21.text = getString(R.string.nautical_dtw); v21.text = if (dtw != null) String.format(Locale.US, "%.2f %s", dtw / 1852.0, nm) else "---"
                i21.setImageResource(R.drawable.ic_action_distance)
                l22.text = getString(R.string.nautical_cog); v22.text = String.format(Locale.US, "%.0f°", Math.toDegrees(state.courseOverGroundTrue ?: 0.0))
                i22.setImageResource(R.drawable.ic_action_cog)
                
                val ttw = state.timeToWaypoint
                l23.text = getString(R.string.nautical_ttw)
                if (ttw != null) {
                    val h = (ttw / 3600).toInt()
                    val m = ((ttw % 3600) / 60).toInt()
                    v23.text = String.format(Locale.US, "%02d:%02d %s", h, m, getString(R.string.nautical_unit_hour_short))
                } else {
                    v23.text = "---"
                }
                i23.setImageResource(R.drawable.widget_time_day)
            }
            else -> {
                l11.text = getString(R.string.nautical_sog); v11.text = String.format(Locale.US, "%.1f%s %s", (state.speedOverGround ?: 0.0) * knotsCoeff, sogTrend, knots)
                i11.setImageResource(R.drawable.ic_action_speed)
                
                val hdgErr = arcView.calculateError(arcView.actualHeading ?: 0, arcView.targetHeading)
                l12.text = getString(R.string.nautical_hdg_err); v12.text = String.format(Locale.US, "%.1f°", hdgErr)
                i12.setImageResource(R.drawable.ic_action_relative_bearing)
                l13.text = getString(R.string.nautical_target_heading); v13.text = String.format(Locale.US, "%d°", state.targetHeading?.let { Math.toDegrees(it).toInt() } ?: 0)
                i13.setImageResource(R.drawable.ic_action_target_direction_on)
                
                l21.text = getString(R.string.nautical_stw); v21.text = String.format(Locale.US, "%.1f%s %s", (state.speedThroughWater ?: 0.0) * knotsCoeff, stwTrend, knots)
                i21.setImageResource(R.drawable.ic_action_sensor_speed_outlined)
                l22.text = getString(R.string.nautical_set_drift); v22.text = String.format(Locale.US, "%03.0f°/%.1f", Math.toDegrees(state.setTrue ?: 0.0), (state.drift ?: 0.0) * knotsCoeff)
                i22.setImageResource(R.drawable.ic_action_bearing)
                l23.text = getString(R.string.nautical_rot); v23.text = String.format(Locale.US, "%.1f %s", Math.toDegrees(state.rateOfTurn ?: 0.0) * 60.0, getString(R.string.nautical_unit_rot_short))
                i23.setImageResource(R.drawable.ic_action_nautical_rot)
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
