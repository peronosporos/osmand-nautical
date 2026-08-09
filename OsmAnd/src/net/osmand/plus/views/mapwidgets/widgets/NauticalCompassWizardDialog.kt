package net.osmand.plus.views.mapwidgets.widgets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.progressindicator.CircularProgressIndicator
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import net.osmand.plus.R
import net.osmand.plus.base.BaseMaterialBottomSheetDialogFragment
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.utils.AndroidUtils

class NauticalCompassWizardDialog : BaseMaterialBottomSheetDialogFragment() {

    private var currentStep = 1

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.dialog_nautical_compass_wizard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val title = view.findViewById<TextView>(R.id.wizard_title)
        val message = view.findViewById<TextView>(R.id.wizard_message)
        val progress = view.findViewById<CircularProgressIndicator>(R.id.calibration_progress)
        val btnNext = view.findViewById<Button>(R.id.btn_next)
        val btnCancel = view.findViewById<Button>(R.id.btn_cancel)
        val btnRetry = view.findViewById<Button>(R.id.btn_retry)

        btnNext.setOnClickListener {
            when (currentStep) {
                1 -> startCalibration(title, message, progress, btnNext, btnCancel, btnRetry)
                2 -> {
                    currentStep = 3
                    title.text = getString(R.string.nautical_compass_wizard_step_3)
                    message.text = getString(R.string.nautical_compass_wizard_step_3_msg)
                    progress.visibility = View.VISIBLE
                    progress.isIndeterminate = false
                    progress.progress = 100
                    btnNext.text = getString(R.string.nautical_compass_wizard_finish)
                }
                3 -> dismiss()
            }
        }

        btnRetry.setOnClickListener {
            startCalibration(title, message, progress, btnNext, btnCancel, btnRetry)
        }

        btnCancel.setOnClickListener {
            if (currentStep >= 2) {
                 NauticalPlugin.engine?.dispatchCommand("CALIBRATE_COMPASS:STOP")
            }
            dismiss()
        }
    }

    private fun startCalibration(
        title: TextView,
        message: TextView,
        progress: CircularProgressIndicator,
        btnNext: Button,
        btnCancel: Button,
        btnRetry: Button
    ) {
        progress.visibility = View.VISIBLE
        progress.isIndeterminate = true
        btnNext.isEnabled = false
        btnCancel.isEnabled = false
        btnRetry.visibility = View.GONE

        val app = AndroidUtils.getApp(requireContext())
        NauticalPlugin.engine?.dispatchCommand("CALIBRATE_COMPASS:START")

        viewLifecycleOwner.lifecycleScope.launch {
            val calibrationJob = launch {
                NauticalPlugin.engine?.marineStateFlow?.collectLatest { state ->
                    val cal = state.pypilotCalibration
                    if (cal != null) {
                        if (cal.isCalibrating && currentStep == 1) {
                            currentStep = 2
                            title.text = getString(R.string.nautical_compass_wizard_step_2)
                            message.text = getString(R.string.nautical_compass_wizard_step_2_msg)
                            progress.visibility = View.GONE
                            btnNext.text = getString(R.string.nautical_compass_wizard_next)
                            btnNext.isEnabled = true
                            btnCancel.isEnabled = true
                            app.showToastMessage(R.string.nautical_compass_calibration_started)
                        }
                        
                        if (currentStep == 2) {
                            val p = (cal.compassCalibrationProgress ?: 0.0).toInt()
                            if (p > 0) {
                                progress.visibility = View.VISIBLE
                                progress.isIndeterminate = false
                                progress.progress = p
                            }
                        }
                    }
                }
            }

            delay(8.seconds)
            if (currentStep == 1) {
                calibrationJob.cancel()
                progress.visibility = View.GONE
                btnNext.isEnabled = true
                btnCancel.isEnabled = true
                btnRetry.visibility = View.VISIBLE
                app.showToastMessage(R.string.nautical_toast_conn_failed)
            }
        }
    }

    companion object {
        const val TAG = "NauticalCompassWizardDialog"

        @JvmStatic
        fun show(fragment: androidx.fragment.app.Fragment) {
            NauticalCompassWizardDialog().show(fragment.childFragmentManager, TAG)
        }
    }
}
