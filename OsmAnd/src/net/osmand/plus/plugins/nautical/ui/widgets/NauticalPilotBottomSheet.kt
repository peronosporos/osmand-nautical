package net.osmand.plus.plugins.nautical.ui.widgets

import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import net.osmand.plus.R
import net.osmand.plus.base.bottomsheetmenu.BaseBottomSheetItem
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
class NauticalPilotBottomSheet : BaseNauticalBottomSheet() {

    private var isArmedPort = false
    private var isArmedStbd = false
    private val armHandler = Handler(Looper.getMainLooper())
    private val resetArmRunnable = Runnable {
        isArmedPort = false
        isArmedStbd = false
        refreshTacticalButtons()
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
    private lateinit var modeToggleGroup: MaterialButtonToggleGroup
    private lateinit var lockBtn: MaterialButton
    private lateinit var rudderView: RudderView
    private lateinit var predictiveActiveImg: ImageView
    private lateinit var minus1Btn: MaterialButton
    private lateinit var plus1Btn: MaterialButton
    private lateinit var minus10Btn: MaterialButton
    private lateinit var plus10Btn: MaterialButton
    private lateinit var stopBtn: MaterialButton
    private lateinit var windBtn: MaterialButton
    private lateinit var routeBtn: MaterialButton
    private var isCourseLocked = false

    companion object {
        @JvmStatic
        fun newInstance(): NauticalPilotBottomSheet {
            return NauticalPilotBottomSheet()
        }
    }

    override fun createMenuItems(savedInstanceState: Bundle?) {
        val engine = NauticalPlugin.engine
        val autopilot = NauticalPlugin.autopilot
        val plugin = NauticalPlugin.getInstance()

        if ((engine == null) || (autopilot == null)) {
            dismissAllowingStateLoss()
            return
        }

        val customView = LayoutInflater.from(requireContext()).inflate(R.layout.nautical_pilot_bottom_sheet, null)

        errorLinear = customView.findViewById(R.id.heading_error_linear)
        arcView = customView.findViewById(R.id.heading_arc_view)
        steeringCard = customView.findViewById(R.id.steering_card)
        modeToggleGroup = customView.findViewById(R.id.mode_toggle_group)
        lockBtn = customView.findViewById(R.id.btn_lock_unlock)
        rudderView = customView.findViewById(R.id.rudder_view)
        predictiveActiveImg = customView.findViewById(R.id.img_predictive_active)
        minus1Btn = customView.findViewById(R.id.btn_minus_1)
        plus1Btn = customView.findViewById(R.id.btn_plus_1)
        
        customView.findViewById<View>(R.id.btn_settings_gear).setOnClickListener {
            net.osmand.plus.settings.fragments.BaseSettingsFragment.showInstance(
                requireActivity(),
                net.osmand.plus.settings.fragments.SettingsScreenType.NAUTICAL_SETTINGS
            )
        }
        customView.findViewById<View>(R.id.btn_settings_gear).setOnLongClickListener {
            net.osmand.plus.settings.fragments.BaseSettingsFragment.showInstance(
                requireActivity(),
                net.osmand.plus.settings.fragments.SettingsScreenType.NAUTICAL_ADVANCED_SETTINGS
            )
            true
        }

        predictiveActiveImg.setOnClickListener {
            net.osmand.plus.settings.fragments.BaseSettingsFragment.showInstance(requireActivity(), net.osmand.plus.settings.fragments.SettingsScreenType.NAUTICAL_ADVANCED_SETTINGS)
        }

        errorLinear.setOnClickListener {
            val state = NauticalPlugin.engine?.getCurrentState()
            state?.headingTrue?.let { hdg ->
                NauticalPlugin.autopilot?.setTargetHeading(Math.toDegrees(hdg))
                app.showToastMessage(R.string.nautical_course_reset)
            }
        }

        minus10Btn = customView.findViewById(R.id.btn_minus_10)
        plus10Btn = customView.findViewById(R.id.btn_plus_10)
        stopBtn = customView.findViewById(R.id.btn_mode_stop)
        windBtn = customView.findViewById(R.id.btn_mode_wind)
        routeBtn = customView.findViewById(R.id.btn_mode_route)

        val advancedBtn = customView.findViewById<View>(R.id.btn_advanced)
        val maneuversBtn = customView.findViewById<View>(R.id.btn_maneuvers)
        val toolCenterBtn = customView.findViewById<View>(R.id.btn_tool_center)
        val switchesBtn = customView.findViewById<View>(R.id.btn_switches)
        val systemsBtn = customView.findViewById<View>(R.id.btn_systems)

        isCourseLocked = app.settings.NAUTICAL_LOCK_TOUCH_DURING_MANEUVERS.get()
        updateLockButton()

        lockBtn.setOnClickListener {
            isCourseLocked = !isCourseLocked
            updateLockButton()
            customView.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
        }

        routeBtn.setOnLongClickListener {
            showPatternsDialog()
            true
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    NauticalPlugin.engine?.dataBroker?.visualState
                        ?.collectLatest { state ->
                            updateUi(state)
                        }
                }
            }
        }

        stopBtn.setOnClickListener {
            autopilot.stopNavigation()
            speakMode("STANDBY")
            syncUiWithState()
        }
        stopBtn.setOnLongClickListener {
            autopilot.stopNavigation()
            speakMode("STANDBY")
            it.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            syncUiWithState()
            true
        }

        modeToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btn_mode_compass -> {
                        autopilot.setAutopilotMode("auto")
                        speakMode("AUTO")
                    }
                    R.id.btn_mode_wind -> {
                        autopilot.setAutopilotMode("wind")
                        speakMode("WIND")
                    }
                    R.id.btn_mode_route -> {
                        if (engine.isFollowingRoute) {
                            autopilot.setAutopilotMode("track")
                            speakMode("TRACK")
                        } else {
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
                    }
                }
                syncUiWithState()
            }
        }

        arcView.onCenterClicked = {
            val state = NauticalPlugin.engine?.getCurrentState()
            state?.let { s ->
                val reference = app.settings.NAUTICAL_HEADING_REFERENCE.get()
                val actualDeg = if (reference == net.osmand.plus.settings.enums.HeadingReference.MAGNETIC) {
                    s.headingMagnetic?.let { h ->
                        val deg = Math.toDegrees(h)
                        autopilot.setTargetHeading(deg)
                        NauticalPlugin.engine?.setAutopilotHeadingMagnetic(h)
                        deg
                    }
                } else {
                    s.headingTrue?.let { h ->
                        val deg = Math.toDegrees(h)
                        autopilot.setTargetHeading(deg)
                        NauticalPlugin.engine?.setAutopilotHeading(h)
                        deg
                    }
                }
                actualDeg?.let { plugin?.speakHeading(it.toInt()) }
                customView.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            }
        }

        minus1Btn.setOnClickListener { handleNudge(-1.0, it) }
        plus1Btn.setOnClickListener { handleNudge(1.0, it) }
        
        minus1Btn.setOnLongClickListener { 
            val state = NauticalPlugin.engine?.getCurrentState()
            if (state?.autopilotState?.uppercase(Locale.US) == "WIND") {
                armManeuver(port = true)
                true
            } else false
        }
        plus1Btn.setOnLongClickListener {
            val state = NauticalPlugin.engine?.getCurrentState()
            if (state?.autopilotState?.uppercase(Locale.US) == "WIND") {
                armManeuver(port = false)
                true
            } else false
        }

        minus10Btn.setOnClickListener {
            autopilot.adjustHeading(-10.0)
            it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
        }
        plus10Btn.setOnClickListener {
            autopilot.adjustHeading(10.0)
            it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
        }

        maneuversBtn.setOnClickListener {
            NauticalManeuversBottomSheet.show(parentFragmentManager)
        }

        toolCenterBtn?.setOnClickListener {
             net.osmand.plus.plugins.nautical.ui.NauticalToolCenterDialog.show(parentFragmentManager)
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

        // Apply Touch Guard to HeadingArcView with explicit lock check
        NauticalTouchGuard.apply(arcView, isLockedCheck = { isCourseLocked })

        items.add(BaseBottomSheetItem.Builder().setCustomView(customView).create())
    }

    private fun syncUiWithState() {
        refreshTacticalButtons()
    }

    private fun refreshTacticalButtons() {
        val state = NauticalPlugin.engine?.getCurrentState()
        val isProa = (settings.NAUTICAL_VESSEL_TYPE.get() == VesselType.PROA)
        
        val upwind = state?.windDirectionApparent?.let { awa ->
            val deg = Math.toDegrees(awa)
            val threshold = if (isArmedPort || isArmedStbd) 100.0 else 80.0
            kotlin.math.abs(deg) < threshold
        } ?: true

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

    private fun updateLockButton() {
        if (isCourseLocked) {
            lockBtn.setIconResource(R.drawable.ic_action_lock)
            lockBtn.alpha = 1.0f
        } else {
            lockBtn.setIconResource(R.drawable.ic_action_lock_open)
            lockBtn.alpha = 0.5f
        }
        arcView.alpha = if (isCourseLocked) 0.5f else 1.0f
    }

    private fun handleNudge(delta: Double, view: View) {
        val state = NauticalPlugin.engine?.getCurrentState()
        if (state?.autopilotState?.uppercase(Locale.US) == "WIND") {
            val isPort = view.id == R.id.btn_minus_1
            if (isArmedPort && isPort) {
                executeArmedManeuver(state, port = true)
            } else if (isArmedStbd && !isPort) {
                executeArmedManeuver(state, port = false)
            } else {
                NauticalPlugin.autopilot?.adjustHeading(delta)
            }
        } else {
            NauticalPlugin.autopilot?.adjustHeading(delta)
        }
        view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private fun armManeuver(port: Boolean) {
        isArmedPort = port
        isArmedStbd = !port
        armHandler.removeCallbacks(resetArmRunnable)
        armHandler.postDelayed(resetArmRunnable, 3000)
        refreshTacticalButtons()
        view?.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
    }

    private fun executeArmedManeuver(state: MarineState, port: Boolean) {
        val isProa = settings.NAUTICAL_VESSEL_TYPE.get() == VesselType.PROA
        if (isProa) {
            showConfirmManeuver(tacking = true, isShunt = true) { NauticalPlugin.autopilot?.shunt() }
        } else {
            val upwind = state.windDirectionApparent?.let { awa -> kotlin.math.abs(Math.toDegrees(awa)) < 90.0 } ?: true
            showConfirmManeuver(tacking = upwind) {
                if (upwind) NauticalPlugin.autopilot?.tack(port = port) else NauticalPlugin.autopilot?.gybe(port = port)
                speakManeuver(upwind, port = port)
            }
        }
        isArmedPort = false
        isArmedStbd = false
        armHandler.removeCallbacks(resetArmRunnable)
        refreshTacticalButtons()
    }

    private fun showConfirmManeuver(tacking: Boolean, isShunt: Boolean = false, onConfirm: () -> Unit) {
        val labelResId = if (isShunt) R.string.nautical_shunt else if (tacking) R.string.nautical_tack else R.string.nautical_gybe
        val label = getString(labelResId).uppercase(Locale.US)

        val isSafe = if (isShunt) true else NauticalPlugin.autopilot?.isWindSafeForManeuver(tacking) ?: false
        val msgResId = if (isShunt) R.string.nautical_confirm_shunt else if (isSafe) R.string.nautical_confirm_maneuver else R.string.nautical_warn_unsafe_maneuver
        val msg = getString(msgResId)

        NauticalPlugin.hudManager?.get()?.showBanner(msg, 10000L, label, !isSafe, onConfirm)
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
                }
            }
            .show()
    }

    private fun updateUi(state: MarineState) {
        if (!isAdded) return
        syncUiWithState()

        val rawMode = state.autopilotState.uppercase(Locale.US)
        val pendingMode = state.pendingAutopilotState?.uppercase(Locale.US)
        val arbitrator = app.let { net.osmand.plus.plugins.nautical.engine.NauticalHelmArbitrator.getInstance(it) }
        val isLocked = arbitrator.isLockedByEmergency()

        arcView.currentMode = rawMode
        arcView.setNightMode(nightMode)
        errorLinear.setNightMode(nightMode)

        val disabledAlpha = 0.4f

        if (isLocked) {
            modeToggleGroup.isEnabled = false
            modeToggleGroup.alpha = 0.5f
            arcView.isEnabled = false
            arcView.alpha = 0.5f
        } else {
            modeToggleGroup.isEnabled = true
            modeToggleGroup.alpha = 1.0f
            arcView.isEnabled = true
            arcView.alpha = 1.0f
        }

        val targetCheckedId = when (rawMode) {
            "STANDBY" -> R.id.btn_mode_stop
            "AUTO" -> R.id.btn_mode_compass
            "WIND" -> R.id.btn_mode_wind
            "TRACK", "ROUTE" -> R.id.btn_mode_route
            else -> View.NO_ID
        }
        
        if (modeToggleGroup.checkedButtonId != targetCheckedId) {
            modeToggleGroup.check(targetCheckedId)
        }

        val modes = mapOf(
            "STANDBY" to R.id.btn_mode_stop,
            "AUTO" to R.id.btn_mode_compass,
            "WIND" to R.id.btn_mode_wind,
            "TRACK" to R.id.btn_mode_route,
            "ROUTE" to R.id.btn_mode_route,
        )

        for ((modeName, btnId) in modes) {
            val btn = when(btnId) {
                R.id.btn_mode_stop -> stopBtn
                R.id.btn_mode_compass -> view?.findViewById<MaterialButton>(R.id.btn_mode_compass)
                R.id.btn_mode_wind -> windBtn
                R.id.btn_mode_route -> routeBtn
                else -> null
            } ?: continue
            
            val isCurrent = rawMode == modeName
            val isPending = pendingMode == modeName

            if (isCurrent || isPending) {
                btn.strokeColor = ContextCompat.getColorStateList(requireContext(), if (isCurrent) R.color.nautical_status_green else R.color.nautical_status_yellow)
                btn.strokeWidth = net.osmand.plus.utils.AndroidUtils.dpToPx(requireContext(), 2f)
            } else {
                btn.strokeWidth = 0
            }
        }

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
        
        arcView.alpha = if (isLocked || isCourseLocked) 0.8f 
                        else if (state.pendingTargetHeading != null) 0.7f 
                        else 1.0f

        predictiveActiveImg.isVisible = NauticalPlugin.autopilot?.activeWaveBias?.let { Math.abs(it) >= 0.3 } ?: false

        state.windDirectionApparent?.let { arcView.windAngleApparent = Math.toDegrees(it).toInt() }
        state.targetWindAngleApparent?.let { arcView.targetWindAngleApparent = Math.toDegrees(it).toInt() }
        state.rudderAngle?.let { rudderView.setRudderAngle(it) }

        val isStale = state.connectionStatus != ConnectionStatus.CONNECTED
        steeringCard.alpha = if (isStale) 0.5f else 1.0f

        if (state.isOffCourse) {
            errorLinear.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.nautical_status_bg_emergency))
        } else {
            errorLinear.setBackgroundColor(Color.TRANSPARENT)
        }

        if (rawMode == "WIND") {
            refreshTacticalButtons()
        } else {
            val defaultColor = ContextCompat.getColor(requireContext(), if (nightMode) R.color.text_color_primary_dark_v2 else R.color.text_color_primary_light_v2)
            minus1Btn.setTextColor(defaultColor)
            minus1Btn.text = "-1"
            plus1Btn.setTextColor(defaultColor)
            plus1Btn.text = "+1"
        }
    }

    override fun getRightBottomButtonTextId(): Int = DEFAULT_VALUE
    override fun getDismissButtonTextId(): Int = DEFAULT_VALUE

    override fun onDestroyView() {
        super.onDestroyView()
        armHandler.removeCallbacks(resetArmRunnable)
    }
}
