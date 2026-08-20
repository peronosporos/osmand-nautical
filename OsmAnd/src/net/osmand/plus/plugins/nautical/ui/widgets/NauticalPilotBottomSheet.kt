package net.osmand.plus.plugins.nautical.ui.widgets

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
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
import kotlin.math.abs

class NauticalPilotBottomSheet : BaseNauticalBottomSheet() {

    private lateinit var errorLinear: HeadingErrorLinearView
    private lateinit var arcView: HeadingArcView
    private lateinit var steeringCard: View
    private lateinit var modeToggleGroup: MaterialButtonToggleGroup
    private lateinit var badgePilotMode: TextView
    private lateinit var lockBtn: MaterialButton
    private lateinit var rudderView: RudderView
    private lateinit var predictiveActiveImg: ImageView
    private lateinit var minus1Btn: MaterialButton
    private lateinit var plus1Btn: MaterialButton
    private lateinit var minus10Btn: MaterialButton
    private lateinit var plus10Btn: MaterialButton
    private lateinit var stopBtn: MaterialButton
    private lateinit var compassBtn: MaterialButton
    private lateinit var windBtn: MaterialButton
    private lateinit var routeBtn: MaterialButton
    private lateinit var tackPortBtn: MaterialButton
    private lateinit var tackStbdBtn: MaterialButton

    private var isCourseLocked = false

    companion object {
        @JvmStatic
        fun newInstance(): NauticalPilotBottomSheet {
            return NauticalPilotBottomSheet()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = true
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)
        return dialog
    }

    override fun onStart() {
        super.onStart()
        dialog?.setCancelable(true)
        dialog?.setCanceledOnTouchOutside(true)
    }

    override fun createMenuItems(savedInstanceState: Bundle?) {
        val engine = NauticalPlugin.engine
        val autopilot = NauticalPlugin.autopilot
        val plugin = NauticalPlugin.getInstance()

        if (engine == null || autopilot == null) {
            dismissAllowingStateLoss()
            return
        }

        val themedContext = net.osmand.plus.utils.UiUtilities.getThemedContext(requireContext(), nightMode)
        val customView = LayoutInflater.from(themedContext).inflate(R.layout.nautical_pilot_bottom_sheet, null)

        badgePilotMode = customView.findViewById(R.id.badge_pilot_mode)
        errorLinear = customView.findViewById(R.id.heading_error_linear)
        arcView = customView.findViewById(R.id.heading_arc_view)
        steeringCard = customView.findViewById(R.id.steering_card)
        modeToggleGroup = customView.findViewById(R.id.mode_toggle_group)
        lockBtn = customView.findViewById(R.id.btn_lock_unlock)
        rudderView = customView.findViewById(R.id.rudder_view)
        predictiveActiveImg = customView.findViewById(R.id.img_predictive_active)

        minus10Btn = customView.findViewById(R.id.btn_minus_10)
        minus1Btn = customView.findViewById(R.id.btn_minus_1)
        plus1Btn = customView.findViewById(R.id.btn_plus_1)
        plus10Btn = customView.findViewById(R.id.btn_plus_10)

        stopBtn = customView.findViewById(R.id.btn_mode_stop)
        compassBtn = customView.findViewById(R.id.btn_mode_compass)
        windBtn = customView.findViewById(R.id.btn_mode_wind)
        routeBtn = customView.findViewById(R.id.btn_mode_route)

        tackPortBtn = customView.findViewById(R.id.btn_tack_port)
        tackStbdBtn = customView.findViewById(R.id.btn_tack_stbd)

        // Settings gear button
        customView.findViewById<View>(R.id.btn_settings_gear)?.setOnClickListener {
            NauticalAdvancedSettingsBottomSheet.newInstance().show(parentFragmentManager, "advanced_settings")
        }

        // Lock course touch guard
        isCourseLocked = app.settings.NAUTICAL_LOCK_TOUCH_DURING_MANEUVERS.get()
        updateLockButton()

        lockBtn.setOnClickListener {
            isCourseLocked = !isCourseLocked
            updateLockButton()
            customView.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
        }

        // Mode Switcher Listeners
        stopBtn.setOnClickListener {
            autopilot.stopNavigation()
            speakMode("STANDBY")
            customView.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            syncUiWithState()
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

        // Tactile Stepped Nudge Buttons
        minus10Btn.setOnClickListener {
            autopilot.adjustHeading(-10.0)
            it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
        }
        minus1Btn.setOnClickListener {
            autopilot.adjustHeading(-1.0)
            it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
        }
        plus1Btn.setOnClickListener {
            autopilot.adjustHeading(1.0)
            it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
        }
        plus10Btn.setOnClickListener {
            autopilot.adjustHeading(10.0)
            it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
        }

        // Tack / Jibe Quick Actions
        tackPortBtn.setOnClickListener {
            val state = NauticalPlugin.engine?.getCurrentState()
            if (state != null) {
                executeArmedManeuver(state, port = true)
            }
        }
        tackStbdBtn.setOnClickListener {
            val state = NauticalPlugin.engine?.getCurrentState()
            if (state != null) {
                executeArmedManeuver(state, port = false)
            }
        }

        // Center Click: Set current vessel heading as target
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

        // Reset course deviation error on linear scale click
        errorLinear.setOnClickListener {
            val state = NauticalPlugin.engine?.getCurrentState()
            state?.headingTrue?.let { hdg ->
                NauticalPlugin.autopilot?.setTargetHeading(Math.toDegrees(hdg))
                app.showToastMessage(R.string.nautical_course_reset)
            }
        }

        // Apply touch guard to arc view
        NauticalTouchGuard.apply(arcView, isLockedCheck = { isCourseLocked })

        // Bind StateFlow
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                NauticalPlugin.engine?.dataBroker?.visualState
                    ?.collectLatest { state ->
                        updateUi(state)
                    }
            }
        }

        items.add(BaseBottomSheetItem.Builder().setCustomView(customView).create())
    }

    private fun syncUiWithState() {
        val state = NauticalPlugin.engine?.getCurrentState()
        if (state != null) {
            updateUi(state)
        }
    }

    private fun updateLockButton() {
        if (isCourseLocked) {
            lockBtn.setIconResource(R.drawable.ic_action_lock)
            lockBtn.alpha = 1.0f
        } else {
            lockBtn.setIconResource(R.drawable.ic_action_lock_open)
            lockBtn.alpha = 0.6f
        }
        arcView.alpha = if (isCourseLocked) 0.5f else 1.0f
    }

    private fun executeArmedManeuver(state: MarineState, port: Boolean) {
        val isProa = settings.NAUTICAL_VESSEL_TYPE.get() == VesselType.PROA
        if (isProa) {
            showConfirmManeuver(tacking = true, isShunt = true) {
                NauticalPlugin.autopilot?.shunt()
            }
        } else {
            val upwind = state.windDirectionApparent?.let { awa -> abs(Math.toDegrees(awa)) < 90.0 } ?: true
            showConfirmManeuver(tacking = upwind) {
                if (upwind) {
                    NauticalPlugin.autopilot?.tack(port = port)
                } else {
                    NauticalPlugin.autopilot?.gybe(port = port)
                }
                speakManeuver(upwind, port = port)
            }
        }
    }

    private fun showConfirmManeuver(tacking: Boolean, isShunt: Boolean = false, onConfirm: () -> Unit) {
        val labelResId = if (isShunt) R.string.nautical_shunt else if (tacking) R.string.nautical_tack else R.string.nautical_gybe
        val label = getString(labelResId).uppercase(Locale.US)

        val isSafe = if (isShunt) true else NauticalPlugin.autopilot?.isWindSafeForManeuver(tacking) ?: false
        val msgResId = if (isShunt) R.string.nautical_confirm_shunt else if (isSafe) R.string.nautical_confirm_maneuver else R.string.nautical_warn_unsafe_maneuver
        val msg = getString(msgResId)

        NauticalPlugin.hudManager?.get()?.showBanner(msg, 10000L, label, !isSafe, onConfirm)
    }

    private fun updateUi(state: MarineState) {
        if (!isAdded) return

        val rawMode = state.autopilotState.uppercase(Locale.US)
        val arbitrator = app.let { net.osmand.plus.plugins.nautical.engine.NauticalHelmArbitrator.getInstance(it) }
        val isLocked = arbitrator.isLockedByEmergency()

        arcView.currentMode = rawMode
        arcView.setNightMode(nightMode)
        errorLinear.setNightMode(nightMode)

        // Mode badge update with OsmAnd styling
        badgePilotMode.text = rawMode
        badgePilotMode.setBackgroundResource(R.drawable.btn_active_light)
        badgePilotMode.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.icon_color_osmand_light)
        badgePilotMode.setTextColor(Color.WHITE)

        // Enable/Disable mode toggle group
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

        // Toggle Group Checked State
        val targetCheckedId = when (rawMode) {
            "AUTO" -> R.id.btn_mode_compass
            "WIND" -> R.id.btn_mode_wind
            "TRACK", "ROUTE" -> R.id.btn_mode_route
            "STANDBY" -> R.id.btn_mode_stop
            else -> View.NO_ID
        }

        if (modeToggleGroup.checkedButtonId != targetCheckedId) {
            modeToggleGroup.check(targetCheckedId)
        }

        // Headings & Deviations
        val actualH = state.headingTrue?.let { Math.toDegrees(it) } ?: 0.0
        val targetH = (state.pendingTargetHeading ?: state.targetHeading ?: state.headingTrue)?.let { Math.toDegrees(it) } ?: 0.0
        var hdgErr = (actualH - targetH).toFloat()
        while (hdgErr > 180) hdgErr -= 360
        while (hdgErr < -180) hdgErr += 360

        if (rawMode == "WIND") {
            val awaDeg = state.windDirectionApparent?.let { Math.toDegrees(it).toInt() } ?: 0
            val targetAwaDeg = state.targetWindAngleApparent?.let { Math.toDegrees(it).toInt() } ?: 0
            var windErr = (awaDeg - targetAwaDeg).toFloat()
            while (windErr > 180) windErr -= 360
            while (windErr < -180) windErr += 360
            errorLinear.label = getString(R.string.nautical_wind_err)
            errorLinear.headingError = windErr
        } else {
            errorLinear.label = getString(R.string.nautical_hdg_err)
            errorLinear.headingError = hdgErr
        }

        arcView.targetHeading = targetH.toInt()
        arcView.actualHeading = actualH.toInt()
        state.windDirectionApparent?.let { arcView.windAngleApparent = Math.toDegrees(it).toInt() }
        state.targetWindAngleApparent?.let { arcView.targetWindAngleApparent = Math.toDegrees(it).toInt() }

        // Rudder
        state.rudderAngle?.let { rudderView.setRudderAngle(it) }

        // Predictive steering active indicator
        predictiveActiveImg.isVisible = NauticalPlugin.autopilot?.activeWaveBias?.let { abs(it) >= 0.3 } ?: false

        // Connection state dimming
        val isStale = state.connectionStatus != ConnectionStatus.CONNECTED
        steeringCard.alpha = if (isStale) 0.5f else 1.0f

        // Off-course alarm background
        if (state.isOffCourse) {
            errorLinear.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.nautical_status_bg_emergency))
        } else {
            errorLinear.setBackgroundColor(Color.TRANSPARENT)
        }
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

    override fun getRightBottomButtonTextId(): Int = DEFAULT_VALUE
    override fun getDismissButtonTextId(): Int = DEFAULT_VALUE
}
