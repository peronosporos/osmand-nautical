package net.osmand.plus.plugins.nautical.ui.widgets

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
import net.osmand.plus.R
import net.osmand.plus.base.bottomsheetmenu.BaseBottomSheetItem
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.SignalKUnitConverter
import net.osmand.plus.plugins.nautical.ui.NauticalTouchGuard
import net.osmand.plus.settings.enums.VesselType

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

        val sliderRudderGain = customView.findViewById<Slider>(R.id.slider_rudder_gain)
        val sliderCounterRudder = customView.findViewById<Slider>(R.id.slider_counter_rudder)
        val sliderAutoTrim = customView.findViewById<Slider>(R.id.slider_auto_trim)
        val sliderFilterSensitivity = customView.findViewById<Slider>(R.id.slider_filter_sensitivity)
        val sliderRudderLimit = customView.findViewById<Slider>(R.id.slider_rudder_limit)
        val sliderOffCourse = customView.findViewById<Slider>(R.id.slider_off_course)
        val sliderXteThreshold = customView.findViewById<Slider>(R.id.slider_xte_threshold)
        val sliderKeelOffset = customView.findViewById<Slider>(R.id.slider_keel_offset)
        val txtKeelOffsetValue = customView.findViewById<android.widget.TextView>(R.id.txt_value_keel_offset)
        val sliderWindAlignment = customView.findViewById<Slider>(R.id.slider_wind_alignment)
        val txtWindAlignmentValue = customView.findViewById<android.widget.TextView>(R.id.txt_value_wind_alignment)
        val txtXteThresholdValue = customView.findViewById<android.widget.TextView>(R.id.txt_value_xte_threshold)

        val sliderSeaState = customView.findViewById<Slider>(R.id.slider_sea_state)
        val txtSeaStateValue = customView.findViewById<android.widget.TextView>(R.id.txt_value_sea_state)
        val switchAutoSeaState = customView.findViewById<SwitchMaterial>(R.id.switch_auto_sea_state)
        val sliderWaveBias = customView.findViewById<Slider>(R.id.slider_wave_bias)
        val sliderActuatorThreshold = customView.findViewById<Slider>(R.id.slider_actuator_threshold)

        val tabLayout = customView.findViewById<TabLayout>(R.id.tab_layout)
        val containerTuning = customView.findViewById<View>(R.id.container_tuning)
        val containerLimits = customView.findViewById<View>(R.id.container_limits)
        val containerVessel = customView.findViewById<View>(R.id.container_vessel)
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

        val sliderP = customView.findViewById<Slider>(R.id.slider_p)
        val sliderI = customView.findViewById<Slider>(R.id.slider_i)
        val sliderD = customView.findViewById<Slider>(R.id.slider_d)
        val sliderDD = customView.findViewById<Slider>(R.id.slider_dd)
        val sliderPR = customView.findViewById<Slider>(R.id.slider_pr)
        val sliderFF = customView.findViewById<Slider>(R.id.slider_ff)
        val sliderWG = customView.findViewById<Slider>(R.id.slider_wg)
        val sliderDeadzone = customView.findViewById<Slider>(R.id.slider_deadzone)
        
        val progressCompass = customView.findViewById<ProgressBar>(R.id.progress_compass)
        val progressRudder = customView.findViewById<ProgressBar>(R.id.progress_rudder)
        val btnCalibrateCompass = customView.findViewById<Button>(R.id.btn_calibrate_compass)
        val btnCalibrateRudder = customView.findViewById<Button>(R.id.btn_calibrate_rudder)

        // Apply Touch Guards to ALL interactive elements, but with the safety lock logic
        val allSliders = listOf(
            sliderRudderGain, sliderCounterRudder, sliderAutoTrim, sliderFilterSensitivity,
            sliderRudderLimit, sliderOffCourse, sliderXteThreshold, sliderSeaState,
            sliderKeelOffset, sliderWindAlignment, sliderWaveBias, sliderActuatorThreshold,
            sliderP, sliderI, sliderD, sliderDD, sliderPR, sliderFF, sliderWG, sliderDeadzone
        )
        allSliders.forEach { NauticalTouchGuard.apply(it, safetyLock) }

        viewLifecycleOwner.lifecycleScope.launch {
            NauticalPlugin.engine?.marineStateFlow?.collectLatest { state ->
                state.pypilotConfig?.let { config ->
                    fun updateSlider(slider: Slider, v: Double?) {
                        if (v != null && !slider.isFocused) slider.value = v.toFloat().coerceIn(slider.valueFrom, slider.valueTo)
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

        val chart = customView.findViewById<LineChart>(R.id.pid_preview_chart)

        // Init values from settings
        sliderRudderGain.value = (settings.NAUTICAL_RUDDER_GAIN.get() as Float).coerceIn(sliderRudderGain.valueFrom, sliderRudderGain.valueTo)
        sliderCounterRudder.value = (settings.NAUTICAL_COUNTER_RUDDER.get() as Float).coerceIn(sliderCounterRudder.valueFrom, sliderCounterRudder.valueTo)
        sliderAutoTrim.value = (settings.NAUTICAL_AUTO_TRIM.get() as Float).coerceIn(sliderAutoTrim.valueFrom, sliderAutoTrim.valueTo)
        sliderFilterSensitivity.value = (settings.NAUTICAL_FILTER_SENSITIVITY.get() as Float).coerceIn(sliderFilterSensitivity.valueFrom, sliderFilterSensitivity.valueTo)
        sliderRudderLimit.value = (settings.NAUTICAL_RUDDER_LIMIT.get() as Float).coerceIn(sliderRudderLimit.valueFrom, sliderRudderLimit.valueTo)
        sliderOffCourse.value = (settings.NAUTICAL_OFF_COURSE_ALARM.get() as Float).coerceIn(sliderOffCourse.valueFrom, sliderOffCourse.valueTo)
        sliderKeelOffset.value = (settings.NAUTICAL_KEEL_OFFSET.get() as Float).coerceIn(sliderKeelOffset.valueFrom, sliderKeelOffset.valueTo)
        sliderWindAlignment.value = (settings.NAUTICAL_WIND_ALIGNMENT.get() as Float).coerceIn(sliderWindAlignment.valueFrom, sliderWindAlignment.valueTo)
        sliderSeaState.value = (settings.NAUTICAL_PILOT_SEA_STATE.get() ?: 3).toFloat().coerceIn(sliderSeaState.valueFrom, sliderSeaState.valueTo)
        sliderWaveBias.value = (settings.NAUTICAL_WAVE_BIAS_SENSITIVITY.get() as Number).toFloat().coerceIn(sliderWaveBias.valueFrom, sliderWaveBias.valueTo)
        sliderActuatorThreshold.value = (settings.NAUTICAL_ACTUATOR_ALARM_THRESHOLD.get() as Float).coerceIn(sliderActuatorThreshold.valueFrom, sliderActuatorThreshold.valueTo)
        sliderXteThreshold.value = (settings.NAUTICAL_XTE_THRESHOLD.get() as Float).coerceIn(sliderXteThreshold.valueFrom, sliderXteThreshold.valueTo)

        txtSeaStateValue.text = sliderSeaState.value.toInt().toString()
        
        val initialKeel = SignalKUnitConverter.formatValue(app, settings, sliderKeelOffset.value.toDouble(), "depth")
        txtKeelOffsetValue.text = "${initialKeel.first} ${initialKeel.second}"
        
        val initialWind = SignalKUnitConverter.formatValue(app, settings, Math.toRadians(sliderWindAlignment.value.toDouble()), "angle")
        txtWindAlignmentValue.text = "${initialWind.first}${initialWind.second}"

        val initialXte = SignalKUnitConverter.formatValue(app, settings, SignalKUnitConverter.nmToMeters(sliderXteThreshold.value.toDouble()), "distance")
        txtXteThresholdValue.text = "${initialXte.first} ${initialXte.second}"
        
        switchAutoSeaState.isChecked = settings.NAUTICAL_PILOT_AUTO_SEA_STATE.get()
        sliderSeaState.isEnabled = !switchAutoSeaState.isChecked
        sliderSeaState.alpha = if (switchAutoSeaState.isChecked) 0.5f else 1.0f

        sliderSeaState.addOnChangeListener { _, value, _ -> txtSeaStateValue.text = value.toInt().toString() }
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
        switchAutoSeaState.setOnCheckedChangeListener { _, isChecked ->
            sliderSeaState.isEnabled = !isChecked
            sliderSeaState.alpha = if (isChecked) 0.5f else 1.0f
        }

        val updateChart = {
            updatePreviewChart(chart, sliderRudderGain.value.toDouble(), sliderCounterRudder.value.toDouble(), sliderAutoTrim.value.toDouble())
        }

        sliderRudderGain.addOnChangeListener { _, value, fromUser -> 
            if (fromUser) autopilot.setRudderGain(value.toDouble())
            updateChart()
        }
        sliderCounterRudder.addOnChangeListener { _, value, fromUser -> 
            if (fromUser) autopilot.setCounterRudder(value.toDouble())
            updateChart()
        }
        sliderAutoTrim.addOnChangeListener { _, value, fromUser -> 
            if (fromUser) autopilot.setAutoTrim(value.toDouble())
            updateChart()
        }
        updateChart()

        customView.findViewById<Button>(R.id.btn_save).setOnClickListener {
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

        customView.findViewById<Button>(R.id.btn_cancel).setOnClickListener { dismissAllowingStateLoss() }
        customView.findViewById<Button>(R.id.btn_reset_defaults).setOnClickListener {
            sliderRudderGain.value = (settings.NAUTICAL_RUDDER_GAIN.defaultValue as Float)
            sliderCounterRudder.value = (settings.NAUTICAL_COUNTER_RUDDER.defaultValue as Float)
            sliderAutoTrim.value = (settings.NAUTICAL_AUTO_TRIM.defaultValue as Float)
            sliderFilterSensitivity.value = (settings.NAUTICAL_FILTER_SENSITIVITY.defaultValue as Float)
            sliderRudderLimit.value = (settings.NAUTICAL_RUDDER_LIMIT.defaultValue as Float)
            sliderOffCourse.value = (settings.NAUTICAL_OFF_COURSE_ALARM.defaultValue as Float)
            sliderXteThreshold.value = (settings.NAUTICAL_XTE_THRESHOLD.defaultValue as Float)
            sliderKeelOffset.value = (settings.NAUTICAL_KEEL_OFFSET.defaultValue as Float)
            sliderWindAlignment.value = (settings.NAUTICAL_WIND_ALIGNMENT.defaultValue as Float)
            sliderSeaState.value = (settings.NAUTICAL_PILOT_SEA_STATE.defaultValue as Number).toFloat()
            switchAutoSeaState.isChecked = false
            updateChart()
        }
        
        // Pypilot direct apply (usually pypilot gains are live-tuned)
        sliderP.addOnChangeListener { _, v, fromUser -> if (fromUser) autopilot.setPypilotGain("p", v.toDouble()) }
        sliderI.addOnChangeListener { _, v, fromUser -> if (fromUser) autopilot.setPypilotGain("i", v.toDouble()) }
        sliderD.addOnChangeListener { _, v, fromUser -> if (fromUser) autopilot.setPypilotGain("d", v.toDouble()) }
        sliderDD.addOnChangeListener { _, v, fromUser -> if (fromUser) autopilot.setPypilotGain("dd", v.toDouble()) }
        sliderPR.addOnChangeListener { _, v, fromUser -> if (fromUser) autopilot.setPypilotGain("pr", v.toDouble()) }
        sliderFF.addOnChangeListener { _, v, fromUser -> if (fromUser) autopilot.setPypilotGain("ff", v.toDouble()) }
        sliderWG.addOnChangeListener { _, v, fromUser -> if (fromUser) autopilot.setPypilotGain("wg", v.toDouble()) }
        sliderDeadzone.addOnChangeListener { _, v, fromUser -> if (fromUser) autopilot.setPypilotGain("deadzone", v.toDouble()) }

        btnCalibrateCompass.setOnClickListener { autopilot.startPypilotCalibration("compass") }
        btnCalibrateRudder.setOnClickListener { autopilot.startPypilotCalibration("rudder") }
        customView.findViewById<Button>(R.id.btn_stop_compass_calib)?.setOnClickListener { autopilot.stopPypilotCalibration("compass") }
        customView.findViewById<Button>(R.id.btn_stop_rudder_calib)?.setOnClickListener { autopilot.stopPypilotCalibration("rudder") }
        customView.findViewById<Button>(R.id.btn_select_pypilot_profile)?.setOnClickListener {
            val profiles = arrayOf("Default", "Slow", "Heavy", "Racing")
            androidx.appcompat.app.AlertDialog.Builder(requireContext()).setTitle(R.string.nautical_pypilot_profile)
                .setItems(profiles) { _, which -> autopilot.setPypilotProfile(profiles[which]) }.show()
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

    private fun updatePreviewChart(chart: LineChart, p: Double, d: Double, i: Double) {
        val entriesHeading = mutableListOf<Entry>()
        val entriesRudder = mutableListOf<Entry>()
        var currentHeading = 0.0
        val targetHeading = 10.0
        var rudderAngle: Double
        var integral = 0.0
        var lastError = targetHeading
        val dt = 0.1
        
        // ITEM 19: Use settings for rudder limit in preview chart
        val settings = (activity?.application as? net.osmand.plus.OsmandApplication)?.settings
        val limit = settings?.NAUTICAL_RUDDER_LIMIT?.get()?.toDouble() ?: 35.0

        for (step in 0 until 100) {
            val error = targetHeading - currentHeading
            integral += error * dt
            val derivative = (error - lastError) / dt
            rudderAngle = (p * error) + (i * integral) + (d * derivative)
            rudderAngle = rudderAngle.coerceIn(-limit, limit)
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
