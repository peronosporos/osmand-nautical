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
import com.google.android.material.slider.Slider
import net.osmand.plus.base.BaseMaterialBottomSheetDialogFragment
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.ConnectionStatus
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.plugins.nautical.ui.HeadingArcView
import net.osmand.plus.plugins.nautical.ui.HeadingErrorLinearView
import net.osmand.plus.plugins.nautical.ui.RudderView
import net.osmand.plus.settings.enums.VesselType
import net.osmand.plus.track.GpxDialogs
import java.util.*

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
            val app = activity?.application as? net.osmand.plus.OsmandApplication
            app?.player?.let { player ->
                val text = getString(R.string.nautical_new_course, heading)
                player.playCommands(player.newCommandBuilder().attention(text))
            }
        }
    }

    private fun speakHeading(heading: Int) {
        lastVoiceHeading = heading
        voiceHandler.removeCallbacks(speakRunnable)
        voiceHandler.postDelayed(speakRunnable, 1000) // 1 second debounce
    }

    private fun speakMode(mode: String) {
        val app = activity?.application as? net.osmand.plus.OsmandApplication
        app?.player?.let { player ->
            val textId = when (mode.uppercase(Locale.US)) {
                "AUTO" -> R.string.nautical_mode_engaged_auto
                "WIND" -> R.string.nautical_mode_engaged_wind
                "TRACK" -> R.string.nautical_mode_engaged_track
                "STANDBY" -> R.string.nautical_mode_engaged_standby
                else -> return
            }
            player.playCommands(player.newCommandBuilder().attention(getString(textId)))
        }
    }

    private fun speakManeuver(tacking: Boolean, port: Boolean) {
        val app = activity?.application as? net.osmand.plus.OsmandApplication
        app?.player?.let { player ->
            val textId = if (tacking) {
                if (port) R.string.nautical_tack_port else R.string.nautical_tack_stbd
            } else {
                if (port) R.string.nautical_gybe_port else R.string.nautical_gybe_stbd
            }
            player.playCommands(player.newCommandBuilder().attention(getString(textId)))
        }
    }

    private lateinit var switchesRecycler: androidx.recyclerview.widget.RecyclerView
    private var switchesAdapter: NauticalSwitchesAdapter? = null

    private lateinit var errorLinear: HeadingErrorLinearView
    private lateinit var arcView: HeadingArcView
    private lateinit var steeringCard: View

    companion object {
        @JvmStatic
        fun newInstance(): NauticalPilotBottomSheet {
            return NauticalPilotBottomSheet()
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            // Removing FLAG_NOT_TOUCH_MODAL to allow "tap outside to dismiss"
            // window.setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setBackgroundDrawableResource(android.R.color.transparent)
        }
        resetAutoDismissTimer()
    }

    private fun resetAutoDismissTimer() {
        autoDismissHandler.removeCallbacks(autoDismissRunnable)
        autoDismissHandler.postDelayed(autoDismissRunnable, 60000)
        updateTackButtons()
    }

    private fun updateTackButtons() {
        val view = view ?: return
        val minus1Btn = view.findViewById<MaterialButton>(R.id.btn_minus_1)
        val plus1Btn = view.findViewById<MaterialButton>(R.id.btn_plus_1)
        val state = NauticalPlugin.engine?.getCurrentState()
        val isProa = osmandSettings.NAUTICAL_VESSEL_TYPE.get() == VesselType.PROA
        val upwind = state?.windDirectionApparent?.let { kotlin.math.abs(Math.toDegrees(it)) < 90.0 } ?: true

        val defaultColor = ContextCompat.getColor(requireContext(), if (nightMode) R.color.text_color_primary_dark_v2 else R.color.text_color_primary_light_v2)
        val armedColor = ContextCompat.getColor(requireContext(), R.color.text_color_negative)

        if (isArmedPort) {
            minus1Btn.setTextColor(armedColor)
            minus1Btn.text = if (isProa) getString(R.string.nautical_shunt) else if (upwind) getString(R.string.nautical_tack) else getString(R.string.nautical_gybe)
        } else {
            minus1Btn.setTextColor(defaultColor)
            minus1Btn.text = if (isProa) getString(R.string.nautical_shunt) else "-1"
        }

        if (isArmedStbd) {
            plus1Btn.setTextColor(armedColor)
            plus1Btn.text = if (isProa) getString(R.string.nautical_shunt) else if (upwind) getString(R.string.nautical_tack) else getString(R.string.nautical_gybe)
        } else {
            plus1Btn.setTextColor(defaultColor)
            plus1Btn.text = if (isProa) getString(R.string.nautical_shunt) else "+1"
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

        errorLinear = view.findViewById(R.id.heading_error_linear)
        arcView = view.findViewById(R.id.heading_arc_view)
        steeringCard = view.findViewById(R.id.steering_card)
        switchesRecycler = view.findViewById(R.id.switches_recycler)

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
        val seaStateSlider = view.findViewById<Slider>(R.id.slider_sea_state)
        val autoSeaStateSwitch = view.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.switch_auto_sea_state)

        arcView.setNightMode(nightMode)
        errorLinear.setNightMode(nightMode)
        rudderView.setNightMode(nightMode)

        listener = { state ->
            view.post {
                if (!isAdded) return@post
                resetAutoDismissTimer()

                val rawMode = state.autopilotState.uppercase(Locale.US)
                val pendingMode = state.pendingAutopilotState?.uppercase(Locale.US)
                arcView.currentMode = rawMode

                autoSeaStateSwitch.isChecked = state.isAutoSeaStateEnabled
                seaStateSlider.isEnabled = !state.isAutoSeaStateEnabled
                seaStateSlider.alpha = if (state.isAutoSeaStateEnabled) 0.5f else 1.0f
                if (state.isAutoSeaStateEnabled && state.seaState != null) {
                    seaStateSlider.value = state.seaState.toFloat()
                }

                val displayMode = pendingMode ?: rawMode
                when (displayMode) {
                    "STANDBY" -> modeToggleGroup.check(R.id.btn_mode_stop)
                    "AUTO" -> modeToggleGroup.check(R.id.btn_mode_compass)
                    "WIND" -> modeToggleGroup.check(R.id.btn_mode_wind)
                    "TRACK", "ROUTE" -> modeToggleGroup.check(R.id.btn_mode_route)
                    else -> modeToggleGroup.clearChecked()
                }

                val windBtn = view.findViewById<View>(R.id.btn_mode_wind)
                val routeBtn = view.findViewById<View>(R.id.btn_mode_route)
                val hasWind = state.windDirectionApparent != null
                val hasRoute = engine.isFollowingRoute
                
                windBtn.isEnabled = hasWind
                windBtn.alpha = if (hasWind) 1.0f else 0.5f
                routeBtn.isEnabled = hasRoute
                routeBtn.alpha = if (hasRoute) 1.0f else 0.5f
                
                modeToggleGroup.alpha = if (pendingMode != null) 0.6f else 1.0f

                val actualH = state.headingTrue?.let { Math.toDegrees(it) } ?: 0.0
                val targetH = (state.pendingTargetHeading ?: state.targetHeading ?: state.headingTrue)?.let { Math.toDegrees(it) } ?: 0.0
                var hdgErr = (actualH - targetH).toFloat()
                while (hdgErr > 180) hdgErr -= 360
                while (hdgErr < -180) hdgErr += 360

                errorLinear.headingError = if (rawMode == "WIND") {
                    val awaDeg = state.windDirectionApparent?.let { Math.toDegrees(it).toInt() } ?: 0
                    val targetAwaDeg = state.targetWindAngleApparent?.let { Math.toDegrees(it).toInt() } ?: 0
                    var windErr = (awaDeg - targetAwaDeg).toFloat()
                    while (windErr > 180) windErr -= 360
                    while (windErr < -180) windErr += 360
                    errorLinear.label = getString(R.string.nautical_wind_err)
                    windErr
                } else {
                    errorLinear.label = getString(R.string.nautical_hdg_err)
                    hdgErr
                }
                arcView.targetHeading = targetH.toInt()
                arcView.actualHeading = actualH.toInt()
                arcView.alpha = if (state.pendingTargetHeading != null) 0.7f else 1.0f

                state.windDirectionApparent?.let { arcView.windAngleApparent = Math.toDegrees(it).toInt() }
                state.targetWindAngleApparent?.let { arcView.targetWindAngleApparent = Math.toDegrees(it).toInt() }
                state.rudderAngle?.let { rudderView?.setRudderAngle(it) }

                // 1. Connection Health Feedback
                val isStale = state.connectionStatus != ConnectionStatus.CONNECTED
                steeringCard.alpha = if (isStale) 0.5f else 1.0f

                // 2. Off-Course Visual Alert (Centralized)
                if (state.isOffCourse) {
                    errorLinear.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.nautical_status_bg_emergency))
                } else {
                    errorLinear.setBackgroundColor(Color.TRANSPARENT)
                }

                // 3. Proa UI Context - Moved to updateTackButtons()
                val sogTrend = when {
                    (state.speedOverGround == null) || (lastSog == null) || (kotlin.math.abs(state.speedOverGround - lastSog!!) < 0.01) -> ""
                    state.speedOverGround > lastSog!! -> getString(R.string.nautical_trend_up)
                    else -> getString(R.string.nautical_trend_down)
                }
                lastSog = state.speedOverGround

                val stwTrend = when {
                    (state.speedThroughWater == null) || (lastStw == null) || (kotlin.math.abs(state.speedThroughWater - lastStw!!) < 0.01) -> ""
                    state.speedThroughWater > lastStw!! -> getString(R.string.nautical_trend_up)
                    else -> getString(R.string.nautical_trend_down)
                }
                lastStw = state.speedThroughWater

                updateTelemetryGrid(
                    state, selectedModeOverride ?: rawMode,
                    label11, val11, icon11, label12, val12, icon12, label13, val13, icon13,
                    label21, val21, icon21, label22, val22, icon22, label23, val23, icon23,
                    sogTrend, stwTrend,
                )

                if (switchesAdapter == null) {
                    switchesAdapter = NauticalSwitchesAdapter(state.switches) { path ->
                        NauticalPlugin.electrical?.toggleSwitch(path)
                    }
                    switchesRecycler.adapter = switchesAdapter
                } else {
                    switchesAdapter?.updateSwitches(state.switches)
                }
                view.findViewById<View>(R.id.switching_title).visibility = if (state.switches.isEmpty()) View.GONE else View.VISIBLE
                switchesRecycler.visibility = if (state.switches.isEmpty()) View.GONE else View.VISIBLE

                if (rawMode == "WIND") {
                    updateTackButtons()
                } else {
                    val defaultColor = ContextCompat.getColor(requireContext(), if (nightMode) R.color.text_color_primary_dark_v2 else R.color.text_color_primary_light_v2)
                    minus1Btn.setTextColor(defaultColor)
                    minus1Btn.text = "-1"
                    plus1Btn.setTextColor(defaultColor)
                    plus1Btn.text = "+1"
                }
            }
        }

        engine.registerListener(listener!!)
        listener?.invoke(engine.getCurrentState() ?: MarineState())

        modeToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                resetAutoDismissTimer()

                val action: () -> Unit = {
                    when (checkedId) {
                        R.id.btn_mode_compass -> {
                            selectedModeOverride = "AUTO"
                            autopilot.setAutopilotMode("auto")
                            speakMode("AUTO")
                        }
                        R.id.btn_mode_wind -> {
                            selectedModeOverride = "WIND"
                            autopilot.setAutopilotMode("wind")
                            speakMode("WIND")
                        }
                        R.id.btn_mode_route -> {
                            selectedModeOverride = "ROUTE"
                            GpxDialogs.selectGPXFile(
                                requireActivity(), false, false,
                                { result ->
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
                                            autopilot.setAutopilotMode("track")
                                            speakMode("TRACK")
                                        }
                                    } else {
                                        selectedModeOverride = null
                                    }
                                    true
                                },
                                nightMode,
                            )
                        }
                        R.id.btn_mode_stop -> {
                            selectedModeOverride = "STANDBY"
                            autopilot.stopNavigation()
                            speakMode("STANDBY")
                        }
                    }
                    listener?.invoke(engine.getCurrentState() ?: MarineState())
                }

                val currentMode = engine.getCurrentState()?.autopilotState?.uppercase(Locale.US) ?: "STANDBY"
                if ((currentMode == "STANDBY") && (checkedId != R.id.btn_mode_stop)) {
                    val targetMode = when (checkedId) {
                        R.id.btn_mode_compass -> "AUTO"
                        R.id.btn_mode_wind -> "WIND"
                        R.id.btn_mode_route -> "ROUTE"
                        else -> ""
                    }
                    showConfirmModeChange(targetMode) { action() }
                } else {
                    action()
                }
            }
        }

        arcView.onCenterClicked = {
            val state = NauticalPlugin.engine?.getCurrentState()
            state?.headingTrue?.let { actualH ->
                val actualDeg = Math.toDegrees(actualH)
                autopilot.setTargetHeading(actualDeg)
                speakHeading(actualDeg.toInt())
                view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            }
        }

        arcView.onHeadingChanged = { heading ->
            val currentState = NauticalPlugin.engine?.getCurrentState()
            if (currentState?.autopilotState?.uppercase(Locale.US) == "WIND") {
                // In WIND mode, we set target wind angle.
                // Assuming drag on compass relative to boat means user wants to sail at that angle.
                // But usually, drag on compass means setting a compass heading.
                // If in WIND mode, maybe we should ignore or convert?
                // For now, only set heading in AUTO mode.
                autopilot.setTargetHeading(heading.toDouble())
                speakHeading(heading)
            } else {
                autopilot.setTargetHeading(heading.toDouble())
                speakHeading(heading)
            }
        }

        arcView.onWindAngleChanged = { angle ->
            autopilot.setTargetWindAngle(angle.toDouble())
            speakHeading(angle) // Reuse speakHeading for now
        }

        seaStateSlider.value = osmandSettings.NAUTICAL_PILOT_SEA_STATE.get().toFloat()
        seaStateSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val level = value.toInt()
                osmandSettings.NAUTICAL_PILOT_SEA_STATE.set(level)
                autopilot.setSeaState(level)
                view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
            }
        }

        autoSeaStateSwitch.setOnCheckedChangeListener { _, isChecked ->
            engine.setAutoSeaStateEnabled(isChecked)
            view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
        }

        minus1Btn.setOnClickListener {
            resetAutoDismissTimer()
            val state = NauticalPlugin.engine?.getCurrentState()
            if (state?.autopilotState?.uppercase(Locale.US) == "WIND") {
                if (isArmedPort) {
                    val isProa = osmandSettings.NAUTICAL_VESSEL_TYPE.get() == VesselType.PROA
                    if (isProa) {
                        showConfirmManeuver(tacking = true, isShunt = true) { 
                            autopilot.shunt()
                        }
                    } else {
                        val upwind = state.windDirectionApparent?.let { awa -> kotlin.math.abs(Math.toDegrees(awa)) < 90.0 } ?: true
                        if (upwind) {
                            showConfirmManeuver(tacking = true) { 
                                autopilot.tack(port = true)
                                speakManeuver(tacking = true, port = true)
                            }
                        } else {
                            showConfirmManeuver(tacking = false) { 
                                autopilot.gybe(port = true)
                                speakManeuver(tacking = false, port = true)
                            }
                        }
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
                if (isArmedStbd) {
                    val isProa = osmandSettings.NAUTICAL_VESSEL_TYPE.get() == VesselType.PROA
                    if (isProa) {
                        showConfirmManeuver(tacking = true, isShunt = true) { 
                            autopilot.shunt()
                        }
                    } else {
                        val upwind = state.windDirectionApparent?.let { awa -> kotlin.math.abs(Math.toDegrees(awa)) < 90.0 } ?: true
                        if (upwind) {
                            showConfirmManeuver(tacking = true) { 
                                autopilot.tack(port = false)
                                speakManeuver(tacking = true, port = false)
                            }
                        } else {
                            showConfirmManeuver(tacking = false) { 
                                autopilot.gybe(port = false)
                                speakManeuver(tacking = false, port = false)
                            }
                        }
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

    private fun updateTelemetryGrid(
        state: MarineState,
        mode: String,
        l11: TextView, v11: TextView, i11: ImageView,
        l12: TextView, v12: TextView, i12: ImageView,
        l13: TextView, v13: TextView, i13: ImageView,
        l21: TextView, v21: TextView, i21: ImageView,
        l22: TextView, v22: TextView, i22: ImageView,
        l23: TextView, v23: TextView, i23: ImageView,
        sogTrend: String,
        stwTrend: String,
    ) {
        val knots = getString(R.string.nautical_unit_knots)
        val nm = getString(R.string.nautical_unit_nm)
        val na = getString(R.string.n_a)
        val knotsCoeff = net.osmand.shared.units.SpeedConstants.KNOTS
        
        when (mode) {
            "WIND" -> {
                l11.text = getString(R.string.nautical_tws)
                v11.text = String.format(Locale.US, "%.1f %s", (state.windSpeedTrue ?: 0.0) * knotsCoeff, knots)
                i11.setImageResource(R.drawable.widget_weather_wind_day)
                
                val awaDeg = state.windDirectionApparent?.let { Math.toDegrees(it).toInt() } ?: 0
                val targetAwaDeg = state.targetWindAngleApparent?.let { Math.toDegrees(it).toInt() } ?: 0
                var windErr = (awaDeg - targetAwaDeg).toFloat()
                while (windErr > 180) windErr -= 360
                while (windErr < -180) windErr += 360
                
                l12.text = getString(R.string.nautical_wind_err)
                v12.text = String.format(Locale.US, "%.1f°", windErr)
                i12.setImageResource(R.drawable.ic_action_relative_bearing)
                l13.text = getString(R.string.nautical_target_twa)
                v13.text = String.format(Locale.US, "%d°", targetAwaDeg)
                i13.setImageResource(R.drawable.ic_action_target_direction_on)
                
                l21.text = getString(R.string.nautical_stw)
                v21.text = String.format(Locale.US, "%.1f%s %s", (state.speedThroughWater ?: 0.0) * knotsCoeff, stwTrend, knots)
                i21.setImageResource(R.drawable.ic_action_sensor_speed_outlined)
                l22.text = getString(R.string.nautical_twa)
                v22.text = String.format(Locale.US, "%.0f°", Math.toDegrees(state.trueWindAngle ?: 0.0))
                i22.setImageResource(R.drawable.widget_weather_wind_day)
                l23.text = getString(R.string.nautical_polar_target)
                v23.text = String.format(Locale.US, "%.1f %s", (state.polarTargetSpeed ?: 0.0) * knotsCoeff, knots)
                i23.setImageResource(R.drawable.ic_action_vmg)
            }
            "TRACK", "ROUTE" -> {
                l11.text = getString(R.string.nautical_sog)
                v11.text = String.format(Locale.US, "%.1f%s %s", (state.speedOverGround ?: 0.0) * knotsCoeff, sogTrend, knots)
                i11.setImageResource(R.drawable.ic_action_speed)
                l12.text = getString(R.string.nautical_xte)
                v12.text = String.format(Locale.US, "%.3f %s", (state.crossTrackError ?: 0.0) / 1852.0, nm)
                i12.setImageResource(R.drawable.ic_action_nautical_xte)
                l13.text = getString(R.string.nautical_btw)
                v13.text = String.format(Locale.US, "%.0f°", Math.toDegrees(state.targetHeading ?: 0.0))
                i13.setImageResource(R.drawable.ic_action_bearing)
                
                val dtw = state.distanceToWaypoint
                l21.text = getString(R.string.nautical_dtw)
                v21.text = if (dtw != null) String.format(Locale.US, "%.2f %s", dtw / 1852.0, nm) else na
                i21.setImageResource(R.drawable.ic_action_distance)
                l22.text = getString(R.string.nautical_cog)
                v22.text = String.format(Locale.US, "%.0f°", Math.toDegrees(state.courseOverGroundTrue ?: 0.0))
                i22.setImageResource(R.drawable.ic_action_cog)
                
                val ttw = state.timeToWaypoint
                l23.text = getString(R.string.nautical_ttw)
                if (ttw != null) {
                    val h = (ttw / 3600).toInt()
                    val m = ((ttw % 3600) / 60).toInt()
                    v23.text = String.format(Locale.US, "%02d:%02d %s", h, m, getString(R.string.nautical_unit_hour_short))
                } else {
                    v23.text = na
                }
                i23.setImageResource(R.drawable.widget_time_day)
            }
            else -> {
                l11.text = getString(R.string.nautical_sog)
                v11.text = String.format(Locale.US, "%.1f%s %s", (state.speedOverGround ?: 0.0) * knotsCoeff, sogTrend, knots)
                i11.setImageResource(R.drawable.ic_action_speed)
                
                val hdgErr = arcView.calculateError(arcView.actualHeading ?: 0, arcView.targetHeading)
                l12.text = getString(R.string.nautical_hdg_err)
                v12.text = String.format(Locale.US, "%.1f°", hdgErr)
                i12.setImageResource(R.drawable.ic_action_relative_bearing)
                l13.text = getString(R.string.nautical_target_heading)
                v13.text = String.format(Locale.US, "%d°", state.targetHeading?.let { Math.toDegrees(it).toInt() } ?: 0)
                i13.setImageResource(R.drawable.ic_action_target_direction_on)
                
                l21.text = getString(R.string.nautical_stw)
                v21.text = String.format(Locale.US, "%.1f%s %s", (state.speedThroughWater ?: 0.0) * knotsCoeff, stwTrend, knots)
                i21.setImageResource(R.drawable.ic_action_sensor_speed_outlined)
                l22.text = getString(R.string.nautical_set_drift)
                v22.text = String.format(Locale.US, "%03.0f°/%.1f", Math.toDegrees(state.setTrue ?: 0.0), (state.drift ?: 0.0) * knotsCoeff)
                i22.setImageResource(R.drawable.ic_action_bearing)
                l23.text = getString(R.string.nautical_rot)
                v23.text = String.format(Locale.US, "%.1f %s", Math.toDegrees(state.rateOfTurn ?: 0.0) * 60.0, getString(R.string.nautical_unit_rot_short))
                i23.setImageResource(R.drawable.ic_action_nautical_rot)
            }
        }
    }

    private fun showConfirmManeuver(tacking: Boolean, isShunt: Boolean = false, onConfirm: () -> Unit) {
        val autopilot = NauticalPlugin.autopilot ?: return
        val isSafe = if (isShunt) true else autopilot.isWindSafeForManeuver(tacking)
        val title = if (isShunt) R.string.nautical_shunt else if (tacking) R.string.nautical_tack else R.string.nautical_gybe
        val msg = if (isShunt) R.string.nautical_confirm_shunt else if (isSafe) R.string.nautical_confirm_maneuver else R.string.nautical_warn_unsafe_maneuver

        androidx.appcompat.app.AlertDialog.Builder(requireContext(), if (nightMode) R.style.OsmandDarkTheme else R.style.OsmandLightTheme)
            .setTitle(title)
            .setMessage(msg)
            .setPositiveButton(R.string.shared_string_yes) { _, _ -> onConfirm() }
            .setNegativeButton(R.string.shared_string_cancel, null)
            .show()
    }

    private fun showConfirmModeChange(mode: String, onConfirm: () -> Unit) {
        val msg = getString(R.string.nautical_confirm_mode_change, mode)
        androidx.appcompat.app.AlertDialog.Builder(requireContext(), if (nightMode) R.style.OsmandDarkTheme else R.style.OsmandLightTheme)
            .setTitle(R.string.nautical_autopilot)
            .setMessage(msg)
            .setPositiveButton(R.string.shared_string_yes) { _, _ -> onConfirm() }
            .setNegativeButton(R.string.shared_string_cancel, null)
            .show()
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
