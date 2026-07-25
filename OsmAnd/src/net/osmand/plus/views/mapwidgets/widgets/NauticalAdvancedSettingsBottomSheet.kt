package net.osmand.plus.views.mapwidgets.widgets

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import net.osmand.plus.base.BottomSheetDialogFragment
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.NauticalPlugin
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

        val safetyLock = view.findViewById<SwitchMaterial>(R.id.safety_lock)
        val settingsContainer = view.findViewById<ViewGroup>(R.id.settings_container)

        val sliderRudderGain = view.findViewById<Slider>(R.id.slider_rudder_gain)
        val sliderCounterRudder = view.findViewById<Slider>(R.id.slider_counter_rudder)
        val sliderAutoTrim = view.findViewById<Slider>(R.id.slider_auto_trim)
        val sliderFilterSensitivity = view.findViewById<Slider>(R.id.slider_filter_sensitivity)
        val sliderRudderLimit = view.findViewById<Slider>(R.id.slider_rudder_limit)
        val sliderOffCourse = view.findViewById<Slider>(R.id.slider_off_course)
        val sliderXteThreshold = view.findViewById<Slider>(R.id.slider_xte_threshold)
        val vesselTypeToggle = view.findViewById<com.google.android.material.button.MaterialButtonToggleGroup>(R.id.vessel_type_toggle)

        val chart = view.findViewById<LineChart>(R.id.pid_preview_chart)

        sliderRudderGain.value = settings.NAUTICAL_RUDDER_GAIN.get().coerceIn(sliderRudderGain.valueFrom, sliderRudderGain.valueTo)
        sliderCounterRudder.value = settings.NAUTICAL_COUNTER_RUDDER.get().coerceIn(sliderCounterRudder.valueFrom, sliderCounterRudder.valueTo)
        sliderAutoTrim.value = settings.NAUTICAL_AUTO_TRIM.get().coerceIn(sliderAutoTrim.valueFrom, sliderAutoTrim.valueTo)
        sliderFilterSensitivity.value = settings.NAUTICAL_FILTER_SENSITIVITY.get().coerceIn(sliderFilterSensitivity.valueFrom, sliderFilterSensitivity.valueTo)
        sliderRudderLimit.value = settings.NAUTICAL_RUDDER_LIMIT.get().coerceIn(sliderRudderLimit.valueFrom, sliderRudderLimit.valueTo)
        sliderOffCourse.value = settings.NAUTICAL_OFF_COURSE_ALARM.get().coerceIn(sliderOffCourse.valueFrom, sliderOffCourse.valueTo)
        sliderXteThreshold.value = settings.NAUTICAL_XTE_THRESHOLD.get().coerceIn(0.01f, 1.0f)
        
        vesselTypeToggle.check(if (settings.NAUTICAL_VESSEL_TYPE.get() == VesselType.PROA) R.id.btn_vessel_proa else R.id.btn_vessel_conv)

        val btnCompassCalib = view.findViewById<Button>(R.id.btn_compass_calib)
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

        btnCompassCalib.setOnClickListener {
            showCompassWizard()
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

    private fun showCompassWizard() {
        NauticalCompassWizardDialog.show(this)
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
