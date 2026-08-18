package net.osmand.plus.plugins.nautical.ui.widgets

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import net.osmand.plus.R
import net.osmand.plus.base.bottomsheetmenu.BaseBottomSheetItem
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.SignalKUnitConverter
import net.osmand.plus.plugins.nautical.ui.NauticalTouchGuard
import java.util.Locale

class NauticalAdvancedSettingsBottomSheet : BaseNauticalBottomSheet() {

    companion object {
        fun newInstance() = NauticalAdvancedSettingsBottomSheet()
    }

    override fun createMenuItems(savedInstanceState: Bundle?) {
        val autopilot = NauticalPlugin.autopilot ?: return
        val plugin = NauticalPlugin.getInstance() ?: return
        
        val app = (activity?.application as? net.osmand.plus.OsmandApplication) ?: return
        val settings = app.settings
        val arbitrator = net.osmand.plus.plugins.nautical.engine.NauticalHelmArbitrator.getInstance(app)
        val isEmergencyLocked = arbitrator.isLockedByEmergency()

        addTitleItem(getString(R.string.nautical_advanced_settings))

        val customView = LayoutInflater.from(requireContext()).inflate(R.layout.bottom_sheet_nautical_advanced, null)
        
        val safetyLock = customView.findViewById<SwitchMaterial>(R.id.safety_lock)

        if (isEmergencyLocked) {
            safetyLock.isChecked = true
            safetyLock.isEnabled = false
            safetyLock.text = getString(R.string.nautical_helm_locked_by, arbitrator.getActiveManeuver() ?: "")
            safetyLock.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_color_negative))
        }

        // Tab 1: Tuning Sliders & Value Labels
        val sliderRudderGain = customView.findViewById<Slider>(R.id.slider_rudder_gain)
        val txtValueRudderGain = customView.findViewById<TextView>(R.id.txt_value_rudder_gain)
        val sliderCounterRudder = customView.findViewById<Slider>(R.id.slider_counter_rudder)
        val txtValueCounterRudder = customView.findViewById<TextView>(R.id.txt_value_counter_rudder)
        val sliderAutoTrim = customView.findViewById<Slider>(R.id.slider_auto_trim)
        val txtValueAutoTrim = customView.findViewById<TextView>(R.id.txt_value_auto_trim)
        val sliderFilterSensitivity = customView.findViewById<Slider>(R.id.slider_filter_sensitivity)
        val txtValueFilterSensitivity = customView.findViewById<TextView>(R.id.txt_value_filter_sensitivity)

        // Tab 2: Limits Sliders & Value Labels
        val sliderRudderLimit = customView.findViewById<Slider>(R.id.slider_rudder_limit)
        val txtValueRudderLimit = customView.findViewById<TextView>(R.id.txt_value_rudder_limit)
        val sliderOffCourse = customView.findViewById<Slider>(R.id.slider_off_course)
        val txtValueOffCourse = customView.findViewById<TextView>(R.id.txt_value_off_course)
        val sliderXteThreshold = customView.findViewById<Slider>(R.id.slider_xte_threshold)
        val txtXteThresholdValue = customView.findViewById<TextView>(R.id.txt_value_xte_threshold)
        val sliderActuatorThreshold = customView.findViewById<Slider>(R.id.slider_actuator_threshold)
        val txtValueActuatorThreshold = customView.findViewById<TextView>(R.id.txt_value_actuator_threshold)
        val sliderKeelOffset = customView.findViewById<Slider>(R.id.slider_keel_offset)
        val txtKeelOffsetValue = customView.findViewById<TextView>(R.id.txt_value_keel_offset)
        val sliderWindAlignment = customView.findViewById<Slider>(R.id.slider_wind_alignment)
        val txtWindAlignmentValue = customView.findViewById<TextView>(R.id.txt_value_wind_alignment)

        // Tab 3: Environment Sliders & Controls
        val sliderSeaState = customView.findViewById<Slider>(R.id.slider_sea_state)
        val txtSeaStateValue = customView.findViewById<TextView>(R.id.txt_value_sea_state)
        val switchAutoSeaState = customView.findViewById<SwitchMaterial>(R.id.switch_auto_sea_state)
        val sliderWaveBias = customView.findViewById<Slider>(R.id.slider_wave_bias)
        val txtValueWaveBias = customView.findViewById<TextView>(R.id.txt_value_wave_bias)

        // Navigation Tabs & Containers
        val tabLayout = customView.findViewById<TabLayout>(R.id.tab_layout)
        val containerTuning = customView.findViewById<View>(R.id.container_tuning)
        val containerLimits = customView.findViewById<View>(R.id.container_limits)
        val containerEnv = customView.findViewById<View>(R.id.container_env)
        val containerPypilot = customView.findViewById<View>(R.id.container_pypilot)

        tabLayout.addTab(tabLayout.newTab().setText("Tuning"))
        tabLayout.addTab(tabLayout.newTab().setText("Limits"))
        tabLayout.addTab(tabLayout.newTab().setText("Env"))

        if (plugin.capabilityManager?.capabilities?.value?.hasAutopilot == true) {
            tabLayout.addTab(tabLayout.newTab().setText("Pypilot"))
        }

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                containerTuning.visibility = if (tab?.position == 0) View.VISIBLE else View.GONE
                containerLimits.visibility = if (tab?.position == 1) View.VISIBLE else View.GONE
                containerEnv.visibility = if (tab?.position == 2) View.VISIBLE else View.GONE
                containerPypilot.visibility = if (tab?.position == 3) View.VISIBLE else View.GONE
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        // Tab 4: Pypilot Sliders & Value Labels
        val sliderP = customView.findViewById<Slider>(R.id.slider_p)
        val txtValueP = customView.findViewById<TextView>(R.id.txt_value_p)
        val sliderI = customView.findViewById<Slider>(R.id.slider_i)
        val txtValueI = customView.findViewById<TextView>(R.id.txt_value_i)
        val sliderD = customView.findViewById<Slider>(R.id.slider_d)
        val txtValueD = customView.findViewById<TextView>(R.id.txt_value_d)
        val sliderDD = customView.findViewById<Slider>(R.id.slider_dd)
        val txtValueDD = customView.findViewById<TextView>(R.id.txt_value_dd)
        val sliderPR = customView.findViewById<Slider>(R.id.slider_pr)
        val txtValuePR = customView.findViewById<TextView>(R.id.txt_value_pr)
        val sliderFF = customView.findViewById<Slider>(R.id.slider_ff)
        val txtValueFF = customView.findViewById<TextView>(R.id.txt_value_ff)
        val sliderWG = customView.findViewById<Slider>(R.id.slider_wg)
        val txtValueWG = customView.findViewById<TextView>(R.id.txt_value_wg)
        val sliderDeadzone = customView.findViewById<Slider>(R.id.slider_deadzone)
        val txtValueDeadzone = customView.findViewById<TextView>(R.id.txt_value_deadzone)
        
        val progressCompass = customView.findViewById<ProgressBar>(R.id.progress_compass)
        val progressRudder = customView.findViewById<ProgressBar>(R.id.progress_rudder)
        val btnCalibrateCompass = customView.findViewById<Button>(R.id.btn_calibrate_compass)
        val btnCalibrateRudder = customView.findViewById<Button>(R.id.btn_calibrate_rudder)

        // Apply Touch Guards to ALL interactive elements
        val allSliders = listOf(
            sliderRudderGain, sliderCounterRudder, sliderAutoTrim, sliderFilterSensitivity,
            sliderRudderLimit, sliderOffCourse, sliderXteThreshold, sliderSeaState,
            sliderKeelOffset, sliderWindAlignment, sliderWaveBias, sliderActuatorThreshold,
            sliderP, sliderI, sliderD, sliderDD, sliderPR, sliderFF, sliderWG, sliderDeadzone
        )
        allSliders.forEach { NauticalTouchGuard.apply(it, safetyLock) }

        // Live Telemetry / Pypilot Stream Subscription
        viewLifecycleOwner.lifecycleScope.launch {
            NauticalPlugin.engine?.marineStateFlow?.collectLatest { state ->
                if (state.seaState != null && switchAutoSeaState.isChecked) {
                    val ss = state.seaState.toFloat().coerceIn(sliderSeaState.valueFrom, sliderSeaState.valueTo)
                    if (!sliderSeaState.isFocused) {
                        sliderSeaState.value = ss
                        txtSeaStateValue.text = ss.toInt().toString()
                    }
                }
                state.pypilotConfig?.let { config ->
                    fun updatePypilot(slider: Slider, txt: TextView, v: Double?, format: String) {
                        if (v != null && !slider.isFocused) {
                            val clamped = v.toFloat().coerceIn(slider.valueFrom, slider.valueTo)
                            slider.value = clamped
                            txt.text = String.format(Locale.US, format, clamped)
                        }
                    }
                    updatePypilot(sliderP, txtValueP, config.p, "%.4f")
                    updatePypilot(sliderI, txtValueI, config.i, "%.4f")
                    updatePypilot(sliderD, txtValueD, config.d, "%.4f")
                    updatePypilot(sliderDD, txtValueDD, config.dd, "%.4f")
                    updatePypilot(sliderPR, txtValuePR, config.pr, "%.2f")
                    updatePypilot(sliderFF, txtValueFF, config.ff, "%.2f")
                    updatePypilot(sliderWG, txtValueWG, config.wg, "%.2f")
                    updatePypilot(sliderDeadzone, txtValueDeadzone, config.deadzone, "%.1f°")
                }
                state.pypilotCalibration?.let { cal ->
                    progressCompass.progress = (cal.compassCalibrationProgress ?: 0.0).toInt()
                    progressRudder.progress = (cal.rudderCalibrationProgress ?: 0.0).toInt()
                }
            }
        }

        val chart = customView.findViewById<LineChart>(R.id.pid_preview_chart)

        // Initialize values from preferences
        sliderRudderGain.value = (settings.NAUTICAL_RUDDER_GAIN.get() as Float).coerceIn(sliderRudderGain.valueFrom, sliderRudderGain.valueTo)
        txtValueRudderGain.text = String.format(Locale.US, "%.1f", sliderRudderGain.value)

        sliderCounterRudder.value = (settings.NAUTICAL_COUNTER_RUDDER.get() as Float).coerceIn(sliderCounterRudder.valueFrom, sliderCounterRudder.valueTo)
        txtValueCounterRudder.text = String.format(Locale.US, "%.1f", sliderCounterRudder.value)

        sliderAutoTrim.value = (settings.NAUTICAL_AUTO_TRIM.get() as Float).coerceIn(sliderAutoTrim.valueFrom, sliderAutoTrim.valueTo)
        txtValueAutoTrim.text = String.format(Locale.US, "%.2f", sliderAutoTrim.value)

        sliderFilterSensitivity.value = (settings.NAUTICAL_FILTER_SENSITIVITY.get() as Float).coerceIn(sliderFilterSensitivity.valueFrom, sliderFilterSensitivity.valueTo)
        txtValueFilterSensitivity.text = sliderFilterSensitivity.value.toInt().toString()

        sliderRudderLimit.value = (settings.NAUTICAL_RUDDER_LIMIT.get() as Float).coerceIn(sliderRudderLimit.valueFrom, sliderRudderLimit.valueTo)
        txtValueRudderLimit.text = "${sliderRudderLimit.value.toInt()}°"

        sliderOffCourse.value = (settings.NAUTICAL_OFF_COURSE_ALARM.get() as Float).coerceIn(sliderOffCourse.valueFrom, sliderOffCourse.valueTo)
        txtValueOffCourse.text = "${sliderOffCourse.value.toInt()}°"

        sliderActuatorThreshold.value = (settings.NAUTICAL_ACTUATOR_ALARM_THRESHOLD.get() as Float).coerceIn(sliderActuatorThreshold.valueFrom, sliderActuatorThreshold.valueTo)
        txtValueActuatorThreshold.text = "${sliderActuatorThreshold.value.toInt()}%"

        sliderKeelOffset.value = (settings.NAUTICAL_KEEL_OFFSET.get() as Float).coerceIn(sliderKeelOffset.valueFrom, sliderKeelOffset.valueTo)
        val initialKeel = SignalKUnitConverter.formatValue(app, settings, sliderKeelOffset.value.toDouble(), "depth")
        txtKeelOffsetValue.text = "${initialKeel.first} ${initialKeel.second}"

        sliderWindAlignment.value = (settings.NAUTICAL_WIND_ALIGNMENT.get() as Float).coerceIn(sliderWindAlignment.valueFrom, sliderWindAlignment.valueTo)
        val initialWind = SignalKUnitConverter.formatValue(app, settings, Math.toRadians(sliderWindAlignment.value.toDouble()), "angle")
        txtWindAlignmentValue.text = "${initialWind.first}${initialWind.second}"

        sliderXteThreshold.value = (settings.NAUTICAL_XTE_THRESHOLD.get() as Float).coerceIn(sliderXteThreshold.valueFrom, sliderXteThreshold.valueTo)
        val initialXte = SignalKUnitConverter.formatValue(app, settings, SignalKUnitConverter.nmToMeters(sliderXteThreshold.value.toDouble()), "distance")
        txtXteThresholdValue.text = "${initialXte.first} ${initialXte.second}"

        sliderSeaState.value = (settings.NAUTICAL_PILOT_SEA_STATE.get() ?: 3).toFloat().coerceIn(sliderSeaState.valueFrom, sliderSeaState.valueTo)
        txtSeaStateValue.text = sliderSeaState.value.toInt().toString()

        sliderWaveBias.value = (settings.NAUTICAL_WAVE_BIAS_SENSITIVITY.get() as Number).toFloat().coerceIn(sliderWaveBias.valueFrom, sliderWaveBias.valueTo)
        txtValueWaveBias.text = "${sliderWaveBias.value.toInt()}%"

        // Auto sea state toggle wiring
        switchAutoSeaState.isChecked = settings.NAUTICAL_PILOT_AUTO_SEA_STATE.get()
        sliderSeaState.isEnabled = !switchAutoSeaState.isChecked
        sliderSeaState.alpha = if (switchAutoSeaState.isChecked) 0.5f else 1.0f

        switchAutoSeaState.setOnCheckedChangeListener { _, isChecked ->
            sliderSeaState.isEnabled = !isChecked
            sliderSeaState.alpha = if (isChecked) 0.5f else 1.0f
        }

        val updateChart = {
            updatePreviewChart(
                chart,
                sliderRudderGain.value.toDouble(),
                sliderCounterRudder.value.toDouble(),
                sliderAutoTrim.value.toDouble(),
                sliderRudderLimit.value.toDouble()
            )
        }

        // Live Real-Time Slider Listeners
        sliderRudderGain.addOnChangeListener { _, value, fromUser -> 
            txtValueRudderGain.text = String.format(Locale.US, "%.1f", value)
            if (fromUser) autopilot.setRudderGain(value.toDouble())
            updateChart()
        }
        sliderCounterRudder.addOnChangeListener { _, value, fromUser -> 
            txtValueCounterRudder.text = String.format(Locale.US, "%.1f", value)
            if (fromUser) autopilot.setCounterRudder(value.toDouble())
            updateChart()
        }
        sliderAutoTrim.addOnChangeListener { _, value, fromUser -> 
            txtValueAutoTrim.text = String.format(Locale.US, "%.2f", value)
            if (fromUser) autopilot.setAutoTrim(value.toDouble())
            updateChart()
        }
        sliderFilterSensitivity.addOnChangeListener { _, value, fromUser ->
            txtValueFilterSensitivity.text = value.toInt().toString()
            if (fromUser) autopilot.setFilterSensitivity(value.toDouble())
        }
        sliderRudderLimit.addOnChangeListener { _, value, fromUser ->
            txtValueRudderLimit.text = "${value.toInt()}°"
            if (fromUser) autopilot.setRudderLimit(value.toDouble())
            updateChart()
        }
        sliderOffCourse.addOnChangeListener { _, value, fromUser ->
            txtValueOffCourse.text = "${value.toInt()}°"
            if (fromUser) autopilot.setOffCourseAlarm(value.toDouble())
        }
        sliderActuatorThreshold.addOnChangeListener { _, value, _ ->
            txtValueActuatorThreshold.text = "${value.toInt()}%"
        }
        sliderKeelOffset.addOnChangeListener { _, value, _ ->
            val (v, u) = SignalKUnitConverter.formatValue(app, settings, value.toDouble(), "depth")
            txtKeelOffsetValue.text = "$v $u"
        }
        sliderWindAlignment.addOnChangeListener { _, value, _ ->
            val (v, u) = SignalKUnitConverter.formatValue(app, settings, Math.toRadians(value.toDouble()), "angle")
            txtWindAlignmentValue.text = "$v$u"
        }
        sliderXteThreshold.addOnChangeListener { _, value, _ ->
            val meters = SignalKUnitConverter.nmToMeters(value.toDouble())
            val (v, u) = SignalKUnitConverter.formatValue(app, settings, meters, "distance")
            txtXteThresholdValue.text = "$v $u"
        }
        sliderSeaState.addOnChangeListener { _, value, fromUser -> 
            txtSeaStateValue.text = value.toInt().toString()
            if (fromUser) autopilot.setSeaState(value.toInt())
        }
        sliderWaveBias.addOnChangeListener { _, value, _ ->
            txtValueWaveBias.text = "${value.toInt()}%"
        }

        // Pypilot Direct Live Tuning Listeners
        sliderP.addOnChangeListener { _, v, fromUser -> 
            txtValueP.text = String.format(Locale.US, "%.4f", v)
            if (fromUser) autopilot.setPypilotGain("p", v.toDouble()) 
        }
        sliderI.addOnChangeListener { _, v, fromUser -> 
            txtValueI.text = String.format(Locale.US, "%.4f", v)
            if (fromUser) autopilot.setPypilotGain("i", v.toDouble()) 
        }
        sliderD.addOnChangeListener { _, v, fromUser -> 
            txtValueD.text = String.format(Locale.US, "%.4f", v)
            if (fromUser) autopilot.setPypilotGain("d", v.toDouble()) 
        }
        sliderDD.addOnChangeListener { _, v, fromUser -> 
            txtValueDD.text = String.format(Locale.US, "%.4f", v)
            if (fromUser) autopilot.setPypilotGain("dd", v.toDouble()) 
        }
        sliderPR.addOnChangeListener { _, v, fromUser -> 
            txtValuePR.text = String.format(Locale.US, "%.2f", v)
            if (fromUser) autopilot.setPypilotGain("pr", v.toDouble()) 
        }
        sliderFF.addOnChangeListener { _, v, fromUser -> 
            txtValueFF.text = String.format(Locale.US, "%.2f", v)
            if (fromUser) autopilot.setPypilotGain("ff", v.toDouble()) 
        }
        sliderWG.addOnChangeListener { _, v, fromUser -> 
            txtValueWG.text = String.format(Locale.US, "%.2f", v)
            if (fromUser) autopilot.setPypilotGain("wg", v.toDouble()) 
        }
        sliderDeadzone.addOnChangeListener { _, v, fromUser -> 
            txtValueDeadzone.text = String.format(Locale.US, "%.1f°", v)
            if (fromUser) autopilot.setPypilotGain("deadzone", v.toDouble()) 
        }

        // Pypilot Calibration & Profile Buttons
        btnCalibrateCompass.setOnClickListener { autopilot.startPypilotCalibration("compass") }
        btnCalibrateRudder.setOnClickListener { autopilot.startPypilotCalibration("rudder") }
        customView.findViewById<Button>(R.id.btn_stop_compass_calib)?.setOnClickListener { autopilot.stopPypilotCalibration("compass") }
        customView.findViewById<Button>(R.id.btn_stop_rudder_calib)?.setOnClickListener { autopilot.stopPypilotCalibration("rudder") }
        customView.findViewById<Button>(R.id.btn_select_pypilot_profile)?.setOnClickListener {
            val profiles = arrayOf("Default", "Slow", "Heavy", "Racing")
            androidx.appcompat.app.AlertDialog.Builder(requireContext()).setTitle(R.string.nautical_pypilot_profile)
                .setItems(profiles) { _, which -> autopilot.setPypilotProfile(profiles[which]) }.show()
        }

        updateChart()

        // Apply and Save Button
        customView.findViewById<View>(R.id.btn_save)?.setOnClickListener {
            settings.NAUTICAL_RUDDER_GAIN.set(sliderRudderGain.value)
            settings.NAUTICAL_COUNTER_RUDDER.set(sliderCounterRudder.value)
            settings.NAUTICAL_AUTO_TRIM.set(sliderAutoTrim.value)
            settings.NAUTICAL_FILTER_SENSITIVITY.set(sliderFilterSensitivity.value)
            settings.NAUTICAL_RUDDER_LIMIT.set(sliderRudderLimit.value)
            settings.NAUTICAL_OFF_COURSE_ALARM.set(sliderOffCourse.value)
            settings.NAUTICAL_XTE_THRESHOLD.set(sliderXteThreshold.value)
            settings.NAUTICAL_KEEL_OFFSET.set(sliderKeelOffset.value)
            settings.NAUTICAL_WIND_ALIGNMENT.set(sliderWindAlignment.value)
            settings.NAUTICAL_PILOT_SEA_STATE.set(sliderSeaState.value.toInt())
            settings.NAUTICAL_PILOT_AUTO_SEA_STATE.set(switchAutoSeaState.isChecked)
            settings.NAUTICAL_WAVE_BIAS_SENSITIVITY.set(sliderWaveBias.value.toInt())
            settings.NAUTICAL_ACTUATOR_ALARM_THRESHOLD.set(sliderActuatorThreshold.value)

            NauticalPlugin.engine?.setAutoSeaStateEnabled(switchAutoSeaState.isChecked)
            autopilot.setSeaState(sliderSeaState.value.toInt())
            autopilot.pushAllSettings()
            dismissAllowingStateLoss()
        }

        // Cancel Button
        customView.findViewById<View>(R.id.btn_cancel)?.setOnClickListener { dismissAllowingStateLoss() }

        // Reset Defaults Button
        customView.findViewById<View>(R.id.btn_reset_defaults)?.setOnClickListener {
            sliderRudderGain.value = (settings.NAUTICAL_RUDDER_GAIN.defaultValue as Float)
            txtValueRudderGain.text = String.format(Locale.US, "%.1f", sliderRudderGain.value)

            sliderCounterRudder.value = (settings.NAUTICAL_COUNTER_RUDDER.defaultValue as Float)
            txtValueCounterRudder.text = String.format(Locale.US, "%.1f", sliderCounterRudder.value)

            sliderAutoTrim.value = (settings.NAUTICAL_AUTO_TRIM.defaultValue as Float)
            txtValueAutoTrim.text = String.format(Locale.US, "%.2f", sliderAutoTrim.value)

            sliderFilterSensitivity.value = (settings.NAUTICAL_FILTER_SENSITIVITY.defaultValue as Float)
            txtValueFilterSensitivity.text = sliderFilterSensitivity.value.toInt().toString()

            sliderRudderLimit.value = (settings.NAUTICAL_RUDDER_LIMIT.defaultValue as Float)
            txtValueRudderLimit.text = "${sliderRudderLimit.value.toInt()}°"

            sliderOffCourse.value = (settings.NAUTICAL_OFF_COURSE_ALARM.defaultValue as Float)
            txtValueOffCourse.text = "${sliderOffCourse.value.toInt()}°"

            sliderXteThreshold.value = (settings.NAUTICAL_XTE_THRESHOLD.defaultValue as Float)
            val defaultXteMeters = SignalKUnitConverter.nmToMeters(sliderXteThreshold.value.toDouble())
            val (xteV, xteU) = SignalKUnitConverter.formatValue(app, settings, defaultXteMeters, "distance")
            txtXteThresholdValue.text = "$xteV $xteU"

            sliderActuatorThreshold.value = (settings.NAUTICAL_ACTUATOR_ALARM_THRESHOLD.defaultValue as Float)
            txtValueActuatorThreshold.text = "${sliderActuatorThreshold.value.toInt()}%"

            sliderKeelOffset.value = (settings.NAUTICAL_KEEL_OFFSET.defaultValue as Float)
            val (keelV, keelU) = SignalKUnitConverter.formatValue(app, settings, sliderKeelOffset.value.toDouble(), "depth")
            txtKeelOffsetValue.text = "$keelV $keelU"

            sliderWindAlignment.value = (settings.NAUTICAL_WIND_ALIGNMENT.defaultValue as Float)
            val (windV, windU) = SignalKUnitConverter.formatValue(app, settings, Math.toRadians(sliderWindAlignment.value.toDouble()), "angle")
            txtWindAlignmentValue.text = "$windV$windU"

            sliderSeaState.value = (settings.NAUTICAL_PILOT_SEA_STATE.defaultValue as Number).toFloat()
            txtSeaStateValue.text = sliderSeaState.value.toInt().toString()

            sliderWaveBias.value = (settings.NAUTICAL_WAVE_BIAS_SENSITIVITY.defaultValue as Number).toFloat()
            txtValueWaveBias.text = "${sliderWaveBias.value.toInt()}%"

            switchAutoSeaState.isChecked = false
            sliderSeaState.isEnabled = true
            sliderSeaState.alpha = 1.0f

            updateChart()
        }

        items.add(BaseBottomSheetItem.Builder().setCustomView(customView).create())
    }

    override fun onStart() {
        super.onStart()
        (dialog as? com.google.android.material.bottomsheet.BottomSheetDialog)?.let { sheetDialog ->
            sheetDialog.setCanceledOnTouchOutside(true)
            sheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.let { bottomSheet ->
                val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheet)
                behavior.isHideable = true
                behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
            }
        }
    }

    private fun updatePreviewChart(chart: LineChart, p: Double, d: Double, i: Double, limitDegrees: Double = 30.0) {
        val entriesHeading = mutableListOf<Entry>()
        val entriesRudder = mutableListOf<Entry>()
        var currentHeading = 0.0
        val targetHeading = 10.0
        var rudderAngle: Double
        var integral = 0.0
        var lastError = targetHeading
        val dt = 0.1

        for (step in 0 until 100) {
            val error = targetHeading - currentHeading
            integral += error * dt
            val derivative = (error - lastError) / dt
            rudderAngle = (p * error) + (i * integral) + (d * derivative)
            rudderAngle = rudderAngle.coerceIn(-limitDegrees, limitDegrees)
            currentHeading += rudderAngle * 0.05 * dt
            entriesHeading.add(Entry(step.toFloat(), currentHeading.toFloat()))
            entriesRudder.add(Entry(step.toFloat(), rudderAngle.toFloat()))
            lastError = error
        }

        val primaryColor = ContextCompat.getColor(requireContext(), if (nightMode) R.color.nautical_status_blue else R.color.color_ok)
        val secondaryColor = ContextCompat.getColor(requireContext(), R.color.nautical_status_red)
        val textColor = if (nightMode) Color.WHITE else Color.BLACK

        val dataSetHeading = LineDataSet(entriesHeading, "Heading Error").apply {
            color = primaryColor
            setDrawCircles(false)
            lineWidth = 2f
        }
        val dataSetRudder = LineDataSet(entriesRudder, "Rudder Angle").apply {
            color = secondaryColor
            setDrawCircles(false)
            lineWidth = 1f
            enableDashedLine(10f, 10f, 0f)
        }

        chart.data = LineData(dataSetHeading, dataSetRudder)
        chart.description.isEnabled = false
        chart.xAxis.isEnabled = false
        chart.axisRight.isEnabled = false
        chart.axisLeft.textColor = textColor
        chart.legend.textColor = textColor
        chart.invalidate()
    }
}
