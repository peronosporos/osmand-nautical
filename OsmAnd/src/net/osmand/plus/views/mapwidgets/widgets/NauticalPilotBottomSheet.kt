package net.osmand.plus.views.mapwidgets.widgets

import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewStub
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import net.osmand.plus.R
import net.osmand.plus.base.BaseMaterialBottomSheetDialogFragment
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.ConnectionStatus
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.plugins.nautical.ui.HeadingArcView
import net.osmand.plus.plugins.nautical.ui.HeadingErrorLinearView
import net.osmand.plus.plugins.nautical.ui.NauticalTouchGuard
import net.osmand.plus.plugins.nautical.ui.RudderView
import net.osmand.plus.settings.enums.VesselType
import net.osmand.plus.track.GpxDialogs
import java.util.Locale

@OptIn(kotlinx.coroutines.FlowPreview::class)
class NauticalPilotBottomSheet : BaseMaterialBottomSheetDialogFragment() {

    private var isArmedPort = false
    private var isArmedStbd = false
    private val armHandler = Handler(Looper.getMainLooper())
    private val resetArmRunnable = Runnable {
        isArmedPort = false
        isArmedStbd = false
        updateTackButtons()
    }
    private var autoDismissHandler = Handler(Looper.getMainLooper())
    private val autoDismissRunnable = Runnable {
        if (isAdded) {
            dismissAllowingStateLoss()
        }
    }
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

    private lateinit var errorLinear: HeadingErrorLinearView
    private lateinit var arcView: HeadingArcView
    private lateinit var steeringCard: View
    private lateinit var authWarning: TextView
    private lateinit var modeToggleGroup: MaterialButtonToggleGroup

    // Telemetry View Cache (Nullable until inflated)
    private var telemetryPane: View? = null
    private var l11: TextView? = null
    private var v11: TextView? = null
    private var i11: ImageView? = null
    private var l12: TextView? = null
    private var v12: TextView? = null
    private var i12: ImageView? = null
    private var l13: TextView? = null
    private var v13: TextView? = null
    private var i13: ImageView? = null
    private var l21: TextView? = null
    private var v21: TextView? = null
    private var i21: ImageView? = null
    private var l22: TextView? = null
    private var v22: TextView? = null
    private var i22: ImageView? = null
    private var l23: TextView? = null
    private var v23: TextView? = null
    private var i23: ImageView? = null

    private fun inflateTelemetry(root: View) {
        if (telemetryPane != null) return
        val stub = root.findViewById<ViewStub>(R.id.telemetry_stub)
        telemetryPane = stub.inflate()
        
        telemetryPane?.let { pane ->
            l11 = pane.findViewById(R.id.txt_label_1_1)
            v11 = pane.findViewById(R.id.txt_value_1_1)
            i11 = pane.findViewById(R.id.img_icon_1_1)
            l12 = pane.findViewById(R.id.txt_label_1_2)
            v12 = pane.findViewById(R.id.txt_value_1_2)
            i12 = pane.findViewById(R.id.img_icon_1_2)
            l13 = pane.findViewById(R.id.txt_label_1_3)
            v13 = pane.findViewById(R.id.txt_value_1_3)
            i13 = pane.findViewById(R.id.img_icon_1_3)

            l21 = pane.findViewById(R.id.txt_label_2_1)
            v21 = pane.findViewById(R.id.txt_value_2_1)
            i21 = pane.findViewById(R.id.img_icon_2_1)
            l22 = pane.findViewById(R.id.txt_label_2_2)
            v22 = pane.findViewById(R.id.txt_value_2_2)
            i22 = pane.findViewById(R.id.img_icon_2_2)
            l23 = pane.findViewById(R.id.txt_label_2_3)
            v23 = pane.findViewById(R.id.txt_value_2_3)
            i23 = pane.findViewById(R.id.img_icon_2_3)
        }
    }

    private fun checkAuthToken(): Boolean {
        val engine = NauticalPlugin.engine
        if ((engine == null) || !(engine.isAuthenticated())) {
            val app = activity?.application as? net.osmand.plus.OsmandApplication
            app?.showToastMessage(R.string.nautical_auth_token_required)
            return false
        }
        return true
    }

    companion object {
        @JvmStatic
        fun newInstance(): NauticalPilotBottomSheet {
            return NauticalPilotBottomSheet()
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setBackgroundDrawableResource(android.R.color.transparent)
        }
        (dialog as? com.google.android.material.bottomsheet.BottomSheetDialog)?.let { sheetDialog ->
            sheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.let { bottomSheet ->
                val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheet)
                val metrics = resources.displayMetrics
                val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                val screenHeightDp = metrics.heightPixels / metrics.density

                if (isLandscape || (screenHeightDp < 600)) {
                    behavior.peekHeight = (metrics.heightPixels * 0.7).toInt()
                    behavior.maxHeight = metrics.heightPixels
                } else {
                    behavior.maxHeight = (metrics.heightPixels * 0.6).toInt()
                    behavior.peekHeight = (metrics.heightPixels * 0.4).toInt()
                }
                behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED
            }
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
        val isProa = (osmandSettings.NAUTICAL_VESSEL_TYPE.get() == VesselType.PROA)
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
        return inflater.inflate(R.layout.nautical_pilot_bottom_sheet, container, false)
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
        authWarning = view.findViewById(R.id.auth_warning)
        modeToggleGroup = view.findViewById(R.id.mode_toggle_group)

        val advancedBtn = view.findViewById<View>(R.id.btn_advanced)
        val maneuversBtn = view.findViewById<View>(R.id.btn_maneuvers)
        val switchesBtn = view.findViewById<View>(R.id.btn_switches)
        val systemsBtn = view.findViewById<View>(R.id.btn_systems)

        val minus1Btn = view.findViewById<MaterialButton>(R.id.btn_minus_1)
        val plus1Btn = view.findViewById<MaterialButton>(R.id.btn_plus_1)
        val minus10Btn = view.findViewById<MaterialButton>(R.id.btn_minus_10)
        val plus10Btn = view.findViewById<MaterialButton>(R.id.btn_plus_10)

        val routeBtn = view.findViewById<View>(R.id.btn_mode_route)
        routeBtn.setOnLongClickListener {
            if (checkAuthToken()) {
                showPatternsDialog()
            }
            true
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    NauticalPlugin.engine?.dataBroker?.visualState
                        ?.collectLatest { state ->
                            updateUi(view, state)
                        }
                }
                launch {
                    NauticalPlugin.autopilotManager?.targetHeadingMag?.collectLatest { magHdg ->
                        magHdg?.let {
                            android.util.Log.d("PilotSheet", "Physical Autopilot Target (Mag): ${Math.toDegrees(it)}")
                        }
                    }
                }
            }
        }

        val stopBtn = view.findViewById<MaterialButton>(R.id.btn_mode_stop)
        stopBtn.setOnLongClickListener {
            if (checkAuthToken()) {
                autopilot.stopNavigation()
                speakMode("STANDBY")
                it.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                resetAutoDismissTimer()
            }
            true
        }

        modeToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                resetAutoDismissTimer()
                if (checkedId == R.id.btn_mode_stop) {
                    // Block immediate stop - require long press
                    val state = engine.getCurrentState()
                    if (state.autopilotState.uppercase(Locale.US) != "STANDBY") {
                        // Re-check the previous button
                        val prevBtnId = when (state.autopilotState.uppercase(Locale.US)) {
                            "AUTO" -> R.id.btn_mode_compass
                            "WIND" -> R.id.btn_mode_wind
                            "TRACK", "ROUTE" -> R.id.btn_mode_route
                            else -> -1
                        }
                        if (prevBtnId != -1) {
                            modeToggleGroup.check(prevBtnId)
                        } else {
                            modeToggleGroup.clearChecked()
                        }
                        
                        NauticalPlugin.hudManager?.get()?.showBanner(
                            getString(R.string.nautical_confirm_stop_autopilot),
                            3000,
                            isWarning = true,
                        )
                    }
                    return@addOnButtonCheckedListener
                }

                if (!checkAuthToken()) {
                    return@addOnButtonCheckedListener
                }

                when (checkedId) {
                    R.id.btn_mode_compass -> {
                        val currentMode = engine.getCurrentState().autopilotState.uppercase(Locale.US)
                        if (currentMode == "STANDBY") {
                            showConfirmModeChange("AUTO") {
                                autopilot.setAutopilotMode("auto")
                                speakMode("AUTO")
                            }
                        } else {
                            autopilot.setAutopilotMode("auto")
                            speakMode("AUTO")
                        }
                    }
                    R.id.btn_mode_wind -> {
                        val currentMode = engine.getCurrentState().autopilotState.uppercase(Locale.US)
                        if (currentMode == "STANDBY") {
                            showConfirmModeChange("WIND") {
                                autopilot.setAutopilotMode("wind")
                                speakMode("WIND")
                            }
                        } else {
                            autopilot.setAutopilotMode("wind")
                            speakMode("WIND")
                        }
                    }
                    R.id.btn_mode_route -> {
                        val currentMode = engine.getCurrentState().autopilotState.uppercase(Locale.US)
                        val action = {
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
                                    }
                                    true
                                },
                                nightMode,
                            )
                        }
                        if (currentMode == "STANDBY") {
                            showConfirmModeChange("ROUTE") { action() }
                        } else {
                            action()
                        }
                    }
                }
            }
        }

        arcView.onCenterClicked = {
            if (checkAuthToken()) {
                val state = NauticalPlugin.engine?.getCurrentState()
                state?.let { s ->
                    val reference = app.settings.NAUTICAL_HEADING_REFERENCE.get()
                    if (reference == net.osmand.plus.settings.enums.HeadingReference.MAGNETIC) {
                        s.headingMagnetic?.let { actualH ->
                            val actualDeg = Math.toDegrees(actualH)
                            autopilot.setTargetHeading(actualDeg)
                            NauticalPlugin.engine?.setAutopilotHeadingMagnetic(actualH)
                            speakHeading(actualDeg.toInt())
                        }
                    } else {
                        s.headingTrue?.let { actualH ->
                            val actualDeg = Math.toDegrees(actualH)
                            autopilot.setTargetHeading(actualDeg)
                            NauticalPlugin.engine?.setAutopilotHeading(actualH)
                            speakHeading(actualDeg.toInt())
                        }
                    }
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                }
            }
        }

        arcView.onHeadingChanged = { heading ->
            if (checkAuthToken()) {
                val degrees = heading.toDouble()
                autopilot.setTargetHeading(degrees)
                NauticalPlugin.autopilotManager?.setTargetHeading(degrees)
                speakHeading(heading)
            }
        }

        arcView.onWindAngleChanged = { angle ->
            if (checkAuthToken()) {
                val degrees = angle.toDouble()
                autopilot.setTargetWindAngle(degrees)
                NauticalPlugin.autopilotManager?.setTargetWindAngle(degrees)
                speakHeading(angle)
            }
        }

        minus1Btn.setOnClickListener { handleNudge(-1.0, it) }
        plus1Btn.setOnClickListener { handleNudge(1.0, it) }

        minus10Btn.setOnClickListener {
            resetAutoDismissTimer()
            if (checkAuthToken()) {
                autopilot.adjustHeading(-10.0)
                it.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            }
        }
        plus10Btn.setOnClickListener {
            resetAutoDismissTimer()
            if (checkAuthToken()) {
                autopilot.adjustHeading(10.0)
                it.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            }
        }

        maneuversBtn.setOnClickListener {
            showManeuversMenu()
        }

        switchesBtn.setOnClickListener {
            NauticalElectricalDashboardBottomSheet.show(parentFragmentManager)
        }

        systemsBtn.setOnClickListener {
            NauticalSystemsBottomSheet.show(parentFragmentManager)
        }

        advancedBtn.setOnClickListener {
            NauticalAdvancedSettingsBottomSheet.newInstance().show(parentFragmentManager, "advanced_settings")
        }

        // Apply Touch Guard to HeadingArcView
        NauticalTouchGuard.apply(arcView)
    }

    private fun handleNudge(delta: Double, view: View) {
        resetAutoDismissTimer()
        if (checkAuthToken()) {
            val state = NauticalPlugin.engine?.getCurrentState()
            if (state?.autopilotState?.uppercase(Locale.US) == "WIND") {
                val btn = view as? MaterialButton
                val isPort = btn?.id == R.id.btn_minus_1
                if (isArmedPort && isPort) {
                    val isProa = osmandSettings.NAUTICAL_VESSEL_TYPE.get() == VesselType.PROA
                    if (isProa) {
                        showConfirmManeuver(tacking = true, isShunt = true) { NauticalPlugin.autopilot?.shunt() }
                    } else {
                        val upwind = state.windDirectionApparent?.let { awa -> kotlin.math.abs(Math.toDegrees(awa)) < 90.0 } ?: true
                        showConfirmManeuver(tacking = upwind) {
                            if (upwind) NauticalPlugin.autopilot?.tack(port = true) else NauticalPlugin.autopilot?.gybe(port = true)
                            speakManeuver(upwind, port = true)
                        }
                    }
                    isArmedPort = false
                    armHandler.removeCallbacks(resetArmRunnable)
                } else if (isArmedStbd && !isPort) {
                    val isProa = osmandSettings.NAUTICAL_VESSEL_TYPE.get() == VesselType.PROA
                    if (isProa) {
                        showConfirmManeuver(tacking = true, isShunt = true) { NauticalPlugin.autopilot?.shunt() }
                    } else {
                        val upwind = state.windDirectionApparent?.let { awa -> kotlin.math.abs(Math.toDegrees(awa)) < 90.0 } ?: true
                        showConfirmManeuver(tacking = upwind) {
                            if (upwind) NauticalPlugin.autopilot?.tack(port = false) else NauticalPlugin.autopilot?.gybe(port = false)
                            speakManeuver(upwind, port = false)
                        }
                    }
                    isArmedStbd = false
                    armHandler.removeCallbacks(resetArmRunnable)
                } else {
                    isArmedPort = isPort
                    isArmedStbd = !isPort
                    armHandler.removeCallbacks(resetArmRunnable)
                    armHandler.postDelayed(resetArmRunnable, 3000)
                }
                updateTackButtons()
            } else {
                NauticalPlugin.autopilot?.adjustHeading(delta)
            }
            view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    private fun getTrendString(current: Double?, last: Double?): String {
        return when {
            ((current == null) || (last == null) || (kotlin.math.abs(current - last) < 0.01)) -> ""
            current > last -> getString(R.string.nautical_trend_up)
            else -> getString(R.string.nautical_trend_down)
        }
    }

    private fun updateTelemetryGrid(
        state: MarineState,
        mode: String,
        sogTrend: String,
        stwTrend: String,
    ) {
        // Ensure telemetry is inflated
        view?.let { inflateTelemetry(it) }
        
        val knots = getString(R.string.nautical_unit_knots)
        val nm = getString(R.string.nautical_unit_nm)
        val na = getString(R.string.n_a)
        val knotsCoeff = net.osmand.shared.units.SpeedConstants.KNOTS
        
        // Safety check for null views if inflation failed
        val l11 = l11 ?: return
        val v11 = v11 ?: return
        val i11 = i11 ?: return
        val l12 = l12 ?: return
        val v12 = v12 ?: return
        val i12 = i12 ?: return
        val l13 = l13 ?: return
        val v13 = v13 ?: return
        val i13 = i13 ?: return
        val l21 = l21 ?: return
        val v21 = v21 ?: return
        val i21 = i21 ?: return
        val l22 = l22 ?: return
        val v22 = v22 ?: return
        val i22 = i22 ?: return
        val l23 = l23 ?: return
        val v23 = v23 ?: return
        val i23 = i23 ?: return

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
                v12.text = if (windErr.isNaN() || windErr.isInfinite()) na else String.format(Locale.US, "%.1f°", windErr)
                i12.setImageResource(R.drawable.ic_action_relative_bearing)
                l13.text = getString(R.string.nautical_target_twa)
                v13.text = if (targetAwaDeg.toFloat().isNaN() || targetAwaDeg.toFloat().isInfinite()) na else String.format(Locale.US, "%d°", targetAwaDeg)
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
        val labelResId = if (isShunt) R.string.nautical_shunt else if (tacking) R.string.nautical_tack else R.string.nautical_gybe
        val label = getString(labelResId).uppercase(Locale.US)

        val isSafe = if (isShunt) true else NauticalPlugin.autopilot?.isWindSafeForManeuver(tacking) ?: false
        val msgResId = if (isShunt) R.string.nautical_confirm_shunt else if (isSafe) R.string.nautical_confirm_maneuver else R.string.nautical_warn_unsafe_maneuver
        val msg = getString(msgResId)

        NauticalPlugin.hudManager?.get()?.showBanner(msg, 10000, label, !isSafe, onConfirm)
    }

    private fun showConfirmModeChange(mode: String, onConfirm: () -> Unit) {
        val msg = getString(R.string.nautical_confirm_mode_change, mode)
        NauticalPlugin.hudManager?.get()?.showBanner(msg, 10000, onConfirm = onConfirm)
    }

    private fun showPatternsDialog() {
        val items = arrayOf<CharSequence>(
            getString(R.string.nautical_expanding_square),
            getString(R.string.nautical_sector_search),
            getString(R.string.nautical_spiral_search),
        )

        androidx.appcompat.app.AlertDialog.Builder(requireContext(), if (nightMode) R.style.OsmandDarkTheme else R.style.OsmandLightTheme)
            .setTitle(R.string.nautical_pattern_menu)
            .setItems(items) { _, which ->
                val state = NauticalPlugin.engine?.getCurrentState() ?: return@setItems
                val lat = state.latitude ?: return@setItems
                val lon = state.longitude ?: return@setItems
                val heading = Math.toDegrees(state.headingTrue ?: 0.0)

                val waypoints = when (which) {
                    0 -> net.osmand.plus.plugins.nautical.engine.PatternSteeringEngine.generateExpandingSquare(lat, lon, 0.25, 4, heading)
                    1 -> net.osmand.plus.plugins.nautical.engine.PatternSteeringEngine.generateSectorSearch(lat, lon, 0.5, heading)
                    2 -> net.osmand.plus.plugins.nautical.engine.PatternSteeringEngine.generateSpiral(lat, lon, 1.0, 0.2)
                    else -> emptyList()
                }

                if (waypoints.isNotEmpty()) {
                    NauticalPlugin.autopilot?.executePattern(waypoints)
                    dismiss()
                }
            }
            .show()
    }

    private fun showManeuversMenu() {
        val state = NauticalPlugin.engine?.getCurrentState()
        val upwind = state?.windDirectionApparent?.let { kotlin.math.abs(Math.toDegrees(it)) < 90.0 } ?: true
        val isProa = (osmandSettings.NAUTICAL_VESSEL_TYPE.get() == VesselType.PROA)

        val options = mutableListOf<Pair<String, String>>()
        if (isProa) {
            options.add("shunting" to getString(R.string.nautical_shunt))
        } else {
            if (upwind) {
                options.add("tacking" to getString(R.string.nautical_tack))
            } else {
                options.add("gybing" to getString(R.string.nautical_gybe))
            }
        }
        
        options.add("anchoring" to getString(R.string.nautical_maneuver_anchoring))
        options.add("weighing_anchor" to getString(R.string.nautical_maneuver_weighing_anchor))
        options.add("docking" to getString(R.string.nautical_maneuver_docking))
        options.add("mooring" to getString(R.string.nautical_maneuver_mooring))
        options.add("med_mooring" to getString(R.string.nautical_maneuver_med_mooring))
        options.add("heaving_to" to getString(R.string.nautical_maneuver_heaving_to))
        options.add("slip_exit" to getString(R.string.nautical_maneuver_slip_exit))
        options.add("man_overboard" to getString(R.string.nautical_mob_label))

        val names = options.map { it.second }.toTypedArray()

        androidx.appcompat.app.AlertDialog.Builder(requireContext(), if (nightMode) R.style.OsmandDarkTheme else R.style.OsmandLightTheme)
            .setTitle(R.string.nautical_maneuver_menu)
            .setItems(names) { _, which ->
                val id = options[which].first
                NauticalPlugin.getInstance()?.maneuverManager?.setActiveManeuver(id)
                dismiss()
            }
            .show()
    }

    private fun updateUi(view: View, state: MarineState) {
        if (!isAdded) return
        resetAutoDismissTimer()

        val engine = NauticalPlugin.engine
        val plugin = NauticalPlugin.getInstance()
        val isAuth = engine?.isAuthenticated() ?: false
        val rawMode = state.autopilotState.uppercase(Locale.US)
        val pendingMode = state.pendingAutopilotState?.uppercase(Locale.US)
        val app = activity?.application as? net.osmand.plus.OsmandApplication
        val arbitrator = app?.let { net.osmand.plus.plugins.nautical.engine.NauticalHelmArbitrator.getInstance(it) }
        val isLocked = arbitrator?.isLockedByEmergency() ?: false

        arcView.currentMode = rawMode
        arcView.setNightMode(nightMode)
        errorLinear.setNightMode(nightMode)

        // Apply Ambient Mode based on plugin state (throttling or battery)
        val isAmbient = plugin?.isThrottlingRedraws == true
        arcView.setAmbientMode(isAmbient)

        val disabledAlpha = 0.4f

        if (isLocked) {
            authWarning.visibility = View.VISIBLE
            authWarning.text = getString(R.string.nautical_helm_locked_by, arbitrator.getActiveManeuver() ?: "")
            authWarning.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_color_negative))
            modeToggleGroup.isEnabled = false
            modeToggleGroup.alpha = 0.5f
            arcView.isEnabled = false
            arcView.alpha = 0.5f
        } else if (!isAuth) {
            authWarning.visibility = View.VISIBLE
            authWarning.setText(R.string.nautical_auth_token_required)
            authWarning.setTextColor(ContextCompat.getColor(requireContext(), R.color.nautical_status_text_emergency))

            for (i in 0 until modeToggleGroup.childCount) {
                val child = modeToggleGroup.getChildAt(i)
                child.isEnabled = child.id == R.id.btn_mode_stop
                child.alpha = if (child.isEnabled) 1.0f else disabledAlpha
            }
            arcView.isEnabled = false
            arcView.alpha = disabledAlpha
        } else {
            authWarning.visibility = View.GONE
            for (i in 0 until modeToggleGroup.childCount) {
                val child = modeToggleGroup.getChildAt(i)
                child.isEnabled = true
                child.alpha = 1.0f
            }
            arcView.isEnabled = true
            arcView.alpha = 1.0f
        }

        // Mode Visual State Arbitration
        val modes = mapOf(
            "STANDBY" to R.id.btn_mode_stop,
            "AUTO" to R.id.btn_mode_compass,
            "WIND" to R.id.btn_mode_wind,
            "TRACK" to R.id.btn_mode_route,
            "ROUTE" to R.id.btn_mode_route,
        )

        modeToggleGroup.clearChecked()
        for ((modeName, btnId) in modes) {
            val btn = view.findViewById<MaterialButton>(btnId) ?: continue
            val isCurrent = rawMode == modeName
            val isPending = pendingMode == modeName

            when {
                isCurrent -> {
                    modeToggleGroup.check(btnId)
                    btn.strokeColor = ContextCompat.getColorStateList(requireContext(), R.color.nautical_status_green)
                    btn.strokeWidth = net.osmand.plus.utils.AndroidUtils.dpToPx(requireContext(), 2f)
                }
                isPending -> {
                    btn.strokeColor = ContextCompat.getColorStateList(requireContext(), R.color.nautical_status_yellow)
                    btn.strokeWidth = net.osmand.plus.utils.AndroidUtils.dpToPx(requireContext(), 2f)
                }
                else -> {
                    btn.strokeWidth = 0
                }
            }
        }

        val windBtn = view.findViewById<View>(R.id.btn_mode_wind)
        val routeBtn = view.findViewById<View>(R.id.btn_mode_route)
        val hasWind = state.windDirectionApparent != null
        val hasRoute = engine?.isFollowingRoute == true

        windBtn.isEnabled = isAuth && hasWind
        windBtn.alpha = if (windBtn.isEnabled) 1.0f else disabledAlpha
        routeBtn.isEnabled = isAuth && hasRoute
        routeBtn.alpha = if (routeBtn.isEnabled) 1.0f else disabledAlpha

        // Telemetry & Error logic
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
        state.rudderAngle?.let { view.findViewById<RudderView>(R.id.rudder_view)?.setRudderAngle(it) }

        val isStale = state.connectionStatus != ConnectionStatus.CONNECTED
        steeringCard.alpha = if (isStale) 0.5f else 1.0f

        val stopBtn = view.findViewById<MaterialButton>(R.id.btn_mode_stop)
        val isFollowingPattern = (engine?.isFollowingRoute == true) // Pattern is a route
        if (isFollowingPattern && (rawMode != "STANDBY")) {
            stopBtn.text = getString(R.string.nautical_abort_pattern)
            stopBtn.setIconResource(R.drawable.ic_action_alert)
        } else {
            stopBtn.text = ""
            stopBtn.setIconResource(R.drawable.ic_action_stop)
        }

        if (state.isOffCourse) {
            errorLinear.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.nautical_status_bg_emergency))
        } else {
            errorLinear.setBackgroundColor(Color.TRANSPARENT)
        }

        val sogTrend = getTrendString(state.speedOverGround, lastSog)
        lastSog = state.speedOverGround
        val stwTrend = getTrendString(state.speedThroughWater, lastStw)
        lastStw = state.speedThroughWater

        updateTelemetryGrid(state, rawMode, sogTrend, stwTrend)

        if (rawMode == "WIND") {
            updateTackButtons()
        } else {
            val defaultColor = ContextCompat.getColor(requireContext(), if (nightMode) R.color.text_color_primary_dark_v2 else R.color.text_color_primary_light_v2)
            view.findViewById<MaterialButton>(R.id.btn_minus_1).setTextColor(defaultColor)
            view.findViewById<MaterialButton>(R.id.btn_minus_1).text = "-1"
            view.findViewById<MaterialButton>(R.id.btn_plus_1).setTextColor(defaultColor)
            view.findViewById<MaterialButton>(R.id.btn_plus_1).text = "+1"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        autoDismissHandler.removeCallbacks(autoDismissRunnable)
        armHandler.removeCallbacks(resetArmRunnable)
    }
}
