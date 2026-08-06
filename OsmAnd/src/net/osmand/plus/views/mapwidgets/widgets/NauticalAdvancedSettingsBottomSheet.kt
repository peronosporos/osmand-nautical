package net.osmand.plus.views.mapwidgets.widgets

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import android.widget.Button
import android.widget.ProgressBar
import net.osmand.plus.base.BottomSheetDialogFragment
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.ui.NauticalTouchGuard
import net.osmand.plus.settings.enums.VesselType

class NauticalAdvancedSettingsBottomSheet : BottomSheetDialogFragment() {

    companion object {
        fun newInstance() = NauticalAdvancedSettingsBottomSheet()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.bottom_sheet_nautical_advanced, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val autopilot = NauticalPlugin.autopilot ?: return
        val plugin = NauticalPlugin.getInstance() ?: return
        
        plugin.applyNightVisionFilter(view)

        val app = (activity?.application as? net.osmand.plus.OsmandApplication) ?: return
        val arbitrator = net.osmand.plus.plugins.nautical.engine.NauticalHelmArbitrator.getInstance(app)
        val isLocked = arbitrator.isLockedByEmergency()

        val safetyLock = view.findViewById<SwitchMaterial>(R.id.safety_lock)
        val settingsContainer = view.findViewById<ViewGroup>(R.id.settings_container)

        if (isLocked) {
            safetyLock.isChecked = true
            safetyLock.isEnabled = false
            safetyLock.text = getString(R.string.nautical_helm_locked_by, arbitrator.getActiveManeuver() ?: "")
            safetyLock.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_color_negative))
            settingsContainer.alpha = 0.5f
            setEnabledRecursive(settingsContainer, enabled = false)
        }

        val sliderRudderGain = view.findViewById<Slider>(R.id.slider_rudder_gain)
        val sliderCounterRudder = view.findViewById<Slider>(R.id.slider_counter_rudder)
        val sliderAutoTrim = view.findViewById<Slider>(R.id.slider_auto_trim)
        val sliderFilterSensitivity = view.findViewById<Slider>(R.id.slider_filter_sensitivity)
        val sliderRudderLimit = view.findViewById<Slider>(R.id.slider_rudder_limit)
        val sliderOffCourse = view.findViewById<Slider>(R.id.slider_off_course)
        val sliderXteThreshold = view.findViewById<Slider>(R.id.slider_xte_threshold)
        val sliderKeelOffset = view.findViewById<Slider>(R.id.slider_keel_offset)
        val txtKeelOffsetValue = view.findViewById<android.widget.TextView>(R.id.txt_value_keel_offset)
        val sliderWindAlignment = view.findViewById<Slider>(R.id.slider_wind_alignment)
        val txtWindAlignmentValue = view.findViewById<android.widget.TextView>(R.id.txt_value_wind_alignment)
        val vesselTypeToggle = view.findViewById<com.google.android.material.button.MaterialButtonToggleGroup>(R.id.vessel_type_toggle)

        val sliderSeaState = view.findViewById<Slider>(R.id.slider_sea_state)
        val txtSeaStateValue = view.findViewById<android.widget.TextView>(R.id.txt_value_sea_state)
        val switchAutoSeaState = view.findViewById<SwitchMaterial>(R.id.switch_auto_sea_state)
        val sliderWaveBias = view.findViewById<Slider>(R.id.slider_wave_bias)
        val sliderActuatorThreshold = view.findViewById<Slider>(R.id.slider_actuator_threshold)

        val tabLayout = view.findViewById<TabLayout>(R.id.tab_layout)
        val containerTuning = view.findViewById<View>(R.id.container_tuning)
        val containerLimits = view.findViewById<View>(R.id.container_limits)
        val containerVessel = view.findViewById<View>(R.id.container_vessel)
        val containerEnv = view.findViewById<View>(R.id.container_env)
        val containerPypilot = view.findViewById<View>(R.id.container_pypilot)

        tabLayout.addTab(tabLayout.newTab().setText("Tuning"))
        tabLayout.addTab(tabLayout.newTab().setText("Limits"))
        tabLayout.addTab(tabLayout.newTab().setText("Vessel"))
        tabLayout.addTab(tabLayout.newTab().setText("Env"))

        val hasPypilot = NauticalPlugin.getInstance()?.capabilityManager?.capabilities?.value?.hasAutopilot == true // More specific check would be better
        if (hasPypilot) {
            tabLayout.addTab(tabLayout.newTab().setText("Pypilot"))
        }

        tabLayout.addOnTabSelectedListener(
            object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab?) {
                    containerTuning.visibility = if (tab?.position == 0) View.VISIBLE else View.GONE
                    containerLimits.visibility = if (tab?.position == 1) View.VISIBLE else View.GONE
                    containerVessel.visibility = if (tab?.position == 2) View.VISIBLE else View.GONE
                    containerEnv.visibility = if (tab?.position == 3) View.VISIBLE else View.GONE
                    containerPypilot.visibility = if (tab?.position == 4) View.VISIBLE else View.GONE
                }

                override fun onTabUnselected(tab: TabLayout.Tab?) {}
                override fun onTabReselected(tab: TabLayout.Tab?) {}
            },
        )

        // Pypilot UI Bindings
        val sliderP = view.findViewById<Slider>(R.id.slider_p)
        val sliderI = view.findViewById<Slider>(R.id.slider_i)
        val sliderD = view.findViewById<Slider>(R.id.slider_d)
        val sliderDD = view.findViewById<Slider>(R.id.slider_dd)
        val sliderPR = view.findViewById<Slider>(R.id.slider_pr)
        val sliderFF = view.findViewById<Slider>(R.id.slider_ff)
        val sliderWG = view.findViewById<Slider>(R.id.slider_wg)
        val sliderDeadzone = view.findViewById<Slider>(R.id.slider_deadzone)
        
        val progressCompass = view.findViewById<ProgressBar>(R.id.progress_compass)
        val progressRudder = view.findViewById<ProgressBar>(R.id.progress_rudder)
        val btnCalibrateCompass = view.findViewById<Button>(R.id.btn_calibrate_compass)
        val btnCalibrateRudder = view.findViewById<Button>(R.id.btn_calibrate_rudder)

        listOf<Slider>(sliderP, sliderI, sliderD, sliderDD, sliderPR, sliderFF, sliderWG, sliderDeadzone).forEach { 
            NauticalTouchGuard.apply(it, safetyLock) 
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val engineInternal = NauticalPlugin.engine
            engineInternal?.marineStateFlow?.collectLatest { state ->
                state.pypilotConfig?.let { config ->
                    fun updateSlider(slider: Slider, v: Double?) {
                        if (v != null && !slider.isFocused) {
                            slider.value = v.toFloat().coerceIn(slider.valueFrom, slider.valueTo)
                        }
                    }
                    updateSlider(sliderP, config.p)
                    updateSlider(sliderI, config.i)
                    updateSlider(sliderD, config.d)
                    updateSlider(sliderDD, config.dd)
                    updateSlider(sliderPR, config.pr)
                    updateSlider(sliderFF, config.ff)
                    updateSlider(sliderWG, config.wg)
                    updateSlider(sliderDeadzone, config.deadzone)
                }
                state.pypilotCalibration?.let { cal ->
                    progressCompass.progress = (cal.compassCalibrationProgress ?: 0.0).toInt()
                    progressRudder.progress = (cal.rudderCalibrationProgress ?: 0.0).toInt()
                }
            }
        }

        sliderP.addOnChangeListener { _, value, fromUser -> if (fromUser) autopilot.setPypilotGain("p", value.toDouble()) }
        sliderI.addOnChangeListener { _, value, fromUser -> if (fromUser) autopilot.setPypilotGain("i", value.toDouble()) }
        sliderD.addOnChangeListener { _, value, fromUser -> if (fromUser) autopilot.setPypilotGain("d", value.toDouble()) }
        sliderDD.addOnChangeListener { _, value, fromUser -> if (fromUser) autopilot.setPypilotGain("dd", value.toDouble()) }
        sliderPR.addOnChangeListener { _, value, fromUser -> if (fromUser) autopilot.setPypilotGain("pr", value.toDouble()) }
        sliderFF.addOnChangeListener { _, value, fromUser -> if (fromUser) autopilot.setPypilotGain("ff", value.toDouble()) }
        sliderWG.addOnChangeListener { _, value, fromUser -> if (fromUser) autopilot.setPypilotGain("wg", value.toDouble()) }
        sliderDeadzone.addOnChangeListener { _, value, fromUser -> if (fromUser) autopilot.setPypilotGain("deadzone", value.toDouble()) }

        btnCalibrateCompass.setOnClickListener { autopilot.startPypilotCalibration("compass") }
        btnCalibrateRudder.setOnClickListener { autopilot.startPypilotCalibration("rudder") }
        
        view.findViewById<Button>(R.id.btn_stop_compass_calib)?.setOnClickListener { autopilot.stopPypilotCalibration("compass") }
        view.findViewById<Button>(R.id.btn_stop_rudder_calib)?.setOnClickListener { autopilot.stopPypilotCalibration("rudder") }

        view.findViewById<Button>(R.id.btn_select_pypilot_profile)?.setOnClickListener {
            val profiles = arrayOf("Default", "Slow", "Heavy", "Racing")
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.nautical_pypilot_profile)
                .setItems(profiles) { _, which ->
                    autopilot.setPypilotProfile(profiles[which])
                }
                .show()
        }
        
        view.findViewById<Button>(R.id.btn_stop_compass_calib)?.setOnClickListener { autopilot.stopPypilotCalibration("compass") }
        view.findViewById<Button>(R.id.btn_stop_rudder_calib)?.setOnClickListener { autopilot.stopPypilotCalibration("rudder") }

        view.findViewById<Button>(R.id.btn_select_pypilot_profile)?.setOnClickListener {
            val profiles = arrayOf("Default", "Slow", "Heavy", "Racing")
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.nautical_pypilot_profile)
                .setItems(profiles) { _, which ->
                    autopilot.setPypilotProfile(profiles[which])
                }
                .show()
        }

        val chart = view.findViewById<LineChart>(R.id.pid_preview_chart)

        sliderRudderGain.value = settings.NAUTICAL_RUDDER_GAIN.get().coerceIn(sliderRudderGain.valueFrom, sliderRudderGain.valueTo)
        sliderCounterRudder.value = settings.NAUTICAL_COUNTER_RUDDER.get().coerceIn(sliderCounterRudder.valueFrom, sliderCounterRudder.valueTo)
        sliderAutoTrim.value = settings.NAUTICAL_AUTO_TRIM.get().coerceIn(sliderAutoTrim.valueFrom, sliderAutoTrim.valueTo)
        
        val currentSeaState = settings.NAUTICAL_PILOT_SEA_STATE.get() ?: 3
        sliderSeaState.value = currentSeaState.toFloat().coerceIn(sliderSeaState.valueFrom, sliderSeaState.valueTo)
        txtSeaStateValue.text = currentSeaState.toString()
        switchAutoSeaState.isChecked = NauticalPlugin.engine?.getCurrentState()?.isAutoSeaStateEnabled ?: false
        sliderSeaState.isEnabled = !switchAutoSeaState.isChecked
        sliderSeaState.alpha = if (switchAutoSeaState.isChecked) 0.5f else 1.0f

        sliderFilterSensitivity.value = settings.NAUTICAL_FILTER_SENSITIVITY.get().coerceIn(sliderFilterSensitivity.valueFrom, sliderFilterSensitivity.valueTo)
        sliderRudderLimit.value = settings.NAUTICAL_RUDDER_LIMIT.get().coerceIn(sliderRudderLimit.valueFrom, sliderRudderLimit.valueTo)
        sliderOffCourse.value = settings.NAUTICAL_OFF_COURSE_ALARM.get().coerceIn(sliderOffCourse.valueFrom, sliderOffCourse.valueTo)
        
        sliderKeelOffset.value = settings.NAUTICAL_KEEL_OFFSET.get().coerceIn(sliderKeelOffset.valueFrom, sliderKeelOffset.valueTo)
        txtKeelOffsetValue.text = getString(R.string.nautical_format_meters, String.format(java.util.Locale.US, "%.1f", sliderKeelOffset.value))
        sliderWindAlignment.value = settings.NAUTICAL_WIND_ALIGNMENT.get().coerceIn(sliderWindAlignment.valueFrom, sliderWindAlignment.valueTo)
        txtWindAlignmentValue.text = getString(R.string.nautical_format_deg, sliderWindAlignment.value.toInt().toString())

        sliderWaveBias.value = settings.NAUTICAL_WAVE_BIAS_SENSITIVITY.get().toFloat().coerceIn(sliderWaveBias.valueFrom, sliderWaveBias.valueTo)
        sliderActuatorThreshold.value = settings.NAUTICAL_ACTUATOR_ALARM_THRESHOLD.get().coerceIn(sliderActuatorThreshold.valueFrom, sliderActuatorThreshold.valueTo)

        val nm = settings.NAUTICAL_XTE_THRESHOLD.get().toDouble()
        sliderXteThreshold.value = nm.toFloat().coerceIn(0.01f, 1.0f)
        val res = net.osmand.plus.plugins.nautical.engine.SignalKUnitConverter.formatValue(app, settings, nm * 1852.0, "distance")
        view.findViewById<android.widget.TextView>(R.id.txt_value_xte_threshold)?.text = getString(R.string.nautical_format_nm, res.first)
        
        vesselTypeToggle.check(if (settings.NAUTICAL_VESSEL_TYPE.get() == VesselType.PROA) R.id.btn_vessel_proa else R.id.btn_vessel_conv)

        // Apply Touch Guards to all sliders
        val sliders = listOf(
            sliderRudderGain, sliderCounterRudder, sliderAutoTrim,
            sliderFilterSensitivity, sliderRudderLimit, sliderOffCourse,
            sliderXteThreshold, sliderSeaState, sliderKeelOffset, sliderWindAlignment,
            sliderWaveBias, sliderActuatorThreshold
        )
        sliders.forEach { slider ->
            NauticalTouchGuard.apply(slider, safetyLock)
        }

        val btnReset = view.findViewById<Button>(R.id.btn_reset_defaults)
        val btnSave = view.findViewById<Button>(R.id.btn_save)
        val btnCancel = view.findViewById<Button>(R.id.btn_cancel)

        safetyLock.setOnCheckedChangeListener { _, isChecked ->
            val alpha = if (isChecked) 0.5f else 1.0f
            settingsContainer.alpha = alpha
            setEnabledRecursive(settingsContainer, !isChecked)
            
            if (!isChecked) {
                app.showToastMessage(R.string.nautical_advanced_settings_unlocked)
            }
        }

        sliderSeaState.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                txtSeaStateValue.text = value.toInt().toString()
            }
        }

        sliderXteThreshold.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val res = net.osmand.plus.plugins.nautical.engine.SignalKUnitConverter.formatValue(app, settings, value.toDouble() * 1852.0, "distance")
                view.findViewById<android.widget.TextView>(R.id.txt_value_xte_threshold)?.text = getString(R.string.nautical_format_nm, res.first)
            }
        }

        switchAutoSeaState.setOnCheckedChangeListener { _, isChecked ->
            sliderSeaState.isEnabled = !isChecked
            sliderSeaState.alpha = if (isChecked) 0.5f else 1.0f
            NauticalPlugin.engine?.setAutoSeaStateEnabled(isChecked)
        }

        sliderKeelOffset.addOnChangeListener { _, value, _ ->
            txtKeelOffsetValue.text = getString(R.string.nautical_format_meters, String.format(java.util.Locale.US, "%.1f", value))
        }
        sliderWindAlignment.addOnChangeListener { _, value, _ ->
            txtWindAlignmentValue.text = getString(R.string.nautical_format_deg, value.toInt().toString())
        }

        val updateChart = {
            updatePreviewChart(
                chart,
                sliderRudderGain.value.toDouble(),
                sliderCounterRudder.value.toDouble(),
                sliderAutoTrim.value.toDouble(),
            )
        }

        sliderRudderGain.addOnChangeListener { _, _, _ -> updateChart() }
        sliderCounterRudder.addOnChangeListener { _, _, _ -> updateChart() }
        sliderAutoTrim.addOnChangeListener { _, _, _ -> updateChart() }

        // Initial chart
        updateChart()

        btnReset.setOnClickListener {
            sliderRudderGain.value = settings.NAUTICAL_RUDDER_GAIN.defaultValue
            sliderCounterRudder.value = settings.NAUTICAL_COUNTER_RUDDER.defaultValue
            sliderAutoTrim.value = settings.NAUTICAL_AUTO_TRIM.defaultValue
            sliderFilterSensitivity.value = settings.NAUTICAL_FILTER_SENSITIVITY.defaultValue
            sliderRudderLimit.value = settings.NAUTICAL_RUDDER_LIMIT.defaultValue
            sliderOffCourse.value = settings.NAUTICAL_OFF_COURSE_ALARM.defaultValue
            sliderXteThreshold.value = settings.NAUTICAL_XTE_THRESHOLD.defaultValue
            
            sliderKeelOffset.value = settings.NAUTICAL_KEEL_OFFSET.defaultValue
            txtKeelOffsetValue.text = getString(R.string.nautical_format_meters, String.format(java.util.Locale.US, "%.1f", sliderKeelOffset.value))
            sliderWindAlignment.value = settings.NAUTICAL_WIND_ALIGNMENT.defaultValue
            txtWindAlignmentValue.text = getString(R.string.nautical_format_deg, sliderWindAlignment.value.toInt().toString())

            sliderSeaState.value = settings.NAUTICAL_PILOT_SEA_STATE.defaultValue.toFloat()
            txtSeaStateValue.text = settings.NAUTICAL_PILOT_SEA_STATE.defaultValue.toString()
            switchAutoSeaState.isChecked = false
            NauticalPlugin.engine?.setAutoSeaStateEnabled(false)

            sliderWaveBias.value = settings.NAUTICAL_WAVE_BIAS_SENSITIVITY.defaultValue.toFloat()
            sliderActuatorThreshold.value = settings.NAUTICAL_ACTUATOR_ALARM_THRESHOLD.defaultValue

            vesselTypeToggle.check(R.id.btn_vessel_conv)
            updateChart()
        }

        btnSave.setOnClickListener {
            settings.NAUTICAL_RUDDER_GAIN.set(sliderRudderGain.value)
            settings.NAUTICAL_COUNTER_RUDDER.set(sliderCounterRudder.value)
            settings.NAUTICAL_AUTO_TRIM.set(sliderAutoTrim.value)
            settings.NAUTICAL_FILTER_SENSITIVITY.set(sliderFilterSensitivity.value)
            settings.NAUTICAL_RUDDER_LIMIT.set(sliderRudderLimit.value)
            settings.NAUTICAL_OFF_COURSE_ALARM.set(sliderOffCourse.value)
            settings.NAUTICAL_XTE_THRESHOLD.set(sliderXteThreshold.value)
            settings.NAUTICAL_KEEL_OFFSET.set(sliderKeelOffset.value)
            settings.NAUTICAL_WIND_ALIGNMENT.set(sliderWindAlignment.value)
            settings.NAUTICAL_WAVE_BIAS_SENSITIVITY.set(sliderWaveBias.value.toInt())
            settings.NAUTICAL_ACTUATOR_ALARM_THRESHOLD.set(sliderActuatorThreshold.value)

            val seaState = sliderSeaState.value.toInt()
            settings.NAUTICAL_PILOT_SEA_STATE.set(seaState)
            autopilot.setSeaState(seaState)

            autopilot.setRudderGain(sliderRudderGain.value.toDouble())
            autopilot.setCounterRudder(sliderCounterRudder.value.toDouble())
            autopilot.setAutoTrim(sliderAutoTrim.value.toDouble())
            autopilot.setFilterSensitivity(sliderFilterSensitivity.value.toDouble())
            autopilot.setRudderLimit(sliderRudderLimit.value.toDouble())
            autopilot.setOffCourseAlarm(sliderOffCourse.value.toDouble())
            
            val selectedVesselType = if (vesselTypeToggle.checkedButtonId == R.id.btn_vessel_proa) VesselType.PROA else VesselType.CONVENTIONAL
            settings.NAUTICAL_VESSEL_TYPE.set(selectedVesselType)

            dismissAllowingStateLoss()
        }

        btnCancel.setOnClickListener {
            dismissAllowingStateLoss()
        }
    }

    private fun setEnabledRecursive(viewGroup: ViewGroup, enabled: Boolean) {
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            child.isEnabled = enabled
            (child as? ViewGroup)?.let { setEnabledRecursive(it, enabled) }
        }
    }

    private fun updatePreviewChart(chart: LineChart, p: Double, d: Double, i: Double) {
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

            // PID for Rudder
            rudderAngle = (p * error) + (i * integral) + (d * derivative)
            // Limit rudder
            rudderAngle = rudderAngle.coerceIn(-35.0, 35.0)

            // Simple vessel physics: heading rate proportional to rudder
            currentHeading += rudderAngle * 0.05 * dt

            entriesHeading.add(Entry(step.toFloat(), currentHeading.toFloat()))
            entriesRudder.add(Entry(step.toFloat(), rudderAngle.toFloat()))

            lastError = error
        }

        val dataSetHeading = LineDataSet(entriesHeading, "Heading Error").apply {
            color = ContextCompat.getColor(requireContext(), R.color.nautical_status_blue)
            setDrawCircles(false)
            lineWidth = 2f
        }
        val dataSetRudder = LineDataSet(entriesRudder, "Rudder Angle").apply {
            color = ContextCompat.getColor(requireContext(), R.color.nautical_status_red)
            setDrawCircles(false)
            lineWidth = 1f
            enableDashedLine(10f, 10f, 0f)
        }

        chart.data = LineData(dataSetHeading, dataSetRudder)
        chart.description.isEnabled = false
        chart.xAxis.isEnabled = false
        chart.axisRight.isEnabled = false
        chart.legend.textColor = if (nightMode) Color.LTGRAY else Color.GRAY
        chart.invalidate()
    }
}
