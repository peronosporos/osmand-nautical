package net.osmand.plus.plugins.nautical.ui.widgets

import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
import net.osmand.plus.plugins.nautical.ui.SlideToConfirmView
import net.osmand.plus.settings.enums.VesselType
import net.osmand.plus.track.GpxDialogs
import java.util.Locale
import kotlin.math.abs

class NauticalPilotBottomSheet : BaseNauticalBottomSheet() {

    private lateinit var pilotRoot: LinearLayout
    private lateinit var bottomSheetHandle: View
    private lateinit var txtPilotTitle: TextView
    private lateinit var badgePilotMode: TextView
    private lateinit var btnSettingsGear: ImageButton
    private lateinit var modeToggleGroup: MaterialButtonToggleGroup
    private lateinit var stopBtn: MaterialButton
    private lateinit var compassBtn: MaterialButton
    private lateinit var windBtn: MaterialButton
    private lateinit var routeBtn: MaterialButton
    private lateinit var modeButtons: Array<MaterialButton>

    private lateinit var steeringCard: MaterialCardView
    private lateinit var errorLinear: HeadingErrorLinearView
    private lateinit var minus10Btn: MaterialButton
    private lateinit var minus1Btn: MaterialButton
    private lateinit var plus1Btn: MaterialButton
    private lateinit var plus10Btn: MaterialButton
    private lateinit var arcView: HeadingArcView
    private lateinit var predictiveActiveImg: ImageView
    private lateinit var rudderView: RudderView

    private lateinit var cardTacticalManeuvers: MaterialCardView
    private lateinit var tacticalActionsRow: LinearLayout
    private lateinit var tackPortBtn: MaterialButton
    private lateinit var lockBtn: MaterialButton
    private lateinit var tackStbdBtn: MaterialButton

    private lateinit var layoutEmbeddedConfirmation: LinearLayout
    private lateinit var layoutManeuverTargetSetup: LinearLayout
    private lateinit var txtManeuverTitle: TextView
    private lateinit var txtManeuverTargetDeg: TextView
    private lateinit var btnManeuverDegMinus: MaterialButton
    private lateinit var btnManeuverDegPlus: MaterialButton
    private lateinit var btnCancelEmbeddedConfirmation: MaterialButton
    private lateinit var embeddedSlideConfirm: SlideToConfirmView

    private lateinit var btnDisengageStandby: MaterialButton

    private var confirmationJob: Job? = null
    private var maneuverHelper: AutopilotBottomSheetHelper? = null

    private var isCourseLocked = false
    private var pendingTargetHeading: Int? = null
    private var pendingManeuverTitle: String = ""
    private var pendingExecuteAction: ((targetHeading: Int?) -> Unit)? = null

    private var nightMode = false
    private var isNightVision = false

    companion object {
        const val TAG = "NauticalPilotBottomSheet"

        @JvmStatic
        fun newInstance(): NauticalPilotBottomSheet {
            return NauticalPilotBottomSheet()
        }

        fun show(fragmentManager: androidx.fragment.app.FragmentManager) {
            if (fragmentManager.isStateSaved) return
            if (fragmentManager.findFragmentByTag(TAG) == null) {
                NauticalPilotBottomSheet().show(fragmentManager, TAG)
            }
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

        pilotRoot = customView.findViewById(R.id.pilot_root)
        bottomSheetHandle = customView.findViewById(R.id.drag_handle)
        txtPilotTitle = customView.findViewById(R.id.txt_pilot_title)
        badgePilotMode = customView.findViewById(R.id.badge_pilot_mode)
        btnSettingsGear = customView.findViewById(R.id.btn_settings_gear)

        modeToggleGroup = customView.findViewById(R.id.mode_toggle_group)
        stopBtn = customView.findViewById(R.id.btn_mode_stop)
        compassBtn = customView.findViewById(R.id.btn_mode_compass)
        windBtn = customView.findViewById(R.id.btn_mode_wind)
        val twaBtn = customView.findViewById<MaterialButton>(R.id.btn_mode_twa)
        routeBtn = customView.findViewById(R.id.btn_mode_route)
        modeButtons = arrayOf(compassBtn, windBtn, twaBtn, routeBtn, stopBtn)

        steeringCard = customView.findViewById(R.id.steering_card)
        errorLinear = customView.findViewById(R.id.heading_error_linear)
        minus10Btn = customView.findViewById(R.id.btn_minus_10)
        minus1Btn = customView.findViewById(R.id.btn_minus_1)
        plus1Btn = customView.findViewById(R.id.btn_plus_1)
        plus10Btn = customView.findViewById(R.id.btn_plus_10)
        arcView = customView.findViewById(R.id.heading_arc_view)
        predictiveActiveImg = customView.findViewById(R.id.img_predictive_active)
        rudderView = customView.findViewById(R.id.rudder_view)

        cardTacticalManeuvers = customView.findViewById(R.id.card_tactical_maneuvers)
        tacticalActionsRow = customView.findViewById(R.id.tactical_actions_row)
        tackPortBtn = customView.findViewById(R.id.btn_tack_port)
        lockBtn = customView.findViewById(R.id.btn_lock_unlock)
        tackStbdBtn = customView.findViewById(R.id.btn_tack_stbd)

        layoutEmbeddedConfirmation = customView.findViewById(R.id.layout_embedded_confirmation)
        layoutManeuverTargetSetup = customView.findViewById(R.id.layout_maneuver_target_setup)
        txtManeuverTitle = customView.findViewById(R.id.txt_maneuver_title)
        txtManeuverTargetDeg = customView.findViewById(R.id.txt_maneuver_target_deg)
        btnManeuverDegMinus = customView.findViewById(R.id.btn_maneuver_deg_minus)
        btnManeuverDegPlus = customView.findViewById(R.id.btn_maneuver_deg_plus)
        btnCancelEmbeddedConfirmation = customView.findViewById(R.id.btn_cancel_embedded_confirmation)
        embeddedSlideConfirm = customView.findViewById(R.id.embedded_slide_confirm)

        btnDisengageStandby = customView.findViewById(R.id.btn_disengage_standby)

        btnCancelEmbeddedConfirmation.setOnClickListener {
            hideEmbeddedConfirmation()
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }

        btnManeuverDegMinus.setOnClickListener {
            pendingTargetHeading?.let { cur ->
                val newH = (((cur - 5) % 360) + 360) % 360
                pendingTargetHeading = newH
                updateEmbeddedTargetDisplay()
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
        }

        btnManeuverDegPlus.setOnClickListener {
            pendingTargetHeading?.let { cur ->
                val newH = (((cur + 5) % 360) + 360) % 360
                pendingTargetHeading = newH
                updateEmbeddedTargetDisplay()
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
        }

        // Settings gear button
        btnSettingsGear.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            NauticalAdvancedSettingsBottomSheet.newInstance().show(parentFragmentManager, "advanced_settings")
        }

        // Lock course touch guard
        isCourseLocked = app.settings.NAUTICAL_LOCK_TOUCH_DURING_MANEUVERS.get()
        updateLockButton()

        lockBtn.setOnClickListener {
            isCourseLocked = !isCourseLocked
            updateLockButton()
            val msgRes = if (isCourseLocked) R.string.nautical_touch_lock_active else R.string.nautical_lock_course
            app.showToastMessage(msgRes)
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }

        // Mode Switcher Listeners
        stopBtn.setOnClickListener {
            autopilot.stopNavigation()
            speakMode("STANDBY")
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            syncUiWithState()
        }

        btnDisengageStandby.setOnClickListener {
            autopilot.stopNavigation()
            speakMode("STANDBY")
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
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
                        speakMode("AWA")
                    }
                    R.id.btn_mode_twa -> {
                        autopilot.setAutopilotMode("twa")
                        speakMode("TWA")
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
                    R.id.btn_mode_stop -> {
                        autopilot.stopNavigation()
                        speakMode("STANDBY")
                    }
                }
                customView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                syncUiWithState()
            }
        }

        // Tactile Stepped Nudge Buttons
        minus10Btn.setOnClickListener {
            autopilot.adjustHeading(-10.0)
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
        minus1Btn.setOnClickListener {
            autopilot.adjustHeading(-1.0)
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
        plus1Btn.setOnClickListener {
            autopilot.adjustHeading(1.0)
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
        plus10Btn.setOnClickListener {
            autopilot.adjustHeading(10.0)
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }

        // Tactical Maneuver Actions with Pre-activation Target Heading Setup
        val helper = AutopilotBottomSheetHelper(
            sheet = this,
            tackPortBtn = tackPortBtn,
            tackStbdBtn = tackStbdBtn,
            onInitiateManeuver = { title, targetHeading, onExecute ->
                showEmbeddedConfirmation(title, targetHeading, onExecute)
            }
        )
        maneuverHelper = helper

        tackPortBtn.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            val state = NauticalPlugin.engine?.getCurrentState()
            helper.handlePrimaryManeuver(settings.NAUTICAL_VESSEL_TYPE.get(), state, isPort = true)
        }
        tackStbdBtn.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            val state = NauticalPlugin.engine?.getCurrentState()
            helper.handlePrimaryManeuver(settings.NAUTICAL_VESSEL_TYPE.get(), state, isPort = false)
        }

        updateManeuverButtons()

        // Continuous Heading / Wind Angle Slider Callbacks
        arcView.onHeadingChanged = { newHeading ->
            autopilot.setTargetHeading(newHeading.toDouble())
        }
        arcView.onHeadingCommitted = { newHeading ->
            autopilot.setTargetHeading(newHeading.toDouble())
            syncUiWithState()
        }
        arcView.onWindAngleChanged = { newAngle ->
            autopilot.setTargetWindAngle(newAngle.toDouble())
            syncUiWithState()
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
                customView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        }

        // Reset course deviation error on linear scale click
        errorLinear.setOnClickListener {
            val state = NauticalPlugin.engine?.getCurrentState()
            state?.headingTrue?.let { hdg ->
                NauticalPlugin.autopilot?.setTargetHeading(Math.toDegrees(hdg))
                app.showToastMessage(R.string.nautical_course_reset)
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
        }

        // Apply touch guard to arc view
        NauticalTouchGuard.apply(arcView, isLockedCheck = { isCourseLocked })

        // Initial styling
        val isNight = NauticalPlugin.isNightVision(app)
        isNightVision = isNight
        applyNightVisionStyling(isNightVision)

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
            val activeColor = net.osmand.plus.utils.AndroidUtils.getColorFromAttr(requireContext(), R.attr.active_color_primary)
            lockBtn.backgroundTintList = ColorStateList.valueOf(activeColor)
            lockBtn.iconTint = ColorStateList.valueOf(Color.WHITE)
            lockBtn.contentDescription = getString(R.string.nautical_touch_lock_active)
        } else {
            lockBtn.setIconResource(R.drawable.ic_action_lock_open)
            lockBtn.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            val defaultIconColor = net.osmand.plus.utils.AndroidUtils.getColorFromAttr(requireContext(), R.attr.icon_color_primary)
            lockBtn.iconTint = ColorStateList.valueOf(defaultIconColor)
            lockBtn.contentDescription = getString(R.string.nautical_lock_course)
        }
        arcView.alpha = if (isCourseLocked) 0.5f else 1.0f
        minus10Btn.isEnabled = !isCourseLocked
        minus1Btn.isEnabled = !isCourseLocked
        plus1Btn.isEnabled = !isCourseLocked
        plus10Btn.isEnabled = !isCourseLocked
        minus10Btn.alpha = if (isCourseLocked) 0.4f else 1.0f
        minus1Btn.alpha = if (isCourseLocked) 0.4f else 1.0f
        plus1Btn.alpha = if (isCourseLocked) 0.4f else 1.0f
        plus10Btn.alpha = if (isCourseLocked) 0.4f else 1.0f
    }

    private fun updateManeuverButtons() {
        maneuverHelper?.updateManeuverButtons(settings.NAUTICAL_VESSEL_TYPE.get())
    }

    private fun updateEmbeddedTargetDisplay() {
        val target = pendingTargetHeading
        if (target != null) {
            txtManeuverTargetDeg.text = String.format(Locale.US, "Target: %d°", target)
            embeddedSlideConfirm.label = String.format(Locale.US, "SLIDE TO %s (%d°)", pendingManeuverTitle.uppercase(Locale.US), target)
        } else {
            embeddedSlideConfirm.label = String.format(Locale.US, "SLIDE TO %s", pendingManeuverTitle.uppercase(Locale.US))
        }
    }

    private fun showEmbeddedConfirmation(title: String, targetHeading: Int?, onExecute: (targetHeading: Int?) -> Unit) {
        confirmationJob?.cancel()
        pendingManeuverTitle = title
        pendingTargetHeading = targetHeading
        pendingExecuteAction = onExecute

        tacticalActionsRow.visibility = View.GONE
        layoutEmbeddedConfirmation.visibility = View.VISIBLE

        txtManeuverTitle.text = title
        if (targetHeading != null) {
            layoutManeuverTargetSetup.visibility = View.VISIBLE
            btnManeuverDegMinus.visibility = View.VISIBLE
            btnManeuverDegPlus.visibility = View.VISIBLE
            txtManeuverTargetDeg.visibility = View.VISIBLE
            updateEmbeddedTargetDisplay()
        } else {
            btnManeuverDegMinus.visibility = View.GONE
            btnManeuverDegPlus.visibility = View.GONE
            txtManeuverTargetDeg.visibility = View.GONE
            updateEmbeddedTargetDisplay()
        }

        embeddedSlideConfirm.reset()
        embeddedSlideConfirm.onConfirm = {
            pendingExecuteAction?.invoke(pendingTargetHeading)
            hideEmbeddedConfirmation()
        }

        confirmationJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(15000L)
            hideEmbeddedConfirmation()
        }
    }

    private fun hideEmbeddedConfirmation() {
        confirmationJob?.cancel()
        confirmationJob = null
        pendingExecuteAction = null
        pendingTargetHeading = null
        layoutEmbeddedConfirmation.visibility = View.GONE
        tacticalActionsRow.visibility = View.VISIBLE
        embeddedSlideConfirm.reset()
    }

    private fun applyNightVisionStyling(isNightVision: Boolean) {
        if (isNightVision) {
            pilotRoot.setBackgroundColor(0xEE120000.toInt())
            bottomSheetHandle.backgroundTintList = ColorStateList.valueOf(0xFF8B0000.toInt())
            txtPilotTitle.setTextColor(0xFFFF1744.toInt())
            badgePilotMode.setTextColor(0xFFFF1744.toInt())
            badgePilotMode.backgroundTintList = ColorStateList.valueOf(0xFF4A0007.toInt())
            steeringCard.setCardBackgroundColor(0xFF1E0002.toInt())
            steeringCard.strokeColor = 0xFF4A0007.toInt()
            cardTacticalManeuvers.setCardBackgroundColor(0xFF1E0002.toInt())
            cardTacticalManeuvers.strokeColor = 0xFF4A0007.toInt()
            minus10Btn.strokeColor = ColorStateList.valueOf(0xFFFF1744.toInt())
            minus10Btn.setTextColor(0xFFFF1744.toInt())
            minus1Btn.strokeColor = ColorStateList.valueOf(0xFFFF1744.toInt())
            minus1Btn.setTextColor(0xFFFF1744.toInt())
            plus10Btn.strokeColor = ColorStateList.valueOf(0xFFFF1744.toInt())
            plus10Btn.setTextColor(0xFFFF1744.toInt())
            plus1Btn.strokeColor = ColorStateList.valueOf(0xFFFF1744.toInt())
            plus1Btn.setTextColor(0xFFFF1744.toInt())
            tackPortBtn.strokeColor = ColorStateList.valueOf(0xFFFF1744.toInt())
            tackPortBtn.setTextColor(0xFFFF1744.toInt())
            tackStbdBtn.strokeColor = ColorStateList.valueOf(0xFFFF1744.toInt())
            tackStbdBtn.setTextColor(0xFFFF1744.toInt())
            btnDisengageStandby.backgroundTintList = ColorStateList.valueOf(0xFFB71C1C.toInt())
            btnDisengageStandby.setTextColor(Color.WHITE)
        } else {
            val defaultCardBg = net.osmand.plus.utils.AndroidUtils.getColorFromAttr(requireContext(), R.attr.card_and_list_background_basic)
            val defaultDivider = net.osmand.plus.utils.AndroidUtils.getColorFromAttr(requireContext(), R.attr.divider_color)
            val defaultTextPrimary = net.osmand.plus.utils.AndroidUtils.getColorFromAttr(requireContext(), android.R.attr.textColorPrimary)

            txtPilotTitle.setTextColor(defaultTextPrimary)
            steeringCard.setCardBackgroundColor(defaultCardBg)
            steeringCard.strokeColor = defaultDivider
            cardTacticalManeuvers.setCardBackgroundColor(defaultCardBg)
            cardTacticalManeuvers.strokeColor = defaultDivider
            minus10Btn.strokeColor = ColorStateList.valueOf(0xFFE53935.toInt())
            minus10Btn.setTextColor(0xFFE53935.toInt())
            minus1Btn.strokeColor = ColorStateList.valueOf(0xFFE53935.toInt())
            minus1Btn.setTextColor(0xFFE53935.toInt())
            plus10Btn.strokeColor = ColorStateList.valueOf(0xFF43A047.toInt())
            plus10Btn.setTextColor(0xFF43A047.toInt())
            plus1Btn.strokeColor = ColorStateList.valueOf(0xFF43A047.toInt())
            plus1Btn.setTextColor(0xFF43A047.toInt())
            tackPortBtn.strokeColor = ColorStateList.valueOf(0xFFE53935.toInt())
            tackPortBtn.setTextColor(0xFFE53935.toInt())
            tackStbdBtn.strokeColor = ColorStateList.valueOf(0xFF43A047.toInt())
            tackStbdBtn.setTextColor(0xFF43A047.toInt())
            btnDisengageStandby.backgroundTintList = ColorStateList.valueOf(0xFFD32F2F.toInt())
            btnDisengageStandby.setTextColor(Color.WHITE)
        }
    }

    private fun updateUi(state: MarineState) {
        if (!isAdded) return

        val rawMode = state.autopilotState.uppercase(Locale.US)
        val arbitrator = app.let { net.osmand.plus.plugins.nautical.engine.NauticalHelmArbitrator.getInstance(it) }
        val isLocked = arbitrator.isLockedByEmergency()

        val isNight = NauticalPlugin.isNightVision(app)
        if (isNightVision != isNight) {
            isNightVision = isNight
            applyNightVisionStyling(isNightVision)
        }

        arcView.currentMode = rawMode
        arcView.setNightMode(nightMode || isNightVision)
        errorLinear.setNightMode(nightMode || isNightVision)
        rudderView.setNightMode(nightMode || isNightVision)

        // Mode badge update with OsmAnd styling
        badgePilotMode.text = rawMode
        if (isNightVision) {
            badgePilotMode.setTextColor(0xFFFF1744.toInt())
            badgePilotMode.backgroundTintList = ColorStateList.valueOf(0xFF4A0007.toInt())
        } else {
            badgePilotMode.setBackgroundResource(R.drawable.btn_active_light)
            badgePilotMode.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.icon_color_osmand_light)
            badgePilotMode.setTextColor(Color.WHITE)
        }

        // Enable/Disable mode toggle group
        if (isLocked) {
            modeToggleGroup.isEnabled = false
            modeToggleGroup.alpha = 0.5f
            arcView.isEnabled = false
            arcView.alpha = 0.5f
        } else {
            modeToggleGroup.isEnabled = true
            modeToggleGroup.alpha = 1.0f
            arcView.isEnabled = !isCourseLocked
            arcView.alpha = if (isCourseLocked) 0.5f else 1.0f
        }

        // Toggle Group Checked State
        val targetCheckedId = when (rawMode) {
            "AUTO" -> R.id.btn_mode_compass
            "WIND" -> R.id.btn_mode_wind
            "TWA" -> R.id.btn_mode_twa
            "TRACK", "ROUTE" -> R.id.btn_mode_route
            "STANDBY" -> R.id.btn_mode_stop
            else -> View.NO_ID
        }

        if (modeToggleGroup.checkedButtonId != targetCheckedId) {
            modeToggleGroup.check(targetCheckedId)
        }

        val orangeColor = if (isNightVision) 0xFFFF1744.toInt() else ContextCompat.getColor(requireContext(), R.color.icon_color_osmand_light)
        val defaultIconColor = if (isNightVision) 0xFFFF1744.toInt() else net.osmand.plus.utils.AndroidUtils.getColorFromAttr(requireContext(), R.attr.icon_color_primary)
        val defaultStrokeColor = if (isNightVision) 0xFFFF1744.toInt() else net.osmand.plus.utils.AndroidUtils.getColorFromAttr(requireContext(), R.attr.active_color_primary)

        for (btn in modeButtons) {
            val isChecked = btn.id == targetCheckedId
            if (isChecked) {
                btn.backgroundTintList = ColorStateList.valueOf(orangeColor)
                btn.iconTint = ColorStateList.valueOf(Color.WHITE)
            } else {
                btn.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                btn.iconTint = ColorStateList.valueOf(defaultIconColor)
            }
            btn.strokeColor = ColorStateList.valueOf(defaultStrokeColor)
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
            errorLinear.headingError = windErr
        } else if (rawMode == "TWA") {
            val twaDeg = state.trueWindAngle?.let { Math.toDegrees(it).toInt() } ?: 0
            val targetTwaDeg = state.targetWindAngleTrue?.let { Math.toDegrees(it).toInt() } ?: 0
            var windErr = (twaDeg - targetTwaDeg).toFloat()
            while (windErr > 180) windErr -= 360
            while (windErr < -180) windErr += 360
            errorLinear.headingError = windErr
        } else {
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

        updateManeuverButtons()
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

    override fun getRightBottomButtonTextId(): Int = DEFAULT_VALUE
    override fun getDismissButtonTextId(): Int = DEFAULT_VALUE
}
